package com.staysync.notificaciones.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.eventos.EventoDominio;
import com.staysync.eventos.TiposEvento;
import com.staysync.notificaciones.messaging.EventosNotificacionListener;
import com.staysync.notificaciones.model.Notificacion;
import com.staysync.notificaciones.repository.ContactoReservaRepository;
import com.staysync.notificaciones.repository.NotificacionRepository;
import com.staysync.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notif_ev;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.mail.password=test",
        "notificaciones.mail.enabled=false"
})
@DisplayName("Notificaciones - correos por eventos (H2)")
class NotificacionesEventosIntegrationTest {

    private static final String ANDES = "hotel-andes";

    @Autowired EventosNotificacionListener listener;
    @Autowired NotificacionRepository notificaciones;
    @Autowired ContactoReservaRepository contactos;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        TenantContext.ejecutarComoSistema(() -> {
            notificaciones.deleteAll();
            contactos.deleteAll();
        });
        jdbc.update("DELETE FROM eventos_procesados");
    }

    private void evento(String tipo, Map<String, Object> payload) throws Exception {
        listener.recibir(objectMapper.writeValueAsString(EventoDominio.nuevo(tipo, ANDES, payload)));
    }

    private Map<String, Object> reserva(String estado) {
        Map<String, Object> p = new HashMap<>();
        p.put("reservaId", 40);
        p.put("codigo", "RES-ANDES040");
        p.put("usuarioId", 7);
        p.put("habitacionId", 12);
        p.put("fechaEntrada", "2026-11-18");
        p.put("fechaSalida", "2026-11-20");
        p.put("precioTotal", 180000);
        p.put("estado", estado);
        p.put("huespedEmail", "ana@correo.cl");
        p.put("huespedNombre", "Ana Rojas");
        return p;
    }

    @Test
    @DisplayName("Reserva creada y pagada: dos correos al huésped, en su hotel; el recibo usa el contacto guardado")
    void reservaYPago() throws Exception {
        evento(TiposEvento.RESERVA_CREADA, reserva("PENDIENTE"));
        evento(TiposEvento.PAGO_EFECTUADO, Map.of("reservaId", 40, "monto", 180000, "moneda", "CLP",
                "referencia", "STRIPE-ABC123"));

        List<Notificacion> enviadas = TenantContext.ejecutarCon(ANDES, () -> notificaciones.findAll());
        assertThat(enviadas).hasSize(2).allMatch(n -> "ana@correo.cl".equals(n.getDestinatario()));
        assertThat(enviadas).extracting(Notificacion::getAsunto)
                .anyMatch(a -> a.contains("Reserva registrada #RES-ANDES040"))
                .anyMatch(a -> a.contains("Recibo de tu pago"));
        assertThat(enviadas).allMatch(n -> ANDES.equals(n.getTenantId()));
    }

    @Test
    @DisplayName("Un pago sin contacto conocido no rompe el consumo: no se envía correo")
    void pagoSinContacto() throws Exception {
        evento(TiposEvento.PAGO_EFECTUADO, Map.of("reservaId", 999, "monto", 1, "moneda", "CLP", "referencia", "X"));

        assertThat(TenantContext.ejecutarCon(ANDES, () -> notificaciones.findAll())).isEmpty();
    }
}
