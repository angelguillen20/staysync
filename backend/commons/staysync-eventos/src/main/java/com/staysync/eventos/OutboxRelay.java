package com.staysync.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Publica en SNS los eventos pendientes del outbox, en orden. Ningún evento se abandona.
 * <ul>
 *   <li>Error general (SNS caído, timeout, credenciales, throttling, topic inexistente...): se
 *       corta la pasada y el relay entero se pausa con espera creciente ({@link #ESPERA_BASE}, el doble en cada fallo
 *       seguido, hasta {@link #ESPERA_MAX}). Las filas no se marcan, así que al volver SNS se
 *       sigue desde el evento más antiguo y se conserva el orden.</li>
 *   <li>SNS rechaza ese mensaje concreto (InvalidParameter sobre el mensaje): solo ese evento se aplaza
 *       (proximo_intento) y la pasada sigue, para que un evento defectuoso no bloquee al resto.</li>
 * </ul>
 * Con varias instancias un evento puede publicarse dos veces: por eso los consumidores
 * son idempotentes (ver {@link ProcesadorEventos}).
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final Duration ESPERA_BASE = Duration.ofSeconds(2);
    static final Duration ESPERA_MAX = Duration.ofMinutes(1);
    /** Cada cuántos fallos seguidos se loguea como error, para que una caída larga se note. */
    static final int AVISO_CADA = 10;
    private static final int LOTE = 50;

    private final JdbcTemplate jdbcTemplate;
    private final SnsClient snsClient;
    private final String topicArn;
    private final Clock clock;

    /** Fallos generales seguidos y momento hasta el que el relay no publica. */
    private int fallosSeguidos;
    private Instant pausaHasta = Instant.MIN;

    public OutboxRelay(JdbcTemplate jdbcTemplate, SnsClient snsClient, String topicArn) {
        this(jdbcTemplate, snsClient, topicArn, Clock.systemUTC());
    }

    OutboxRelay(JdbcTemplate jdbcTemplate, SnsClient snsClient, String topicArn, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.snsClient = snsClient;
        this.topicArn = topicArn;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${staysync.eventos.relay-ms:2000}")
    public synchronized void publicarPendientes() {
        Instant ahora = clock.instant();
        if (ahora.isBefore(pausaHasta)) {
            return;
        }
        List<Map<String, Object>> pendientes = jdbcTemplate.queryForList(
                "SELECT id, tipo, tenant_id, contenido, intentos FROM outbox_eventos "
                        + "WHERE publicado_en IS NULL AND (proximo_intento IS NULL OR proximo_intento <= ?) "
                        + "ORDER BY id LIMIT " + LOTE,
                Timestamp.from(ahora));
        for (Map<String, Object> fila : pendientes) {
            if (!publicar(fila)) {
                return;
            }
        }
    }

    /** Devuelve false si hay que cortar la pasada (error general de SNS). */
    private boolean publicar(Map<String, Object> fila) {
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
                    Timestamp.from(clock.instant()), id);
            log.info("Evento {} publicado en SNS (outbox id={})", tipo, id);
            fallosSeguidos = 0;
            return true;
        } catch (RuntimeException e) {
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String errorGuardado = error.length() > 500 ? error.substring(0, 500) : error;
            if (esErrorGeneral(e)) {
                fallosSeguidos++;
                Duration espera = espera(fallosSeguidos);
                pausaHasta = clock.instant().plus(espera);
                jdbcTemplate.update("UPDATE outbox_eventos SET intentos = intentos + 1, ultimo_error = ? WHERE id = ?",
                        errorGuardado, id);
                if (fallosSeguidos % AVISO_CADA == 0) {
                    log.error("SNS lleva {} fallos seguidos; los eventos esperan en el outbox. Último: {}",
                            fallosSeguidos, error);
                } else {
                    log.warn("No se pudo publicar el evento {} (outbox id={}): {}. Relay en pausa {} s",
                            tipo, id, error, espera.toSeconds());
                }
                return false;
            }
            int intentos = ((Number) fila.get("intentos")).intValue() + 1;
            Duration espera = espera(intentos);
            jdbcTemplate.update("UPDATE outbox_eventos SET intentos = intentos + 1, proximo_intento = ?, "
                            + "ultimo_error = ? WHERE id = ?",
                    Timestamp.from(clock.instant().plus(espera)), errorGuardado, id);
            if (intentos % AVISO_CADA == 0) {
                log.error("SNS rechaza el evento {} (outbox id={}) tras {} intentos: {}", tipo, id, intentos, error);
            } else {
                log.warn("SNS rechazó el evento {} (outbox id={}, intento {}): {}. Se reintenta en {} s",
                        tipo, id, intentos, error, espera.toSeconds());
            }
            return true;
        }
    }

    /**
     * false solo si SNS rechaza este mensaje en concreto (InvalidParameter sobre el mensaje o sus
     * atributos, p. ej. "Message too long"): los demás eventos pueden seguir. Todo lo demás afecta a
     * cualquier mensaje y se trata como general: red, 5xx, credenciales, throttling, topic
     * inexistente (404) o mal configurado, KMS...
     */
    static boolean esErrorGeneral(RuntimeException e) {
        if (e instanceof AwsServiceException servicio && servicio.awsErrorDetails() != null
                && !servicio.isThrottlingException()) {
            String codigo = servicio.awsErrorDetails().errorCode();
            String mensaje = String.valueOf(servicio.awsErrorDetails().errorMessage()).toLowerCase();
            boolean rechazaElMensaje = "InvalidParameter".equals(codigo) || "InvalidParameterValue".equals(codigo);
            return !rechazaElMensaje || mensaje.contains("topic");
        }
        return true;
    }

    /** Espera tras el fallo número {@code fallos}: 2 s, 4 s, 8 s... hasta 1 min. */
    static Duration espera(int fallos) {
        Duration espera = ESPERA_BASE.multipliedBy(1L << Math.min(Math.max(fallos - 1, 0), 10));
        return espera.compareTo(ESPERA_MAX) > 0 ? ESPERA_MAX : espera;
    }

    private static MessageAttributeValue atributo(String valor) {
        return MessageAttributeValue.builder().dataType("String").stringValue(valor).build();
    }
}
