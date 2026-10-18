package com.staysync.eventos;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sobre común de todos los eventos (contrato en docs/eventos.md).
 *
 * @param eventId    identificador único; los consumidores lo usan para ignorar duplicados
 * @param tipo       uno de {@link TiposEvento}; SNS filtra por él
 * @param version    versión del payload, para evolucionarlo sin romper consumidores
 * @param tenantId   hotel al que pertenece el evento
 * @param ocurridoEn instante en que ocurrió el hecho
 * @param payload    datos del evento
 */
public record EventoDominio(String eventId, String tipo, int version, String tenantId,
                            Instant ocurridoEn, Map<String, Object> payload) {

    public static EventoDominio nuevo(String tipo, String tenantId, Map<String, Object> payload) {
        return new EventoDominio(UUID.randomUUID().toString(), tipo, 1, tenantId, Instant.now(), payload);
    }

    public Long payloadLong(String campo) {
        Object v = payload == null ? null : payload.get(campo);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return v == null ? null : Long.valueOf(v.toString());
    }

    public String payloadString(String campo) {
        Object v = payload == null ? null : payload.get(campo);
        return v == null ? null : v.toString();
    }
}
