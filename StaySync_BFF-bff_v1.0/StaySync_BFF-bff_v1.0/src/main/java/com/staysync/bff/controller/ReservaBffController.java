package com.staysync.bff.controller;

import com.staysync.bff.client.HabitacionesClient;
import com.staysync.bff.client.ReservasClient;
import com.staysync.bff.dto.reserva.ReservaDetalleResponse;
import com.staysync.bff.messaging.NotificacionEventPublisher;
import com.staysync.bff.messaging.event.ReservaCreadaEvent;
import com.staysync.bff.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequestMapping("/bff/reservas")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "BFF Reservas", description = "Gestión de reservas con datos enriquecidos de habitación")
public class ReservaBffController {

    private static final String HABITACION_ID     = "habitacionId";
    private static final String HABITACION_NUMERO = "habitacionNumero";
    private static final String ESTADO            = "estado";
    private static final String FECHA_ENTRADA     = "fechaEntrada";
    private static final String FECHA_SALIDA      = "fechaSalida";
    private static final String USUARIO_ID        = "usuarioId";
    private static final String CODIGO            = "codigo";

    private final ReservasClient             reservasClient;
    private final HabitacionesClient         habitacionesClient;
    private final NotificacionEventPublisher notificacionPublisher;
    private final JwtService                 jwtService;

    @Operation(summary = "Listar todas las reservas")
    @GetMapping
    public ResponseEntity<Object> listar(@RequestHeader("Authorization") String authHeader) {
        return reservasClient.listar(authHeader);
    }

    @Operation(summary = "Reservas del día: check-ins pendientes (CONFIRMADA/hoy) y check-outs activos (CHECKIN)")
    @GetMapping("/hoy")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public ResponseEntity<Object> getReservasHoy(@RequestHeader("Authorization") String authHeader) {
        return reservasClient.getReservasHoy(authHeader);
    }

    @Operation(summary = "Obtener reserva detallada con información de habitación",
               description = "Agrega datos de reservas-service + habitaciones-service en una sola respuesta")
    @GetMapping("/{id}/detalle")
    public ResponseEntity<ReservaDetalleResponse> getDetalle(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {

        var reservaResp = reservasClient.getById(authHeader, id);
        @SuppressWarnings("unchecked")
        Map<String, Object> reserva = (Map<String, Object>) reservaResp.getBody();

        if (reserva == null) return ResponseEntity.notFound().build();

        Long habitacionId = reserva.get(HABITACION_ID) instanceof Number n ? n.longValue() : null;
        ReservaDetalleResponse.HabitacionInfo habitacionInfo = null;

        if (habitacionId != null) {
            var habResp = habitacionesClient.getById(authHeader, habitacionId);
            @SuppressWarnings("unchecked")
            Map<String, Object> hab = (Map<String, Object>) habResp.getBody();
            if (hab != null) {
                habitacionInfo = ReservaDetalleResponse.HabitacionInfo.builder()
                        .id(habitacionId)
                        .numero(String.valueOf(hab.get("numero")))
                        .tipo(extraerNombreTipo(hab))
                        .precioPorNoche(new BigDecimal(String.valueOf(hab.getOrDefault("precio", "0"))))
                        .estado(String.valueOf(hab.get(ESTADO)))
                        .build();
            }
        }

        long noches = calcularNoches(
                String.valueOf(reserva.getOrDefault(FECHA_ENTRADA, "")),
                String.valueOf(reserva.getOrDefault(FECHA_SALIDA, "")));

        ReservaDetalleResponse detalle = ReservaDetalleResponse.builder()
                .id(toLong(reserva.get("id")))
                .codigoReserva(String.valueOf(reserva.getOrDefault("codigoReserva", "")))
                .estado(String.valueOf(reserva.getOrDefault(ESTADO, "")))
                .fuente(String.valueOf(reserva.getOrDefault("fuente", "")))
                .fechaEntrada(String.valueOf(reserva.getOrDefault(FECHA_ENTRADA, "")))
                .fechaSalida(String.valueOf(reserva.getOrDefault(FECHA_SALIDA, "")))
                .habitacionId(habitacionId)
                .usuarioId(toLong(reserva.get(USUARIO_ID)))
                .precioTotal(new BigDecimal(String.valueOf(reserva.getOrDefault("precioTotal", "0"))))
                .notas(String.valueOf(reserva.getOrDefault("notas", "")))
                .habitacion(habitacionInfo)
                .noches(noches)
                .build();

        return ResponseEntity.ok(detalle);
    }

    @Operation(summary = "Crear nueva reserva")
    @PostMapping
    public ResponseEntity<Object> crear(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Object body) {

        var response = reservasClient.crear(authHeader, body);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> reserva = (Map<String, Object>) response.getBody();
                String email         = extractEmail(authHeader);
                String nombreUsuario = emailToNombre(email);

                notificacionPublisher.publishReservaCreada(new ReservaCreadaEvent(
                        toLong(reserva.get("id")),
                        toLong(reserva.get(USUARIO_ID)),
                        email,
                        nombreUsuario,
                        String.valueOf(reserva.getOrDefault(CODIGO, "")),
                        etiquetaHabitacion(reserva),
                        String.valueOf(reserva.getOrDefault(FECHA_ENTRADA, "")),
                        String.valueOf(reserva.getOrDefault(FECHA_SALIDA, "")),
                        toDouble(reserva.get("precioTotal"))
                ));
            } catch (Exception e) {
                log.warn("No se pudo publicar notificación de reserva creada: {}", e.getMessage());
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response.getBody());
    }

