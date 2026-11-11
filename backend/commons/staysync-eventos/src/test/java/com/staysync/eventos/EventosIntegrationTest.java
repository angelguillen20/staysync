package com.staysync.eventos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = EventosIntegrationTest.App.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:eventos;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.cloud.aws.region.static=us-east-1",
        "spring.cloud.aws.sns.enabled=false"
})
@DisplayName("Outbox, relay a SNS y consumo idempotente")
class EventosIntegrationTest {

    @SpringBootApplication
    static class App {
    }

    @Autowired Outbox outbox;
    @Autowired ProcesadorEventos procesador;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlatformTransactionManager txManager;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM outbox_eventos");
        jdbc.update("DELETE FROM eventos_procesados");
    }

    private String eventoJson(String tipo) throws Exception {
        return objectMapper.writeValueAsString(EventoDominio.nuevo(tipo, "hotel-andes", Map.of("reservaId", 7)));
    }

    @Test
    @DisplayName("El evento queda en el outbox solo si la transacción se confirma")
    void outboxAtadoALaTransaccion() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> outbox.registrar(TiposEvento.RESERVA_CREADA, "hotel-andes", Map.of("reservaId", 1)));
        tx.executeWithoutResult(s -> {
            outbox.registrar(TiposEvento.RESERVA_CANCELADA, "hotel-andes", Map.of("reservaId", 2));
            s.setRollbackOnly();
        });

        assertThat(jdbc.queryForList("SELECT tipo FROM outbox_eventos", String.class))
                .containsExactly(TiposEvento.RESERVA_CREADA);
    }

    @Test
    @DisplayName("El manejador corre una sola vez aunque el mensaje llegue duplicado, con el hotel del evento")
    void consumoIdempotente() throws Exception {
        String json = eventoJson(TiposEvento.PAGO_EFECTUADO);
        AtomicInteger veces = new AtomicInteger();
        AtomicReference<String> hotelVisto = new AtomicReference<>();
        Map<String, java.util.function.Consumer<EventoDominio>> manejadores = Map.of(TiposEvento.PAGO_EFECTUADO, e -> {
            veces.incrementAndGet();
            hotelVisto.set(TenantContext.require());
        });

        assertThat(procesador.procesar(json, manejadores)).isTrue();
        assertThat(procesador.procesar(json, manejadores)).isFalse();

        assertThat(veces.get()).isEqualTo(1);
        assertThat(hotelVisto.get()).isEqualTo("hotel-andes");
        assertThat(TenantContext.get()).isEmpty();
    }

    @Test
    @DisplayName("Si el manejador falla, el evento no queda marcado y se puede reintentar")
    void falloPermiteReintento() throws Exception {
        String json = eventoJson(TiposEvento.RESERVA_CANCELADA);
        Map<String, java.util.function.Consumer<EventoDominio>> falla = Map.of(TiposEvento.RESERVA_CANCELADA, e -> {
            throw new IllegalStateException("BD caída");
        });

        assertThatThrownBy(() -> procesador.procesar(json, falla)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eventos_procesados", Integer.class)).isZero();

        assertThat(procesador.procesar(json, Map.of(TiposEvento.RESERVA_CANCELADA, e -> { }))).isTrue();
    }

    @Test
    @DisplayName("Un tipo sin manejador se ignora (la cola recibe solo lo que filtra SNS)")
    void tipoSinManejador() throws Exception {
        assertThat(procesador.procesar(eventoJson(TiposEvento.RESERVA_CHECKIN), Map.of())).isFalse();
    }

    @Test
    @DisplayName("Lee el evento aunque SNS lo entregue envuelto en su notificación")
    void leeNotificacionSns() throws Exception {
        String interno = eventoJson(TiposEvento.RESERVA_CONFIRMADA);
        String envuelto = objectMapper.writeValueAsString(Map.of(
                "Type", "Notification", "TopicArn", "arn:aws:sns:us-east-1:000000000000:staysync-eventos",
                "Message", interno));

        assertThat(procesador.leer(envuelto).tipo()).isEqualTo(TiposEvento.RESERVA_CONFIRMADA);
    }

    @Test
    @DisplayName("El relay publica en SNS con el atributo tipo y marca el evento como publicado")
    void relayPublica() {
        new TransactionTemplate(txManager).executeWithoutResult(s ->
                outbox.registrar(TiposEvento.PAGO_EFECTUADO, "hotel-andes", Map.of("reservaId", 9)));
        SnsClient sns = mock(SnsClient.class);

        new OutboxRelay(jdbc, sns, "arn:aws:sns:us-east-1:000000000000:staysync-eventos").publicarPendientes();

        verify(sns).publish(argThatTipo(TiposEvento.PAGO_EFECTUADO));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_eventos WHERE publicado_en IS NULL", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Si SNS está caído, el evento queda pendiente con el error y sin aplazar (conserva el orden)")
    void relayReintenta() {
        registrar(TiposEvento.PAGO_EFECTUADO);
        SnsClient sns = mock(SnsClient.class);
        when(sns.publish(any(PublishRequest.class))).thenThrow(new RuntimeException("SNS no disponible"));

        new OutboxRelay(jdbc, sns, "arn").publicarPendientes();

        List<Map<String, Object>> filas = jdbc.queryForList("SELECT intentos, ultimo_error, proximo_intento FROM outbox_eventos WHERE publicado_en IS NULL");
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).get("INTENTOS")).isEqualTo(1);
        assertThat(filas.get(0).get("ULTIMO_ERROR")).isEqualTo("SNS no disponible");
        assertThat(filas.get(0).get("PROXIMO_INTENTO")).isNull();
    }

    @Test
    @DisplayName("Con SNS caído el relay se pausa, corta la pasada y al volver publica en el orden original")
    void relayPausaYConservaElOrden() {
        registrar(TiposEvento.RESERVA_CONFIRMADA);
        registrar(TiposEvento.RESERVA_CANCELADA);
        SnsClient sns = mock(SnsClient.class);
        when(sns.publish(any(PublishRequest.class)))
                .thenThrow(new RuntimeException("SNS no disponible"))
                .thenReturn(null);
        RelojMovil reloj = new RelojMovil();
        OutboxRelay relay = new OutboxRelay(jdbc, sns, "arn", reloj);

        relay.publicarPendientes();          // falla el primero y corta: no intenta el segundo
        relay.publicarPendientes();          // en pausa: no llama a SNS
        verify(sns, times(1)).publish(any(PublishRequest.class));

        reloj.avanzar(OutboxRelay.ESPERA_BASE);
        relay.publicarPendientes();          // SNS volvió: publica los dos, el más antiguo primero

        var orden = inOrder(sns);
        orden.verify(sns, times(2)).publish(argThatTipo(TiposEvento.RESERVA_CONFIRMADA));
        orden.verify(sns).publish(argThatTipo(TiposEvento.RESERVA_CANCELADA));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_eventos WHERE publicado_en IS NULL", Integer.class)).isZero();
    }

    @Test
    @DisplayName("La pausa crece con cada fallo seguido de SNS")
    void relayPausaCreciente() {
        registrar(TiposEvento.PAGO_EFECTUADO);
        SnsClient sns = mock(SnsClient.class);
        when(sns.publish(any(PublishRequest.class))).thenThrow(new RuntimeException("SNS no disponible"));
        RelojMovil reloj = new RelojMovil();
        OutboxRelay relay = new OutboxRelay(jdbc, sns, "arn", reloj);

        relay.publicarPendientes();                      // fallo 1: pausa 2 s
        reloj.avanzar(Duration.ofSeconds(2));
        relay.publicarPendientes();                      // fallo 2: pausa 4 s
        reloj.avanzar(Duration.ofSeconds(3));
        relay.publicarPendientes();                      // aún en pausa
        verify(sns, times(2)).publish(any(PublishRequest.class));

        reloj.avanzar(Duration.ofSeconds(1));
        relay.publicarPendientes();
        verify(sns, times(3)).publish(any(PublishRequest.class));
    }

    @Test
    @DisplayName("Un evento que SNS rechaza (4xx) se aplaza solo y no bloquea a los siguientes")
    void eventoRechazadoNoBloquea() {
        registrar(TiposEvento.RESERVA_CREADA);
        registrar(TiposEvento.PAGO_EFECTUADO);
        SnsClient sns = mock(SnsClient.class);
        when(sns.publish(argThatTipo(TiposEvento.RESERVA_CREADA))).thenThrow(snsError(400, "InvalidParameter"));
        OutboxRelay relay = new OutboxRelay(jdbc, sns, "arn");

        relay.publicarPendientes();
        relay.publicarPendientes();   // el rechazado está aplazado: no se reintenta todavía

        verify(sns, times(1)).publish(argThatTipo(TiposEvento.RESERVA_CREADA));
        verify(sns, times(1)).publish(argThatTipo(TiposEvento.PAGO_EFECTUADO));
        Map<String, Object> rechazado = jdbc.queryForMap(
                "SELECT intentos, proximo_intento, publicado_en FROM outbox_eventos WHERE tipo = ?", TiposEvento.RESERVA_CREADA);
        assertThat(rechazado.get("INTENTOS")).isEqualTo(1);
        assertThat(rechazado.get("PROXIMO_INTENTO")).isNotNull();
        assertThat(rechazado.get("PUBLICADO_EN")).isNull();
    }

    @Test
    @DisplayName("Distingue errores generales de SNS de los rechazos de un mensaje")
    void clasificaErrores() {
        assertThat(OutboxRelay.esErrorGeneral(new RuntimeException("timeout"))).isTrue();
        assertThat(OutboxRelay.esErrorGeneral(snsError(500, "InternalError"))).isTrue();
        assertThat(OutboxRelay.esErrorGeneral(snsError(403, "AuthorizationError"))).isTrue();
        assertThat(OutboxRelay.esErrorGeneral(snsError(400, "Throttling"))).isTrue();
        assertThat(OutboxRelay.esErrorGeneral(snsError(404, "NotFound"))).isTrue();
        assertThat(OutboxRelay.esErrorGeneral(snsError(400, "KMSDisabled"))).isTrue();
        assertThat(OutboxRelay.esErrorGeneral(snsError(400, "InvalidParameter", "Invalid parameter: TopicArn"))).isTrue();
        assertThat(OutboxRelay.esErrorGeneral(snsError(400, "InvalidParameter", "Invalid parameter: Message too long"))).isFalse();
        assertThat(OutboxRelay.esErrorGeneral(snsError(400, "InvalidParameter"))).isFalse();
    }

    @Test
    @DisplayName("Con el topic inexistente (404) pausa el relay en vez de aplazar evento por evento")
    void topicInexistentePausa() {
        registrar(TiposEvento.RESERVA_CHECKIN);
        registrar(TiposEvento.RESERVA_CHECKOUT);
        SnsClient sns = mock(SnsClient.class);
        when(sns.publish(any(PublishRequest.class))).thenThrow(snsError(404, "NotFound", "Topic does not exist"));

        new OutboxRelay(jdbc, sns, "arn").publicarPendientes();

        verify(sns, times(1)).publish(any(PublishRequest.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_eventos WHERE proximo_intento IS NOT NULL", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Nunca abandona un evento: lo publica aunque lleve muchos intentos fallidos")
    void relayNoAbandona() {
        registrar(TiposEvento.PAGO_EFECTUADO);
        jdbc.update("UPDATE outbox_eventos SET intentos = 25");
        SnsClient sns = mock(SnsClient.class);

        new OutboxRelay(jdbc, sns, "arn").publicarPendientes();

        verify(sns).publish(argThatTipo(TiposEvento.PAGO_EFECTUADO));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_eventos WHERE publicado_en IS NULL", Integer.class)).isZero();
    }

    @Test
    @DisplayName("La espera se duplica en cada fallo, con tope de 1 minuto")
    void esperaCreciente() {
        assertThat(OutboxRelay.espera(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(OutboxRelay.espera(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(OutboxRelay.espera(5)).isEqualTo(Duration.ofSeconds(32));
        assertThat(OutboxRelay.espera(6)).isEqualTo(Duration.ofMinutes(1));
        assertThat(OutboxRelay.espera(1000)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("Migra un outbox antiguo: agrega proximo_intento y aparca los eventos que la versión anterior abandonó")
    void migraOutboxAntiguo() {
        jdbc.execute("DROP TABLE outbox_eventos");
        jdbc.update("DELETE FROM eventos_migraciones");
        jdbc.execute("""
                CREATE TABLE outbox_eventos (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(36) NOT NULL, tipo VARCHAR(60) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL, contenido TEXT NOT NULL, creado_en TIMESTAMP NOT NULL,
                    publicado_en TIMESTAMP NULL, intentos INT NOT NULL DEFAULT 0, ultimo_error VARCHAR(500) NULL,
                    CONSTRAINT uk_outbox_event_id UNIQUE (event_id))""");
        jdbc.update("INSERT INTO outbox_eventos (event_id, tipo, tenant_id, contenido, creado_en, intentos) "
                + "VALUES ('abandonado', 'ReservaCheckin', 'hotel-andes', '{}', CURRENT_TIMESTAMP, 10)");
        jdbc.update("INSERT INTO outbox_eventos (event_id, tipo, tenant_id, contenido, creado_en, intentos) "
                + "VALUES ('en-curso', 'PagoEfectuado', 'hotel-andes', '{}', CURRENT_TIMESTAMP, 3)");

        new EsquemaEventos(jdbc).afterPropertiesSet();
        new EsquemaEventos(jdbc).afterPropertiesSet();   // idempotente

        assertThat(jdbc.queryForObject("SELECT proximo_intento FROM outbox_eventos WHERE event_id = 'abandonado'",
                Timestamp.class)).isEqualTo(EsquemaEventos.APARCADO);
        assertThat(jdbc.queryForObject("SELECT proximo_intento FROM outbox_eventos WHERE event_id = 'en-curso'",
                Timestamp.class)).isNull();

        SnsClient sns = mock(SnsClient.class);
        new OutboxRelay(jdbc, sns, "arn").publicarPendientes();
        verify(sns, times(1)).publish(argThatTipo(TiposEvento.PAGO_EFECTUADO));
        verify(sns, never()).publish(argThatTipo(TiposEvento.RESERVA_CHECKIN));
    }

    @Test
    @DisplayName("Si el arranque se cortó tras agregar la columna, el siguiente arranque aparca igual")
    void aparcaTrasMigracionInterrumpida() {
        jdbc.update("DELETE FROM eventos_migraciones");
        jdbc.update("INSERT INTO outbox_eventos (event_id, tipo, tenant_id, contenido, creado_en, intentos) "
                + "VALUES ('abandonado', 'ReservaCheckin', 'hotel-andes', '{}', CURRENT_TIMESTAMP, 12)");

        new EsquemaEventos(jdbc).afterPropertiesSet();

        assertThat(jdbc.queryForObject("SELECT proximo_intento FROM outbox_eventos", Timestamp.class))
                .isEqualTo(EsquemaEventos.APARCADO);
    }

    @Test
    @DisplayName("Una vez migrado, un evento con muchos fallos de la versión nueva no se aparca al reiniciar")
    void noAparcaTrasMigrar() {
        new EsquemaEventos(jdbc).afterPropertiesSet();
        registrar(TiposEvento.PAGO_EFECTUADO);
        jdbc.update("UPDATE outbox_eventos SET intentos = 40");

        new EsquemaEventos(jdbc).afterPropertiesSet();

        assertThat(jdbc.queryForObject("SELECT proximo_intento FROM outbox_eventos", Timestamp.class)).isNull();
    }

    private void registrar(String tipo) {
        new TransactionTemplate(txManager).executeWithoutResult(s ->
                outbox.registrar(tipo, "hotel-andes", Map.of("reservaId", 9)));
    }

    private static SnsException snsError(int status, String codigo) {
        return snsError(status, codigo, null);
    }

    private static SnsException snsError(int status, String codigo, String mensaje) {
        return (SnsException) SnsException.builder()
                .statusCode(status)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode(codigo).errorMessage(mensaje).build())
                .build();
    }

    /** Reloj que solo avanza cuando el test lo pide. */
    static class RelojMovil extends Clock {
        private Instant ahora = Instant.now();

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private static PublishRequest argThatTipo(String tipo) {
        return argThat(r -> tipo.equals(r.messageAttributes().get(TiposEvento.ATRIBUTO_TIPO).stringValue()));
    }
}
