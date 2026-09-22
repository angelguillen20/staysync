package com.staysync.notificaciones.service;

import com.staysync.notificaciones.messaging.event.ReservaCreadaEvent;
import com.staysync.notificaciones.messaging.event.ReservaEstadoCambiadoEvent;
import com.staysync.notificaciones.messaging.event.UsuarioRegistradoEvent;
import com.staysync.notificaciones.model.Notificacion;
import com.staysync.notificaciones.model.Notificacion.EstadoNotificacion;
import com.staysync.notificaciones.model.PlantillaNotificacion;
import com.staysync.notificaciones.repository.NotificacionRepository;
import com.staysync.notificaciones.repository.PlantillaNotificacionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificacionService - Tests Unitarios")
class NotificacionServiceTest {

    @Mock private NotificacionRepository          notificacionRepository;
    @Mock private PlantillaNotificacionRepository plantillaRepository;
    @Mock private JavaMailSender                  mailSender;

    @InjectMocks private NotificacionService notificacionService;

    private PlantillaNotificacion plantillaReserva;
    private PlantillaNotificacion plantillaUsuario;
    private PlantillaNotificacion plantillaEstado;

    @BeforeEach
    void setUp() {
        // FIX: @InjectMocks no procesa @Value — mailEnabled queda false por defecto.
        // Lo forzamos a true para probar el path real de envío de email.
        ReflectionTestUtils.setField(notificacionService, "mailEnabled", true);

        plantillaReserva = buildPlantilla("RESERVA_CREADA",
                "Reserva {{codigo}} confirmada",
                "<h1>Hola {{nombreUsuario}}, tu reserva {{codigo}} está lista.</h1>");

        plantillaUsuario = buildPlantilla("USUARIO_REGISTRO",
                "Bienvenido {{nombre}}",
                "<p>Hola {{nombre}}, tu cuenta {{email}} fue creada.</p>");

        plantillaEstado = buildPlantilla("RESERVA_ESTADO",
                "Reserva {{codigo}} actualizada",
                "<p>Tu reserva {{codigo}} cambió a {{estadoNuevo}}.</p>");
    }

    // ── procesarEvento() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("procesarEvento() - debe crear notificación, renderizar plantilla y enviar email")
    void debeCrearNotificacionYEnviarEmail() {
        Map<String, Object> vars = Map.of(
                "codigo",        "RES-001",
                "nombreUsuario", "Juan García",
                "email",         "juan@test.com"
        );

        when(plantillaRepository.findByCodigo("RESERVA_CREADA")).thenReturn(Optional.of(plantillaReserva));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificacionService.procesarEvento("RESERVA_CREADA", 1L, "juan@test.com", vars);

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(notificacionRepository, atLeast(2)).save(any(Notificacion.class));
    }

    @Test
    @DisplayName("procesarEvento() - sin plantilla debe guardar notificación genérica y enviar email igual")
    void debeManejarPlantillaInexistente() {
        Map<String, Object> vars = Map.of("key", "value");

        when(plantillaRepository.findByCodigo("INEXISTENTE")).thenReturn(Optional.empty());
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificacionService.procesarEvento("INEXISTENTE", 1L, "test@test.com", vars);

        // El servicio envía el email igualmente aunque no haya plantilla (con asunto genérico)
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(notificacionRepository, atLeast(2)).save(any(Notificacion.class));
    }

    // ── procesarRegistroUsuario() ────────────────────────────────────────────

