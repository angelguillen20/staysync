package com.staysync.bff.controller;

import com.staysync.bff.client.HabitacionesClient;
import com.staysync.bff.client.ReservasClient;
import com.staysync.bff.client.UsuariosClient;
import com.staysync.bff.dto.reserva.ReservaDetalleResponse;
import com.staysync.bff.security.AccesoHuesped;
import com.staysync.bff.security.JwtService;
import com.staysync.bff.service.CuentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/bff/reservas")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "BFF Reservas", description = "Gestión de reservas con datos enriquecidos de habitación")
public class ReservaBffController {

    private static final String HABITACION_ID     = "habitacionId";
    private static final String ESTADO            = "estado";
    private static final String FECHA_ENTRADA     = "fechaEntrada";
    private static final String FECHA_SALIDA      = "fechaSalida";
    private static final String USUARIO_ID        = "usuarioId";

    private final ReservasClient             reservasClient;
    private final HabitacionesClient         habitacionesClient;
    private final UsuariosClient             usuariosClient;
    private final JwtService                 jwtService;
    private final AccesoHuesped              accesoHuesped;
    private final CuentaService              cuentaService;
    private final com.staysync.bff.service.PlataformaService plataforma;

    @Operation(summary = "Listar todas las reservas",
               description = "SUPER_ADMIN: ?hotel=todos o ?hotel=<tenantId> junta las de otros hoteles (con hotelNombre)")
    @GetMapping
    public ResponseEntity<Object> listar(@RequestHeader("Authorization") String authHeader,
                                         @RequestParam(required = false) String hotel) {
        if (hotel != null && !hotel.isBlank()) {
            return ResponseEntity.ok(plataforma.porHotel(authHeader, hotel, () ->
                    reservasClient.reenviar(authHeader, HttpMethod.GET, "/api/v1/reservas?size=500", null).getBody()));
        }
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
                        .precioPorNoche(new BigDecimal(String.valueOf(hab.getOrDefault("precioPorNoche", "0"))))
                        .estado(String.valueOf(hab.get(ESTADO)))
                        .build();
            }
        }

        long noches = calcularNoches(
                String.valueOf(reserva.getOrDefault(FECHA_ENTRADA, "")),
                String.valueOf(reserva.getOrDefault(FECHA_SALIDA, "")));

        ReservaDetalleResponse detalle = ReservaDetalleResponse.builder()
                .id(toLong(reserva.get("id")))
                .codigoReserva(String.valueOf(reserva.getOrDefault("codigo", "")))
                .estado(String.valueOf(reserva.getOrDefault(ESTADO, "")))
                .fuente(String.valueOf(reserva.getOrDefault("fuente", "")))
                .fechaEntrada(String.valueOf(reserva.getOrDefault(FECHA_ENTRADA, "")))
                .fechaSalida(String.valueOf(reserva.getOrDefault(FECHA_SALIDA, "")))
                .habitacionId(habitacionId)
                .usuarioId(toLong(reserva.get(USUARIO_ID)))
                .precioTotal(new BigDecimal(String.valueOf(reserva.getOrDefault("precioTotal", "0"))))
                .notas(Objects.toString(reserva.get("notas"), ""))
                .habitacion(habitacionInfo)
                .noches(noches)
                .build();

        return ResponseEntity.ok(detalle);
    }

    @Operation(summary = "Crear nueva reserva",
               description = "Agrega el email y nombre del huésped para que Notificaciones le escriba "
                       + "al recibir los eventos de la reserva")
    @PostMapping
    public ResponseEntity<Object> crear(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> conContacto = new HashMap<>(body);
        // Un huésped siempre reserva para sí mismo, aunque el body traiga otro usuarioId
        if (accesoHuesped.esHuesped(authHeader)) {
            conContacto.put(USUARIO_ID, accesoHuesped.usuarioId(authHeader));
        }
        agregarContactoHuesped(authHeader, conContacto);
        var response = reservasClient.crear(authHeader, conContacto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response.getBody());
    }

    @Operation(summary = "Cambiar estado de reserva (checkin, checkout, etc.)",
               description = "Las notificaciones salen de los eventos que publica reservas-service. "
                       + "El check-out exige la cuenta saldada.")
    @PatchMapping("/{id}/estado")
    public ResponseEntity<Object> cambiarEstado(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        if ("CHECKOUT".equals(body.get(ESTADO))) {
            BigDecimal saldo = cuentaService.saldo(authHeader, id);
            if (saldo.signum() > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Antes del check-out cobra el saldo de la cuenta: $"
                        + String.format(java.util.Locale.forLanguageTag("es-CL"), "%,.0f", saldo));
            }
        }
        return reservasClient.cambiarEstado(authHeader, id, body);
    }

    @Operation(summary = "Cambiar fechas y/o habitación (reserva pendiente o confirmada)")
    @PatchMapping("/{id}/modificar")
    public ResponseEntity<Object> modificar(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Object body) {
        return reservasClient.reenviar(authHeader, HttpMethod.PATCH, "/api/v1/reservas/" + id + "/modificar", body);
    }

    @Operation(summary = "Reservas que ocupan algún día del rango: calendario de ocupación y reportes")
    @GetMapping("/rango")
    public ResponseEntity<Object> rango(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam String desde,
            @RequestParam String hasta,
            @RequestParam(required = false) String hotel) {
        try {  // valida antes de armar la URL
            LocalDate.parse(desde);
            LocalDate.parse(hasta);
        } catch (java.time.format.DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las fechas van en formato AAAA-MM-DD.");
        }
        String ruta = "/api/v1/reservas/rango?desde=" + desde + "&hasta=" + hasta;
        if (hotel != null && !hotel.isBlank()) {
            return ResponseEntity.ok(plataforma.porHotel(authHeader, hotel, () ->
                    reservasClient.reenviar(authHeader, HttpMethod.GET, ruta, null).getBody()));
        }
        return reservasClient.reenviar(authHeader, HttpMethod.GET, ruta, null);
    }

    @Operation(summary = "Cuenta del huésped: estadía, consumos, pagos y saldo")
    @GetMapping("/{id}/cuenta")
    public Map<String, Object> cuenta(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        accesoHuesped.verificarReservaPropia(authHeader, id);
        return cuentaService.cuenta(authHeader, id);
    }

    @Operation(summary = "Cobrar en recepción el saldo de la cuenta", description = "Body: { metodoPago }")
    @PostMapping("/{id}/cuenta/pago")
    public ResponseEntity<Object> pagarSaldo(@RequestHeader("Authorization") String authHeader,
                                             @PathVariable Long id,
                                             @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cuentaService.pagarSaldo(authHeader, id, body.get("metodoPago")));
    }

    @Operation(summary = "Cancelar reserva")
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<Object> cancelar(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        accesoHuesped.verificarReservaPropia(authHeader, id);
        return reservasClient.cancelar(authHeader, id);
    }

    @Operation(summary = "Reservas de un usuario específico")
    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<Object> porUsuario(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long usuarioId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        accesoHuesped.verificarMismoUsuario(authHeader, usuarioId);
        return reservasClient.listarPorUsuario(authHeader, usuarioId, page, size);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Huésped que reserva para sí: sus datos salen de su perfil. Recepción reservando para
     * un huésped: se consulta el perfil de ese usuario. Si falla, la reserva se crea igual
     * y Notificaciones simplemente no tendrá a quién escribir.
     */
    @SuppressWarnings("unchecked")
    private void agregarContactoHuesped(String authHeader, Map<String, Object> body) {
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long usuarioId = toLong(body.get(USUARIO_ID));
            ResponseEntity<Object> perfil = "HUESPED".equals(jwtService.extractRol(token)) || usuarioId == null
                    ? usuariosClient.getPerfilPropio(authHeader)
                    : usuariosClient.getPerfil(authHeader, usuarioId);
            if (perfil.getBody() instanceof Map<?, ?> datos) {
                Map<String, Object> u = (Map<String, Object>) datos;
                body.putIfAbsent("huespedEmail", u.get("email"));
                body.putIfAbsent("huespedNombre", (u.getOrDefault("nombre", "") + " " + u.getOrDefault("apellido", "")).trim());
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener el contacto del huésped para la reserva: {}", e.getMessage());
        }
    }

    private long calcularNoches(String entrada, String salida) {
        try {
            return ChronoUnit.DAYS.between(LocalDate.parse(entrada), LocalDate.parse(salida));
        } catch (Exception e) {
            return 0;
        }
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

}
