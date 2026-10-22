package com.staysync.reservas.service;

import com.staysync.eventos.Outbox;
import com.staysync.eventos.TiposEvento;
import com.staysync.reservas.dto.request.CrearReservaRequest;
import com.staysync.reservas.dto.response.ReservaResponse;
import com.staysync.reservas.exception.*;
import com.staysync.reservas.model.HuespedAdicional;
import com.staysync.reservas.model.Reserva;
import com.staysync.reservas.model.Reserva.EstadoReserva;
import com.staysync.reservas.model.ReservaNoche;
import com.staysync.reservas.repository.ReservaNocheRepository;
import com.staysync.reservas.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ciclo de vida de las reservas. Todas las consultas quedan filtradas por el hotel de la
 * petición (@TenantId). Cada cambio relevante deja un evento en el outbox, en la misma
 * transacción: Notificaciones, Servicios, Habitaciones y Pagos reaccionan a esos eventos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReservaService {

    // Zona horaria oficial del hotel (Chile — America/Santiago, con horario de verano DST)
    private static final ZoneId     ZONA_HOTEL              = ZoneId.of("America/Santiago");
    private static final LocalTime  HORA_CHECKIN            = LocalTime.of(15, 0);
    private static final long       HORAS_MIN_CANCELACION   = 24;
    private static final DateTimeFormatter FMT_LIMITE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm z");

    private final ReservaRepository reservaRepository;
    private final ReservaNocheRepository nocheRepository;
    private final RestTemplate restTemplate;
    private final Outbox outbox;

    @Value("${services.habitaciones.url}")
    private String habitacionesUrl;

    /**
     * Anti-overbooking en dos capas: la consulta de conflictos rechaza rápido el caso común
     * y la restricción única de reserva_noches cubre las reservas que llegan a la vez.
     * Una reserva PENDIENTE (aún sin pagar) ya bloquea sus noches.
     */
    @Transactional
    public ReservaResponse crear(CrearReservaRequest request) {
        long noches = request.getFechaSalida().toEpochDay() - request.getFechaEntrada().toEpochDay();
        if (noches <= 0) throw new IllegalArgumentException("La fecha de salida debe ser posterior a la de entrada");

        boolean conflicto = reservaRepository.existeConflicto(
                request.getHabitacionId(), request.getFechaEntrada(), request.getFechaSalida());
        if (conflicto) {
            throw new HabitacionNoDisponibleException(request.getHabitacionId());
        }

        Map<String, Object> habitacion = obtenerDatosHabitacion(request.getHabitacionId());

        if (habitacion != null && habitacion.get("capacidad") instanceof Number cap
                && request.getNumHuespedes() > cap.intValue()) {
            throw new IllegalArgumentException(
                "La habitación tiene capacidad máxima de " + cap.intValue() + " huéspedes. Solicitados: " + request.getNumHuespedes());
        }

        java.math.BigDecimal precioPorNoche = (habitacion != null && habitacion.get("precioPorNoche") != null)
                ? new java.math.BigDecimal(habitacion.get("precioPorNoche").toString())
                : java.math.BigDecimal.valueOf(100.00);
        java.math.BigDecimal precioTotal = precioPorNoche.multiply(java.math.BigDecimal.valueOf(noches));

        Reserva reserva = Reserva.builder()
                .codigo(generarCodigo())
                .usuarioId(request.getUsuarioId())
                .habitacionId(request.getHabitacionId())
                .fechaEntrada(request.getFechaEntrada())
                .fechaSalida(request.getFechaSalida())
                .numHuespedes(request.getNumHuespedes())
                .precioTotal(precioTotal)
                .notas(request.getNotas())
                .huespedEmail(request.getHuespedEmail())
                .huespedNombre(request.getHuespedNombre())
                .fuente(request.getFuente())
                .estado(EstadoReserva.PENDIENTE)
                .build();

        Reserva guardada = reservaRepository.save(reserva);
        bloquearNoches(guardada);

        if (request.getHuespedesAdicionales() != null && !request.getHuespedesAdicionales().isEmpty()) {
            request.getHuespedesAdicionales().forEach(h -> {
                HuespedAdicional adicional = HuespedAdicional.builder()
                        .reserva(guardada)
                        .nombre(h.getNombre())
                        .apellido(h.getApellido())
                        .documento(h.getDocumento())
                        .build();
                guardada.getHuespedesAdicionales().add(adicional);
            });
            reservaRepository.save(guardada);
        }

        publicar(TiposEvento.RESERVA_CREADA, guardada);
        log.info("Reserva creada: {} con {} huéspedes adicionales",
                guardada.getCodigo(), guardada.getHuespedesAdicionales().size());
        return toResponse(guardada);
    }

    private void bloquearNoches(Reserva reserva) {
        List<ReservaNoche> noches = reserva.getFechaEntrada().datesUntil(reserva.getFechaSalida())
                .map(fecha -> ReservaNoche.builder()
                        .habitacionId(reserva.getHabitacionId())
                        .fecha(fecha)
                        .reservaId(reserva.getId())
                        .build())
                .toList();
        try {
            nocheRepository.saveAllAndFlush(noches);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
            // Otra reserva tomó alguna de estas noches entre la consulta y el insert
            log.warn("Overbooking evitado: habitación {} ya reservada para alguna noche de {}..{}",
                    reserva.getHabitacionId(), reserva.getFechaEntrada(), reserva.getFechaSalida());
            throw new HabitacionNoDisponibleException(reserva.getHabitacionId());
        }
    }

    public Map<String, Object> getReservasHoy() {
        LocalDate hoy = LocalDate.now(ZoneId.systemDefault());
        List<ReservaResponse> pendientesCheckin = reservaRepository
                .findByEstadoAndFechaEntrada(EstadoReserva.CONFIRMADA, hoy)
                .stream().map(this::toResponse).collect(Collectors.toList());
        List<ReservaResponse> pendientesCheckout = reservaRepository
                .findByEstado(EstadoReserva.CHECKIN)
                .stream().map(this::toResponse).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fecha", hoy.toString());
        result.put("pendientesCheckin", pendientesCheckin);
        result.put("pendientesCheckout", pendientesCheckout);
        return result;
    }

    /**
     * Pasa a CHECKIN las reservas confirmadas cuya fecha de entrada ya llegó. Recorre todos
     * los hoteles: {@link ReservaScheduler} la llama en modo sistema.
     */
    @Transactional
    public void autoTransicionarACheckin() {
        LocalDate hoy = LocalDate.now(ZoneId.systemDefault());
        List<Reserva> confirmadas = reservaRepository.findConfirmadasParaAutoCheckin(hoy);
        confirmadas.forEach(r -> {
            r.setEstado(EstadoReserva.CHECKIN);
            reservaRepository.save(r);
            publicar(TiposEvento.RESERVA_CHECKIN, r);
        });
        if (!confirmadas.isEmpty()) {
            log.info("Auto-checkin: {} reservas transicionadas a CHECKIN para {}", confirmadas.size(), hoy);
        }
    }

    public Page<ReservaResponse> listar(Pageable pageable) {
        return reservaRepository.findAll(pageable).map(this::toResponse);
    }

    public ReservaResponse obtenerPorId(Long id) {
        return toResponse(findOrThrow(id));
    }

    public ReservaResponse obtenerPorCodigo(String codigo) {
        return toResponse(reservaRepository.findByCodigo(codigo)
                .orElseThrow(() -> new ReservaNotFoundException(codigo)));
    }

    public Page<ReservaResponse> listarPorUsuario(Long usuarioId, Pageable pageable) {
        return reservaRepository.findByUsuarioId(usuarioId, pageable).map(this::toResponse);
    }

    @Transactional
    public ReservaResponse cambiarEstado(Long id, EstadoReserva nuevoEstado) {
        Reserva reserva = findOrThrow(id);
        validarTransicion(reserva.getEstado(), nuevoEstado);
        reserva.setEstado(nuevoEstado);
        Reserva guardada = reservaRepository.save(reserva);
        if (nuevoEstado == EstadoReserva.CANCELADA) {
            nocheRepository.liberarNoches(guardada.getId());
        }
        tipoEventoPara(nuevoEstado).ifPresent(tipo -> publicar(tipo, guardada));
        return toResponse(guardada);
    }

    @Transactional
    public ReservaResponse cancelar(Long id) {
        Reserva reserva = findOrThrow(id);
        if (reserva.getEstado() == EstadoReserva.CHECKOUT || reserva.getEstado() == EstadoReserva.CANCELADA) {
            throw new TransicionEstadoInvalidaException(reserva.getEstado().name(), "CANCELADA");
        }
        validarVentanaCancelacion(reserva);
        reserva.setEstado(EstadoReserva.CANCELADA);
        Reserva guardada = reservaRepository.save(reserva);
        nocheRepository.liberarNoches(guardada.getId());
        publicar(TiposEvento.RESERVA_CANCELADA, guardada);
        return toResponse(guardada);
    }

    /**
     * Reacción al evento PagoEfectuado: confirma la reserva si sigue pendiente. Un pago que
     * llega dos veces, o sobre una reserva ya confirmada, no cambia nada.
     */
    @Transactional
    public void confirmarPorPago(Long reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId).orElse(null);
        if (reserva == null) {
            log.warn("PagoEfectuado para la reserva {} que no existe en este hotel", reservaId);
            return;
        }
        if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
            log.info("Reserva {} ya estaba en {}: el pago no cambia su estado", reserva.getCodigo(), reserva.getEstado());
            return;
        }
        cambiarEstado(reservaId, EstadoReserva.CONFIRMADA);
        log.info("Reserva {} confirmada tras el pago", reserva.getCodigo());
    }

    /**
     * Verifica que la cancelación se solicite con al menos 24 horas de anticipación
     * al check-in (fijado a las 15:00 hora del hotel).
     *
     * Diseño de separación de responsabilidades:
     *   - PATCH /cancelar  → aplica esta regla (flujo del huésped).
     *   - PATCH /{id}/estado con CANCELADA → omite esta regla (escape de administrador).
     *
     * Se usa ZonedDateTime para comparar correctamente aunque el servidor corra en UTC.
     */
    private void validarVentanaCancelacion(Reserva reserva) {
        ZonedDateTime ahora      = ZonedDateTime.now(ZONA_HOTEL);
        ZonedDateTime checkinZdt = ZonedDateTime.of(reserva.getFechaEntrada(), HORA_CHECKIN, ZONA_HOTEL);

        long horasRestantes = ChronoUnit.HOURS.between(ahora, checkinZdt);

        if (horasRestantes < HORAS_MIN_CANCELACION) {
            ZonedDateTime fechaLimite = checkinZdt.minusHours(HORAS_MIN_CANCELACION);
            throw new CancelacionRestringidaException(
                "Cancelación no permitida: la reserva solo puede cancelarse con al menos " +
                HORAS_MIN_CANCELACION + " horas de anticipación al check-in (15:00 hora del hotel). " +
                "El plazo máximo fue el " + fechaLimite.format(FMT_LIMITE) + ".");
        }
    }

    public boolean tieneConflicto(Long habitacionId, java.time.LocalDate entrada, java.time.LocalDate salida) {
        return reservaRepository.existeConflicto(habitacionId, entrada, salida);
    }

    private Reserva findOrThrow(Long id) {
        return reservaRepository.findById(id).orElseThrow(() -> new ReservaNotFoundException(id));
    }

    private void validarTransicion(EstadoReserva actual, EstadoReserva nuevo) {
        boolean valida = switch (actual) {
            case PENDIENTE   -> nuevo == EstadoReserva.CONFIRMADA || nuevo == EstadoReserva.CANCELADA;
            case CONFIRMADA  -> nuevo == EstadoReserva.CHECKIN    || nuevo == EstadoReserva.CANCELADA;
            case CHECKIN     -> nuevo == EstadoReserva.CHECKOUT;
            default          -> false;
        };
        if (!valida) throw new TransicionEstadoInvalidaException(actual.name(), nuevo.name());
    }

    private static Optional<String> tipoEventoPara(EstadoReserva estado) {
        return Optional.ofNullable(switch (estado) {
            case CONFIRMADA -> TiposEvento.RESERVA_CONFIRMADA;
            case CANCELADA  -> TiposEvento.RESERVA_CANCELADA;
            case CHECKIN    -> TiposEvento.RESERVA_CHECKIN;
            case CHECKOUT   -> TiposEvento.RESERVA_CHECKOUT;
            default         -> null;
        });
    }

    /** Payload común de los eventos de reserva (contrato en docs/eventos.md). */
    private void publicar(String tipo, Reserva r) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservaId", r.getId());
        payload.put("codigo", r.getCodigo());
        payload.put("usuarioId", r.getUsuarioId());
        payload.put("habitacionId", r.getHabitacionId());
        payload.put("fechaEntrada", String.valueOf(r.getFechaEntrada()));
        payload.put("fechaSalida", String.valueOf(r.getFechaSalida()));
        payload.put("precioTotal", r.getPrecioTotal());
        payload.put("estado", r.getEstado().name());
        if (r.getHuespedEmail() != null) payload.put("huespedEmail", r.getHuespedEmail());
        if (r.getHuespedNombre() != null) payload.put("huespedNombre", r.getHuespedNombre());
        outbox.registrar(tipo, r.getTenantId(), payload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> obtenerDatosHabitacion(Long habitacionId) {
        try {
            return restTemplate.getForObject(
                    habitacionesUrl + "/api/v1/habitaciones/" + habitacionId, Map.class);
        } catch (HttpClientErrorException.NotFound e) {
            // habitaciones-service filtra por hotel: 404 = no existe o es de otro hotel
            throw new IllegalArgumentException("La habitación " + habitacionId + " no existe en este hotel");
        } catch (Exception e) {
            log.warn("No se pudo obtener datos de habitación {}: {}", habitacionId, e.getMessage());
            return null;
        }
    }

    private String generarCodigo() {
        String codigo;
        do {
            codigo = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (reservaRepository.existsByCodigo(codigo));
        return codigo;
    }

    private ReservaResponse toResponse(Reserva r) {
        List<ReservaResponse.HuespedAdicionalResponse> huespedesResp =
                r.getHuespedesAdicionales() == null ? Collections.emptyList()
                : r.getHuespedesAdicionales().stream()
                        .map(h -> ReservaResponse.HuespedAdicionalResponse.builder()
                                .id(h.getId()).nombre(h.getNombre())
                                .apellido(h.getApellido()).documento(h.getDocumento())
                                .build())
                        .collect(Collectors.toList());

        return ReservaResponse.builder()
                .id(r.getId()).codigo(r.getCodigo())
                .usuarioId(r.getUsuarioId()).habitacionId(r.getHabitacionId())
                .fechaEntrada(r.getFechaEntrada()).fechaSalida(r.getFechaSalida())
                .numHuespedes(r.getNumHuespedes()).estado(r.getEstado())
                .precioTotal(r.getPrecioTotal()).notas(r.getNotas())
                .fuente(r.getFuente()).createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt())
                .huespedesAdicionales(huespedesResp)
                .build();
    }
}
