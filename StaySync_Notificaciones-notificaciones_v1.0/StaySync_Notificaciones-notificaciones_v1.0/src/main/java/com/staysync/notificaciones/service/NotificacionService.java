package com.staysync.notificaciones.service;

import com.staysync.notificaciones.messaging.event.ReservaCreadaEvent;
import com.staysync.notificaciones.messaging.event.ReservaEstadoCambiadoEvent;
import com.staysync.notificaciones.messaging.event.UsuarioRegistradoEvent;
import com.staysync.notificaciones.model.Notificacion;
import com.staysync.notificaciones.model.Notificacion.Canal;
import com.staysync.notificaciones.model.Notificacion.EstadoNotificacion;
import com.staysync.notificaciones.model.PlantillaNotificacion;
import com.staysync.notificaciones.repository.NotificacionRepository;
import com.staysync.notificaciones.repository.PlantillaNotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificacionService {

    private static final String PLANTILLA_USUARIO_REGISTRO = "USUARIO_REGISTRO";
    private static final String PLANTILLA_RESERVA_CREADA   = "RESERVA_CREADA";
    private static final String PLANTILLA_RESERVA_ESTADO   = "RESERVA_ESTADO";

    private final NotificacionRepository          notificacionRepository;
    private final PlantillaNotificacionRepository plantillaRepository;
    private final JavaMailSender                  mailSender;

    @Value("${notificaciones.mail.enabled:true}")
    private boolean mailEnabled;

    @Transactional
    public void procesarRegistroUsuario(UsuarioRegistradoEvent evento) {
        Map<String, Object> vars = Map.of(
                "nombre", evento.nombre(),
                "email",  evento.email()
        );
        procesarEvento(PLANTILLA_USUARIO_REGISTRO, evento.usuarioId(), evento.email(), vars);
    }

    @Transactional
    public void procesarReservaCreada(ReservaCreadaEvent evento) {
        Map<String, Object> vars = Map.of(
                "nombreUsuario", evento.nombreUsuario(),
                "codigo",        evento.codigo()      != null ? evento.codigo()      : "N/A",
                "habitacion",    evento.habitacion()   != null ? evento.habitacion()  : "N/A",
                "fechaEntrada",  evento.fechaEntrada() != null ? evento.fechaEntrada(): "N/A",
                "fechaSalida",   evento.fechaSalida()  != null ? evento.fechaSalida() : "N/A",
                "precioTotal",   evento.precioTotal()  != null ? evento.precioTotal().toString() : "0"
        );
        procesarEvento(PLANTILLA_RESERVA_CREADA, evento.usuarioId(), evento.email(), vars);
    }

    @Transactional
    public void procesarCambioEstado(ReservaEstadoCambiadoEvent evento) {
        Map<String, Object> vars = Map.of(
                "nombreUsuario", evento.nombreUsuario(),
                "codigo",        evento.codigo()     != null ? evento.codigo()     : "N/A",
                "habitacion",    evento.habitacion() != null ? evento.habitacion() : "N/A",
                "estadoNuevo",   traducirEstado(evento.estadoNuevo())
        );
        procesarEvento(PLANTILLA_RESERVA_ESTADO, evento.usuarioId(), evento.email(), vars);
    }

    @Transactional
    public void procesarEvento(String codigoPlantilla, Long usuarioId,
                               String destinatario, Map<String, Object> variables) {
        PlantillaNotificacion plantilla = plantillaRepository.findByCodigo(codigoPlantilla).orElse(null);

        String asunto = plantilla != null
                ? renderizar(plantilla.getAsunto(), variables)
                : "Notificación StaySync";
        String cuerpo = plantilla != null
                ? renderizar(plantilla.getCuerpo(), variables)
                : variables.toString();

        Notificacion notificacion = Notificacion.builder()
                .plantilla(plantilla)
                .usuarioId(usuarioId)
                .canal(Canal.EMAIL)
                .destinatario(destinatario)
                .asunto(asunto)
                .cuerpo(cuerpo)
                .estado(EstadoNotificacion.PENDIENTE)
                .build();

        notificacion = notificacionRepository.save(notificacion);
        enviarEmail(notificacion);
    }

    @Transactional
    public void enviarEmail(Notificacion notificacion) {
        if (!mailEnabled) {
            log.info("""
                    ╔══════════════════════════════════════════════════════╗
                    ║  [DEV] EMAIL SIMULADO (mail.enabled=false)
                    ║  Para: {}
                    ║  Asunto: {}
                    ╠══════════════════════════════════════════════════════╣
                    {}
                    ╚══════════════════════════════════════════════════════╝
                    """,
                    notificacion.getDestinatario(),
                    notificacion.getAsunto(),
                    notificacion.getCuerpo());
            notificacion.setEstado(EstadoNotificacion.ENVIADO);
            notificacion.setEnviadoEn(LocalDateTime.now());
            notificacionRepository.save(notificacion);
            return;
        }

        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(notificacion.getDestinatario());
            mensaje.setSubject(notificacion.getAsunto());
            mensaje.setText(notificacion.getCuerpo());
            mensaje.setFrom("angel.guillen.0220@gmail.com");

            mailSender.send(mensaje);

            notificacion.setEstado(EstadoNotificacion.ENVIADO);
            notificacion.setEnviadoEn(LocalDateTime.now());
            log.info("Email enviado a: {}", notificacion.getDestinatario());
        } catch (Exception e) {
            notificacion.setIntentos(notificacion.getIntentos() + 1);
            notificacion.setErrorMsg(e.getMessage());
            if (notificacion.getIntentos() >= notificacion.getMaxIntentos()) {
                notificacion.setEstado(EstadoNotificacion.FALLIDO);
                log.error("Email fallido definitivamente para: {}", notificacion.getDestinatario());
            }
            log.warn("Error enviando email (intento {}): {}", notificacion.getIntentos(), e.getMessage());
        }
        notificacionRepository.save(notificacion);
    }

    private String renderizar(String plantilla, Map<String, Object> variables) {
        if (plantilla == null) return "";
        String resultado = plantilla;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            resultado = resultado.replace(
                    "{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return resultado;
    }

    private String traducirEstado(String estado) {
        if (estado == null) return "Desconocido";
        return switch (estado) {
            case "PENDIENTE"  -> "Pendiente de confirmación";
            case "CONFIRMADA" -> "Confirmada";
            case "CHECKIN"    -> "Check-in activo";
            case "CHECKOUT"   -> "Finalizada";
            case "CANCELADA"  -> "Cancelada";
            case "NO_SHOW"    -> "No se presentó";
            default           -> estado;
        };
    }
}
