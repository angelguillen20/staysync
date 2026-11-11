package com.staysync.eventos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.util.Map;

/**
 * Patrón outbox: el evento se guarda en la misma transacción que el cambio de negocio.
 * Si la transacción se revierte, el evento tampoco existe; si se confirma, {@link OutboxRelay}
 * lo publicará aunque SNS esté caído en ese momento. Así nunca hay "reserva guardada pero
 * aviso perdido" ni "aviso de algo que no se guardó".
 */
public class Outbox {

    private static final Logger log = LoggerFactory.getLogger(Outbox.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public Outbox(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** Debe llamarse dentro de la transacción (@Transactional) que guarda el cambio. */
    public EventoDominio registrar(String tipo, String tenantId, Map<String, Object> payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            log.warn("Evento {} registrado fuera de una transacción: no queda atado al cambio de negocio", tipo);
        }
        EventoDominio evento = EventoDominio.nuevo(tipo, tenantId, payload);
        jdbcTemplate.update(
                "INSERT INTO outbox_eventos (event_id, tipo, tenant_id, contenido, creado_en, intentos) VALUES (?, ?, ?, ?, ?, 0)",
                evento.eventId(), tipo, tenantId, aJson(evento), Timestamp.from(evento.ocurridoEn()));
        log.debug("Evento {} {} guardado en outbox (hotel {})", tipo, evento.eventId(), tenantId);
        return evento;
    }

    private String aJson(EventoDominio evento) {
        try {
            return objectMapper.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + evento.tipo(), e);
        }
    }
}
