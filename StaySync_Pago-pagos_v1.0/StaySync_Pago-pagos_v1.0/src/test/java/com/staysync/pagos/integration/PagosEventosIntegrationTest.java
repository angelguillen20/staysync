package com.staysync.pagos.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.eventos.EventoDominio;
import com.staysync.eventos.TiposEvento;
import com.staysync.pagos.messaging.EventosPagoListener;
import com.staysync.pagos.model.Pago;
import com.staysync.pagos.model.Pago.EstadoPago;
import com.staysync.pagos.model.Pago.MetodoPago;
import com.staysync.pagos.repository.PagoRepository;
import com.staysync.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pagos_ev;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "stripe.secret-key=sk_test_dummy"
})
@DisplayName("Pagos - eventos y aislamiento por hotel (H2)")
class PagosEventosIntegrationTest {

    private static final String DEMO = "staysync-demo";
    private static final String ANDES = "hotel-andes";

    @Autowired PagoRepository pagoRepository;
    @Autowired EventosPagoListener listener;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    private Pago pagar(String hotel, Long reservaId, String referencia) {
        return TenantContext.ejecutarCon(hotel, () -> pagoRepository.save(Pago.builder()
                .referencia(referencia).reservaId(reservaId).usuarioId(1L)
                .monto(BigDecimal.valueOf(90000)).moneda("CLP")
                .metodoPago(MetodoPago.STRIPE).estado(EstadoPago.COMPLETADO).build()));
    }

    @AfterEach
    void limpiar() {
        TenantContext.ejecutarComoSistema(() -> pagoRepository.deleteAll());
        jdbc.update("DELETE FROM outbox_eventos");
        jdbc.update("DELETE FROM eventos_procesados");
    }

    @Test
    @DisplayName("ReservaCancelada reembolsa el pago de ese hotel y deja intacto el de otro hotel con el mismo reservaId")
    void cancelacionReembolsaSoloEnSuHotel() throws Exception {
        Pago demo = pagar(DEMO, 50L, "PAY-DEMO0001");
        Pago andes = pagar(ANDES, 50L, "PAY-ANDES001");

        listener.recibir(objectMapper.writeValueAsString(
                EventoDominio.nuevo(TiposEvento.RESERVA_CANCELADA, DEMO, Map.of("reservaId", 50))));

        EstadoPago estadoDemo = TenantContext.ejecutarCon(DEMO, () -> pagoRepository.findById(demo.getId()).get().getEstado());
        EstadoPago estadoAndes = TenantContext.ejecutarCon(ANDES, () -> pagoRepository.findById(andes.getId()).get().getEstado());
        assertThat(estadoDemo).isEqualTo(EstadoPago.REEMBOLSADO);
        assertThat(estadoAndes).isEqualTo(EstadoPago.COMPLETADO);

        assertThat(jdbc.queryForList("SELECT tenant_id FROM outbox_eventos WHERE tipo = ?", String.class,
                TiposEvento.REEMBOLSO_EFECTUADO)).containsExactly(DEMO);
    }

    @Test
    @DisplayName("Un hotel no ve los pagos de otro")
    void aislamiento() {
        pagar(ANDES, 60L, "PAY-ANDES002");

        assertThat(TenantContext.ejecutarCon(DEMO, () -> pagoRepository.findByReservaId(60L))).isEmpty();
        assertThat(TenantContext.ejecutarCon(ANDES, () -> pagoRepository.findByReservaId(60L))).hasSize(1);
    }
}
