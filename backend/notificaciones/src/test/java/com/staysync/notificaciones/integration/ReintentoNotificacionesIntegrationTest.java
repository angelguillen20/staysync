package com.staysync.notificaciones.integration;

import com.staysync.notificaciones.model.Notificacion;
import com.staysync.notificaciones.model.Notificacion.EstadoNotificacion;
import com.staysync.notificaciones.repository.NotificacionRepository;
import com.staysync.notificaciones.service.NotificacionService;
import com.staysync.notificaciones.service.ReintentoNotificacionesScheduler;
import com.staysync.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notif_reintentos;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.mail.password=test",
        "notificaciones.mail.enabled=true",
        "notificaciones.mail.from=no-reply@staysync.test",
        // Sin espera entre intentos y sin que el job programado corra solo durante el test
        "notificaciones.reintentos.espera-base=0s",
        "notificaciones.reintentos.revision-ms=3600000"
})
@DisplayName("Notificaciones - reintento de correos fallidos (H2)")
class ReintentoNotificacionesIntegrationTest {

    private static final String ANDES = "hotel-andes";

    @MockitoBean JavaMailSender mailSender;
    @Autowired NotificacionService service;
    @Autowired ReintentoNotificacionesScheduler scheduler;
    @Autowired NotificacionRepository notificaciones;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        TenantContext.ejecutarComoSistema(() -> notificaciones.deleteAll());
    }

    private Notificacion unica() {
        return TenantContext.ejecutarComoSistema(() -> notificaciones.findAll()).get(0);
    }

    private void enviarCorreoDelHotel() {
        TenantContext.ejecutarCon(ANDES, () ->
                service.procesarEvento("RESERVA_CREADA", 7L, "ana@correo.cl", Map.of("codigo", "RES-1")));
    }

    @Test
    @DisplayName("Si el SMTP falla y luego vuelve, el job reenvía el correo y queda ENVIADO en su hotel")
    void reintentaHastaEnviar() {
        doThrow(new MailSendException("SMTP caído")).doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        enviarCorreoDelHotel();
        assertThat(unica().getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(unica().getIntentos()).isEqualTo(1);

        scheduler.reintentarPendientes();

        Notificacion n = unica();
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.ENVIADO);
        assertThat(n.getEnviadoEn()).isNotNull();
        assertThat(n.getTenantId()).isEqualTo(ANDES);
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Si el SMTP sigue caído, queda FALLIDO al agotar los intentos y no se reintenta más")
    void quedaFallidoAlAgotarIntentos() {
        doThrow(new MailSendException("SMTP caído")).when(mailSender).send(any(SimpleMailMessage.class));

        enviarCorreoDelHotel();
        int max = unica().getMaxIntentos();
        for (int i = 0; i < max + 2; i++) {
            scheduler.reintentarPendientes();
        }

        Notificacion n = unica();
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.FALLIDO);
        assertThat(n.getIntentos()).isEqualTo(max);
        assertThat(n.getErrorMsg()).contains("SMTP caído");
        verify(mailSender, times(max)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Un correo pendiente desde hace más de 24 h queda FALLIDO sin enviarse")
    void caducaLosPendientesAntiguos() {
        doThrow(new MailSendException("SMTP caído")).doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        enviarCorreoDelHotel();
        jdbc.update("UPDATE notificaciones SET created_at = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(3)));

        scheduler.reintentarPendientes();

        Notificacion n = unica();
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.FALLIDO);
        assertThat(n.getErrorMsg()).startsWith("Caducado sin enviar.").contains("SMTP caído");
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Un correo ya enviado no se reintenta")
    void noReenviaLosEnviados() {
        enviarCorreoDelHotel();

        scheduler.reintentarPendientes();

        assertThat(unica().getEstado()).isEqualTo(EstadoNotificacion.ENVIADO);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}
