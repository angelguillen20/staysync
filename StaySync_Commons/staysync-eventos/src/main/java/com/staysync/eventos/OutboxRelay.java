package com.staysync.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Publica en SNS los eventos pendientes del outbox, en orden. Si SNS falla, el evento queda
 * pendiente y se reintenta en la siguiente pasada (hasta {@link #MAX_INTENTOS}).
 * Con varias instancias un evento puede publicarse dos veces: por eso los consumidores
 * son idempotentes (ver {@link ProcesadorEventos}).
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final int MAX_INTENTOS = 10;
    private static final int LOTE = 50;

    private final JdbcTemplate jdbcTemplate;
    private final SnsClient snsClient;
    private final String topicArn;

    public OutboxRelay(JdbcTemplate jdbcTemplate, SnsClient snsClient, String topicArn) {
        this.jdbcTemplate = jdbcTemplate;
        this.snsClient = snsClient;
        this.topicArn = topicArn;
    }

    @Scheduled(fixedDelayString = "${staysync.eventos.relay-ms:2000}")
    public void publicarPendientes() {
        List<Map<String, Object>> pendientes = jdbcTemplate.queryForList(
                "SELECT id, tipo, tenant_id, contenido FROM outbox_eventos "
                        + "WHERE publicado_en IS NULL AND intentos < ? ORDER BY id LIMIT " + LOTE,
                MAX_INTENTOS);
        for (Map<String, Object> fila : pendientes) {
            publicar(fila);
        }
    }

    private void publicar(Map<String, Object> fila) {
        Object id = fila.get("id");
        String tipo = (String) fila.get("tipo");
        try {
            snsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message((String) fila.get("contenido"))
                    .messageAttributes(Map.of(
                            TiposEvento.ATRIBUTO_TIPO, atributo(tipo),
                            TiposEvento.ATRIBUTO_TENANT, atributo((String) fila.get("tenant_id"))))
                    .build());
            jdbcTemplate.update("UPDATE outbox_eventos SET publicado_en = ? WHERE id = ?",
                    Timestamp.from(Instant.now()), id);
            log.info("Evento {} publicado en SNS (outbox id={})", tipo, id);
        } catch (RuntimeException e) {
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            jdbcTemplate.update("UPDATE outbox_eventos SET intentos = intentos + 1, ultimo_error = ? WHERE id = ?",
                    error.length() > 500 ? error.substring(0, 500) : error, id);
            log.warn("No se pudo publicar el evento {} (outbox id={}): {}", tipo, id, error);
        }
    }

    private static MessageAttributeValue atributo(String valor) {
        return MessageAttributeValue.builder().dataType("String").stringValue(valor).build();
    }
}
