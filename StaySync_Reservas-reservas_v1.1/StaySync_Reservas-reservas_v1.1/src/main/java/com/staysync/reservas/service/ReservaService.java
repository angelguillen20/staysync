package com.staysync.reservas.service;

import com.staysync.reservas.dto.request.CrearReservaRequest;
import com.staysync.reservas.dto.response.ReservaResponse;
import com.staysync.reservas.exception.*;
import com.staysync.reservas.model.HuespedAdicional;
import com.staysync.reservas.model.Reserva;
import com.staysync.reservas.model.Reserva.EstadoReserva;
import com.staysync.reservas.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

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
    private final RestTemplate restTemplate;

    @Value("${services.habitaciones.url}")
    private String habitacionesUrl;

    @Transactional
    public ReservaResponse crear(CrearReservaRequest request) {
        boolean conflicto = reservaRepository.existeConflicto(
                request.getHabitacionId(), request.getFechaEntrada(), request.getFechaSalida());
        if (conflicto) {
            throw new HabitacionNoDisponibleException(request.getHabitacionId());
        }

        long noches = request.getFechaSalida().toEpochDay() - request.getFechaEntrada().toEpochDay();
        if (noches <= 0) throw new IllegalArgumentException("La fecha de salida debe ser posterior a la de entrada");

        Map<String, Object> habitacion = obtenerDatosHabitacion(request.getHabitacionId());

        if (habitacion != null && habitacion.get("capacidad") instanceof Number cap) {
            if (request.getNumHuespedes() > cap.intValue()) {
                throw new IllegalArgumentException(
                    "La habitación tiene capacidad máxima de " + cap.intValue() + " huéspedes. Solicitados: " + request.getNumHuespedes());
            }
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
                .fuente(request.getFuente())
                .estado(EstadoReserva.PENDIENTE)
                .build();

        Reserva guardada = reservaRepository.save(reserva);

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

        log.info("Reserva creada: {} con {} huéspedes adicionales",
                guardada.getCodigo(), guardada.getHuespedesAdicionales().size());
        return toResponse(guardada);
    }

    public Map<String, Object> getReservasHoy() {
        LocalDate hoy = LocalDate.now();
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

    @Transactional
    @Scheduled(cron = "0 0 0 * * *")
    public void autoTransicionarACheckin() {
        LocalDate hoy = LocalDate.now();
        List<Reserva> confirmadas = reservaRepository.findConfirmadasParaAutoCheckin(hoy);
        confirmadas.forEach(r -> {
            r.setEstado(EstadoReserva.CHECKIN);
            reservaRepository.save(r);
            sincronizarEstadoHabitacion(r.getHabitacionId(), EstadoReserva.CHECKIN);
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
        sincronizarEstadoHabitacion(guardada.getHabitacionId(), nuevoEstado);
        return toResponse(guardada);
    }

    @Transactional
    public void cancelar(Long id) {
        Reserva reserva = findOrThrow(id);
        if (reserva.getEstado() == EstadoReserva.CHECKOUT || reserva.getEstado() == EstadoReserva.CANCELADA) {
            throw new TransicionEstadoInvalidaException(reserva.getEstado().name(), "CANCELADA");
        }
        validarVentanaCancelacion(reserva);
        reserva.setEstado(EstadoReserva.CANCELADA);
        reservaRepository.save(reserva);
    }

    /**
     * Verifica que la cancelación se solicite con al menos 24 horas de anticipación
     * al check-in (fijado a las 15:00 hora Colombia).
     *
     * Diseño de separación de responsabilidades:
     *   - PATCH /cancelar  → aplica esta regla (flujo del huésped).
     *   - PATCH /{id}/estado con CANCELADA → omite esta regla (escape de administrador).
     *
     * Zona horaria: America/Bogota (UTC-5, sin DST).
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
                HORAS_MIN_CANCELACION + " horas de anticipación al check-in (15:00 hora Colombia). " +
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

    /**
     * Sincroniza el estado de la habitación cuando la reserva entra o sale de estancia activa.
     * Llamada síncrona directa (RestTemplate) a habitaciones-service — reemplaza el evento
     * "reserva.checkin"/"reserva.checkout" que antes se publicaba a una cola nunca enlazada.
     */
    private void sincronizarEstadoHabitacion(Long habitacionId, EstadoReserva nuevoEstado) {
        String estadoHabitacion = switch (nuevoEstado) {
            case CHECKIN  -> "OCUPADA";
            case CHECKOUT -> "DISPONIBLE";
            default       -> null;
        };
        if (estadoHabitacion == null) return;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("estado", estadoHabitacion), headers);
            restTemplate.exchange(
                    habitacionesUrl + "/api/v1/habitaciones/" + habitacionId + "/estado",
                    HttpMethod.PATCH,
                    entity,
                    Object.class);
            log.info("Habitación {} sincronizada a {} (reserva -> {})", habitacionId, estadoHabitacion, nuevoEstado);
        } catch (Exception e) {
            log.error("No se pudo sincronizar habitación {} a {}: {}", habitacionId, estadoHabitacion, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> obtenerDatosHabitacion(Long habitacionId) {
        try {
            return restTemplate.getForObject(
                    habitacionesUrl + "/api/v1/habitaciones/" + habitacionId, Map.class);
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
