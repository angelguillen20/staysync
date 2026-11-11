package com.staysync.eventos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.tenant.TenantContext;
import com.staysync.tenant.TenantHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Lo usan los listeners SQS de cada servicio. Por cada mensaje:
 * <ol>
 *   <li>Lee el sobre del evento (también si llega envuelto en la notificación de SNS).</li>
 *   <li>Fija el hotel del evento en {@link TenantContext}: las consultas del manejador
 *       quedan filtradas por ese hotel.</li>
 *   <li>En una sola transacción marca el eventId como procesado y ejecuta el manejador.
 *       Si el eventId ya estaba, es un duplicado y se ignora.</li>
 * </ol>
 * Si el manejador lanza una excepción, la transacción se revierte (el eventId no queda
 * marcado) y la excepción sube al listener: SQS reintenta y, tras varios fallos, el
 * mensaje termina en la DLQ.
 */
public class ProcesadorEventos {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorEventos.class);

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public ProcesadorEventos(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
                             TransactionTemplate transactionTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /** @return true si el evento se procesó; false si se ignoró (duplicado o tipo sin manejador). */
    public boolean procesar(String mensaje, Map<String, Consumer<EventoDominio>> manejadores) {
        EventoDominio evento = leer(mensaje);
        Consumer<EventoDominio> manejador = manejadores.get(evento.tipo());
        if (manejador == null) {
            log.debug("Evento {} sin manejador en este servicio: se ignora", evento.tipo());
            return false;
        }
        if (!TenantHeaders.esValido(evento.tenantId())) {
            throw new IllegalArgumentException("Evento " + evento.eventId() + " sin hotel válido: " + evento.tenantId());
        }
        Boolean procesado = TenantContext.ejecutarCon(evento.tenantId(), () ->
                transactionTemplate.execute(status -> {
                    if (!marcarProcesado(evento)) {
                        log.info("Evento {} {} ya procesado: duplicado ignorado", evento.tipo(), evento.eventId());
                        return false;
                    }
                    manejador.accept(evento);
                    log.info("Evento {} {} procesado (hotel {})", evento.tipo(), evento.eventId(), evento.tenantId());
                    return true;
                }));
        return Boolean.TRUE.equals(procesado);
    }

    private boolean marcarProcesado(EventoDominio evento) {
        try {
            jdbcTemplate.update("INSERT INTO eventos_procesados (event_id, tipo, procesado_en) VALUES (?, ?, ?)",
                    evento.eventId(), evento.tipo(), Timestamp.from(Instant.now()));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    EventoDominio leer(String mensaje) {
        try {
            JsonNode raiz = objectMapper.readTree(mensaje);
            // Sin "raw message delivery", SNS envuelve el evento: {"Type":"Notification","Message":"{...}"}
            if (raiz.has("Message") && raiz.has("TopicArn")) {
                raiz = objectMapper.readTree(raiz.get("Message").asText());
            }
            return objectMapper.treeToValue(raiz, EventoDominio.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Mensaje que no es un evento de StaySync: " + e.getOriginalMessage(), e);
        }
    }
}
