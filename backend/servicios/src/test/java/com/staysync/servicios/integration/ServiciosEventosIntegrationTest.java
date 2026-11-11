package com.staysync.servicios.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.eventos.EventoDominio;
import com.staysync.eventos.TiposEvento;
import com.staysync.servicios.messaging.EventosServiciosListener;
import com.staysync.servicios.model.TareaAseo;
import com.staysync.servicios.model.TareaAseo.EstadoAseo;
import com.staysync.servicios.repository.TareaAseoRepository;
import com.staysync.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:servicios_ev;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Servicios - tareas de aseo por eventos (H2)")
class ServiciosEventosIntegrationTest {

    private static final String DEMO = "staysync-demo";
    private static final String ANDES = "hotel-andes";

    @Autowired EventosServiciosListener listener;
    @Autowired TareaAseoRepository repository;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        TenantContext.ejecutarComoSistema(() -> repository.deleteAll());
        jdbc.update("DELETE FROM eventos_procesados");
    }

    private void evento(String tipo, String hotel, long reservaId) throws Exception {
        listener.recibir(objectMapper.writeValueAsString(EventoDominio.nuevo(tipo, hotel, Map.of(
                "reservaId", reservaId, "habitacionId", 12, "fechaSalida", "2026-11-20"))));
    }

    private List<TareaAseo> tareas(String hotel) {
        return TenantContext.ejecutarCon(hotel, () -> repository.findAll());
    }

    @Test
    @DisplayName("Confirmada programa el aseo; un evento repetido no duplica la tarea")
    void confirmadaProgramaUnaVez() throws Exception {
        evento(TiposEvento.RESERVA_CONFIRMADA, DEMO, 1);
        evento(TiposEvento.RESERVA_CONFIRMADA, DEMO, 1);

        assertThat(tareas(DEMO)).singleElement()
                .satisfies(t -> {
                    assertThat(t.getEstado()).isEqualTo(EstadoAseo.PROGRAMADA);
                    assertThat(t.getFechaProgramada()).hasToString("2026-11-20");
                });
        assertThat(tareas(ANDES)).isEmpty();
    }

    @Test
    @DisplayName("Cancelada cancela el aseo programado; Checkout lo deja listo para limpiar")
    void cancelacionYCheckout() throws Exception {
        evento(TiposEvento.RESERVA_CONFIRMADA, ANDES, 2);
        evento(TiposEvento.RESERVA_CANCELADA, ANDES, 2);
        evento(TiposEvento.RESERVA_CONFIRMADA, ANDES, 3);
        evento(TiposEvento.RESERVA_CHECKOUT, ANDES, 3);

        assertThat(tareas(ANDES)).extracting(TareaAseo::getReservaId, TareaAseo::getEstado)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(2L, EstadoAseo.CANCELADA),
                        org.assertj.core.groups.Tuple.tuple(3L, EstadoAseo.PENDIENTE));
    }
}
