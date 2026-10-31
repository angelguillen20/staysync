package com.staysync.bff.security;

import com.staysync.bff.client.ReservasClient;
import com.staysync.bff.exception.AccesoDenegadoException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Un huésped solo opera sobre lo suyo. Reservas, pagos y servicios no validan el rol: confían
 * en el BFF, así que aquí se fija su usuarioId y se comprueba que la reserva le pertenezca.
 * Para el personal del hotel (ADMIN, RECEPCIONISTA, SUPER_ADMIN) estas comprobaciones no aplican.
 */
@Component
@RequiredArgsConstructor
public class AccesoHuesped {

    private static final String NO_ES_TUYA = "Esa reserva no pertenece a tu cuenta.";

    private final JwtService jwtService;
    private final ReservasClient reservasClient;

    public boolean esHuesped(String authHeader) {
        return "HUESPED".equals(jwtService.extractRol(token(authHeader)));
    }

    public Long usuarioId(String authHeader) {
        Number id = jwtService.extractClaim(token(authHeader), c -> c.get("userId", Number.class));
        return id == null ? null : id.longValue();
    }

    /** Un huésped solo consulta sus propios datos (mis reservas, mis solicitudes). */
    public void verificarMismoUsuario(String authHeader, Long usuarioId) {
        if (esHuesped(authHeader) && (usuarioId == null || !usuarioId.equals(usuarioId(authHeader)))) {
            throw new AccesoDenegadoException("Solo puedes consultar tus propios datos.");
        }
    }

    /** Un huésped solo cancela o paga sus propias reservas. */
    public void verificarReservaPropia(String authHeader, Object reservaId) {
        if (!esHuesped(authHeader)) {
            return;
        }
        Long id = aLong(reservaId);
        if (id == null) {
            throw new AccesoDenegadoException(NO_ES_TUYA);
        }
        Object body = reservasClient.getById(authHeader, id).getBody();
        Object dueno = body instanceof Map<?, ?> reserva ? reserva.get("usuarioId") : null;
        if (!usuarioId(authHeader).equals(aLong(dueno))) {
            throw new AccesoDenegadoException(NO_ES_TUYA);
        }
    }

    /**
     * La reserva que se va a cobrar: debe seguir PENDIENTE y, si cobra un huésped, ser suya.
     * El monto del pago sale de su precioTotal, nunca del navegador.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> reservaPorPagar(String authHeader, Object reservaId) {
        Long id = aLong(reservaId);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la reserva que se va a pagar.");
        }
        if (!(reservasClient.getById(authHeader, id).getBody() instanceof Map<?, ?> reserva)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La reserva " + id + " no existe.");
        }
        if (esHuesped(authHeader) && !usuarioId(authHeader).equals(aLong(reserva.get("usuarioId")))) {
            throw new AccesoDenegadoException(NO_ES_TUYA);
        }
        if (!"PENDIENTE".equals(reserva.get("estado"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La reserva no tiene pagos pendientes (estado " + reserva.get("estado") + ").");
        }
        return (Map<String, Object>) reserva;
    }

    private static Long aLong(Object valor) {
        if (valor instanceof Number n) {
            return n.longValue();
        }
        try {
            return valor == null ? null : Long.valueOf(valor.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String token(String authHeader) {
        return authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
    }
}