    @Test
    @DisplayName("procesarRegistroUsuario() - debe enviar email de bienvenida al usuario registrado")
    void debeProcesarRegistroUsuario() {
        // FIX: UsuarioRegistradoEvent es un record — usa constructor canónico, NO setters
        UsuarioRegistradoEvent event = new UsuarioRegistradoEvent(1L, "Ana", "ana@test.com");

        when(plantillaRepository.findByCodigo("USUARIO_REGISTRO")).thenReturn(Optional.of(plantillaUsuario));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificacionService.procesarRegistroUsuario(event);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // ── procesarReservaCreada() ──────────────────────────────────────────────

    @Test
    @DisplayName("procesarReservaCreada() - debe enviar confirmación al huésped con datos de la reserva")
    void debeProcesarReservaCreada() {
        // FIX: ReservaCreadaEvent es un record — precioTotal es Double, no String
        ReservaCreadaEvent event = new ReservaCreadaEvent(
                1L, 1L, "juan@test.com", "Juan", "RES-001", "101",
                "2026-12-01", "2026-12-05", 400.0
        );

        when(plantillaRepository.findByCodigo("RESERVA_CREADA")).thenReturn(Optional.of(plantillaReserva));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificacionService.procesarReservaCreada(event);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // ── procesarCambioEstado() ───────────────────────────────────────────────

    @Test
    @DisplayName("procesarCambioEstado() - debe notificar cambio de estado al huésped")
    void debeProcesarCambioEstado() {
        // FIX: ReservaEstadoCambiadoEvent es un record — usa constructor canónico
        ReservaEstadoCambiadoEvent event = new ReservaEstadoCambiadoEvent(
                1L, 1L, "juan@test.com", "Juan", "RES-001", "101", "CHECKIN"
        );

        when(plantillaRepository.findByCodigo("RESERVA_ESTADO")).thenReturn(Optional.of(plantillaEstado));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificacionService.procesarCambioEstado(event);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // ── enviarEmail() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("enviarEmail() - debe cambiar estado a ENVIADO cuando el envío es exitoso")
    void debeMarcarEnviadoTrasEnvioExitoso() {
        Notificacion notif = buildNotificacion(1L, "ok@test.com", "Asunto", "Cuerpo",
                EstadoNotificacion.PENDIENTE);

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificacionService.enviarEmail(notif);

        assertThat(notif.getEstado()).isEqualTo(EstadoNotificacion.ENVIADO);
        assertThat(notif.getEnviadoEn()).isNotNull();
        verify(notificacionRepository).save(notif);
    }

    @Test
    @DisplayName("enviarEmail() - debe marcar FALLIDO después del máximo de intentos")
    void debeMarcarFallidoTrasMáxIntentos() {
        Notificacion notif = buildNotificacion(3L, "fail@test.com", "Test", "Cuerpo",
                EstadoNotificacion.PENDIENTE);
        notif.setIntentos(2);     // ya tuvo 2 intentos previos
        notif.setMaxIntentos(3);

        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificacionService.enviarEmail(notif);

        assertThat(notif.getEstado()).isEqualTo(EstadoNotificacion.FALLIDO);
        assertThat(notif.getIntentos()).isEqualTo(3);
        assertThat(notif.getErrorMsg()).isNotBlank();
    }

    @Test
    @DisplayName("enviarEmail() - no debe marcar FALLIDO si aún quedan intentos disponibles")
    void noDebeMarcarFallidoSiHayIntentosRestantes() {
        Notificacion notif = buildNotificacion(4L, "retry@test.com", "Test", "Cuerpo",
                EstadoNotificacion.PENDIENTE);
        notif.setIntentos(0);
        notif.setMaxIntentos(3);

        doThrow(new RuntimeException("SMTP timeout")).when(mailSender).send(any(SimpleMailMessage.class));
        when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificacionService.enviarEmail(notif);

        // Con 1 intento fallido sobre 3 máximos, el estado NO debe ser FALLIDO todavía
        assertThat(notif.getEstado()).isNotEqualTo(EstadoNotificacion.FALLIDO);
        assertThat(notif.getEstado()).isNotEqualTo(EstadoNotificacion.ENVIADO);
        assertThat(notif.getIntentos()).isEqualTo(1);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PlantillaNotificacion buildPlantilla(String codigo, String asunto, String cuerpo) {
        return PlantillaNotificacion.builder()
                .id(1L).codigo(codigo).nombre(codigo)
                .canal(Notificacion.Canal.EMAIL)
                .asunto(asunto).cuerpo(cuerpo)
                .activa(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private Notificacion buildNotificacion(Long id, String dest, String asunto,
                                           String cuerpo, EstadoNotificacion estado) {
        return Notificacion.builder()
                .id(id).usuarioId(1L)
                .canal(Notificacion.Canal.EMAIL)
                .destinatario(dest).asunto(asunto).cuerpo(cuerpo)
                .estado(estado).intentos(0).maxIntentos(3)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}
