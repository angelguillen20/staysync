package com.staysync.bff.service;

import com.staysync.bff.client.PagosClient;
import com.staysync.bff.client.ReservasClient;
import com.staysync.bff.client.ServiciosClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

/**
 * Cuenta del huésped: estadía + servicios consumidos − pagos completados = saldo. Antes los
 * servicios pedidos (spa, room service...) tenían precio pero nadie los cobraba.
 * Lee cada servicio con reenviar(): si uno no responde la cuenta falla (503) en vez de asumir
 * "sin pagos" y cobrar dos veces.
 */
@Component
@RequiredArgsConstructor
public class CuentaService {

    private final ReservasClient reservasClient;
    private final ServiciosClient serviciosClient;
    private final PagosClient pagosClient;

    public Map<String, Object> cuenta(String authHeader, Long reservaId) {
        Map<String, Object> reserva = mapa(reservasClient.reenviar(authHeader, GET, "/api/v1/reservas/" + reservaId, null).getBody());
        List<Map<String, Object>> consumos = lista(serviciosClient.reenviar(
                authHeader, GET, "/api/v1/solicitudes/reserva/" + reservaId, null).getBody()).stream()
                .filter(s -> !"CANCELADO".equals(s.get("estado"))).toList();
        List<Map<String, Object>> pagos = lista(pagosClient.reenviar(
                authHeader, GET, "/api/v1/pagos/reserva/" + reservaId, null).getBody());

        // Una reserva cancelada ya no cobra la estadía (Pagos la reembolsó); un no-show sí
        BigDecimal estadia = "CANCELADA".equals(reserva.get("estado")) ? BigDecimal.ZERO : monto(reserva.get("precioTotal"));
        BigDecimal totalConsumos = sumar(consumos, "precioTotal");
        BigDecimal totalPagado = sumar(pagos.stream().filter(p -> "COMPLETADO".equals(p.get("estado"))).toList(), "monto");

        Map<String, Object> cuenta = new LinkedHashMap<>();
        cuenta.put("reservaId", reservaId);
        cuenta.put("codigo", reserva.get("codigo"));
        cuenta.put("estado", reserva.get("estado"));
        cuenta.put("usuarioId", reserva.get("usuarioId"));
        cuenta.put("huespedNombre", reserva.get("huespedNombre"));
        cuenta.put("estadia", estadia);
        cuenta.put("consumos", consumos);
        cuenta.put("totalConsumos", totalConsumos);
        cuenta.put("pagos", pagos);
        cuenta.put("totalPagado", totalPagado);
        cuenta.put("saldo", estadia.add(totalConsumos).subtract(totalPagado));
        return cuenta;
    }

    public BigDecimal saldo(String authHeader, Long reservaId) {
        return (BigDecimal) cuenta(authHeader, reservaId).get("saldo");
    }

    /** Recepción cobra todo el saldo: consumos y, si la reserva se modificó, la diferencia de tarifa. */
    public Object pagarSaldo(String authHeader, Long reservaId, Object metodoPago) {
        Map<String, Object> cuenta = cuenta(authHeader, reservaId);
        BigDecimal saldo = (BigDecimal) cuenta.get("saldo");
        if (saldo.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuenta no tiene saldo pendiente.");
        }
        Map<String, Object> pago = new HashMap<>();
        pago.put("reservaId", reservaId);
        pago.put("usuarioId", cuenta.get("usuarioId"));
        pago.put("monto", saldo);
        pago.put("moneda", "CLP");
        pago.put("metodoPago", metodoPago);
        pago.put("descripcion", "Saldo de la cuenta " + cuenta.get("codigo"));
        return pagosClient.reenviar(authHeader, POST, "/api/v1/pagos", pago).getBody();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object body) {
        if (body instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Respuesta inesperada al armar la cuenta.");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Object body) {
        return body instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    private static BigDecimal sumar(List<Map<String, Object>> filas, String campo) {
        return filas.stream().map(f -> monto(f.get(campo))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal monto(Object valor) {
        return valor == null ? BigDecimal.ZERO : new BigDecimal(valor.toString());
    }
}
