package com.staysync.pagos.service;

import com.staysync.eventos.Outbox;
import com.staysync.eventos.TiposEvento;
import com.staysync.pagos.model.Pago;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Eventos que publica Pagos (contrato en docs/eventos.md). Se guardan en el outbox de la transacción actual. */
@Component
@RequiredArgsConstructor
public class PagoEventos {

    private final Outbox outbox;

    public void pagoEfectuado(Pago pago) {
        outbox.registrar(TiposEvento.PAGO_EFECTUADO, pago.getTenantId(), payload(pago));
    }

    public void reembolsoEfectuado(Pago pago) {
        outbox.registrar(TiposEvento.REEMBOLSO_EFECTUADO, pago.getTenantId(), payload(pago));
    }

    private static Map<String, Object> payload(Pago pago) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("pagoId", pago.getId());
        p.put("referencia", pago.getReferencia());
        p.put("reservaId", pago.getReservaId());
        p.put("usuarioId", pago.getUsuarioId());
        p.put("monto", pago.getMonto());
        p.put("moneda", pago.getMoneda());
        return p;
    }
}
