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
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

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
    @DisplayName("Si SNS falla, el evento queda pendiente con el error registrado")
    void relayReintenta() {
        new TransactionTemplate(txManager).executeWithoutResult(s ->
                outbox.registrar(TiposEvento.PAGO_EFECTUADO, "hotel-andes", Map.of("reservaId", 9)));
        SnsClient sns = mock(SnsClient.class);
        when(sns.publish(any(PublishRequest.class))).thenThrow(new RuntimeException("SNS no disponible"));

        new OutboxRelay(jdbc, sns, "arn").publicarPendientes();

        List<Map<String, Object>> filas = jdbc.queryForList("SELECT intentos, ultimo_error FROM outbox_eventos WHERE publicado_en IS NULL");
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).get("INTENTOS")).isEqualTo(1);
        assertThat(filas.get(0).get("ULTIMO_ERROR")).isEqualTo("SNS no disponible");
    }

    private static PublishRequest argThatTipo(String tipo) {
        return argThat(r -> tipo.equals(r.messageAttributes().get(TiposEvento.ATRIBUTO_TIPO).stringValue()));
    }
}