    @Operation(summary = "Cambiar estado de reserva (checkin, checkout, etc.)")
    @PatchMapping("/{id}/estado")
    public ResponseEntity<Object> cambiarEstado(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Object body) {

        var response = reservasClient.cambiarEstado(authHeader, id, body);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> reserva = (Map<String, Object>) response.getBody();
                String email         = extractEmail(authHeader);
                String nombreUsuario = emailToNombre(email);

                notificacionPublisher.publishReservaEstadoCambiado(
                        toLong(reserva.get("id")),
                        toLong(reserva.get(USUARIO_ID)),
                        email,
                        nombreUsuario,
                        String.valueOf(reserva.getOrDefault(CODIGO, "")),
                        etiquetaHabitacion(reserva),
                        String.valueOf(reserva.getOrDefault(ESTADO, ""))
                );
            } catch (Exception e) {
                log.warn("No se pudo publicar notificación de cambio de estado: {}", e.getMessage());
            }
        }

        return response;
    }

    @Operation(summary = "Cancelar reserva")
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<Object> cancelar(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {

        var response = reservasClient.cancelar(authHeader, id);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> reserva = (Map<String, Object>) response.getBody();
                String email         = extractEmail(authHeader);
                String nombreUsuario = emailToNombre(email);

                notificacionPublisher.publishReservaEstadoCambiado(
                        toLong(reserva.get("id")),
                        toLong(reserva.get(USUARIO_ID)),
                        email,
                        nombreUsuario,
                        String.valueOf(reserva.getOrDefault(CODIGO, "")),
                        etiquetaHabitacion(reserva),
                        "CANCELADA"
                );
            } catch (Exception e) {
                log.warn("No se pudo publicar notificación de cancelación: {}", e.getMessage());
            }
        }

        return response;
    }

    @Operation(summary = "Reservas de un usuario específico")
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<Object> porUsuario(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long usuarioId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return reservasClient.listarPorUsuario(authHeader, usuarioId, page, size);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractEmail(String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return jwtService.extractEmail(token);
    }

    private String emailToNombre(String email) {
        if (email == null || email.isBlank()) return "Cliente";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private long calcularNoches(String entrada, String salida) {
        try {
            return ChronoUnit.DAYS.between(LocalDate.parse(entrada), LocalDate.parse(salida));
        } catch (Exception e) {
            return 0;
        }
    }

    private String etiquetaHabitacion(Map<String, Object> reserva) {
        return "Hab. " + reserva.getOrDefault(HABITACION_NUMERO, reserva.getOrDefault(HABITACION_ID, ""));
    }

    @SuppressWarnings("unchecked")
    private String extraerNombreTipo(Map<String, Object> hab) {
        Object tipo = hab.get("tipo");
        if (tipo instanceof Map<?, ?> tipoMap) {
            return String.valueOf(((Map<String, Object>) tipoMap).getOrDefault("nombre", ""));
        }
        return String.valueOf(tipo);
    }

    private Long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        return null;
    }

    private Double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        return null;
    }
}
