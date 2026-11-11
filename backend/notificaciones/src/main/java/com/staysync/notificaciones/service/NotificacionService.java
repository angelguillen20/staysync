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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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

    /** Dirección "De" de los correos (MAIL_FROM). Debe estar verificada en el proveedor SMTP. */
    @Value("${notificaciones.mail.from:}")
    private String remitente;

    /** Espera antes del primer reintento; se duplica en cada fallo (5, 10, 20 min...). */
    @Value("${notificaciones.reintentos.espera-base:5m}")
    private Duration esperaBase;

    /** Un correo que lleva más que esto sin enviarse ya no se envía: queda FALLIDO. */
    @Value("${notificaciones.reintentos.caducidad:24h}")
    private Duration caducidad;

    /** Sin remitente el SMTP rechaza todos los correos: mejor fallar al arrancar que en cada envío. */
    @PostConstruct
    void validarConfiguracion() {
        if (mailEnabled && (remitente == null || remitente.isBlank())) {
            throw new IllegalStateException("Falta el remitente de los correos: define MAIL_FROM "
                    + "(o notificaciones.mail.from), o desactiva el envío con MAIL_ENABLED=false");
        }
    }

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
            notificacion.setEnviadoEn(LocalDateTime.now(ZoneId.systemDefault()));
            notificacionRepository.save(notificacion);
            return;
        }

        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(notificacion.getDestinatario());
            mensaje.setSubject(notificacion.getAsunto());
            mensaje.setText(notificacion.getCuerpo());
            mensaje.setFrom(remitente);

            mailSender.send(mensaje);

            notificacion.setEstado(EstadoNotificacion.ENVIADO);
            notificacion.setEnviadoEn(LocalDateTime.now(ZoneId.systemDefault()));
            log.info("Email enviado a: {}", notificacion.getDestinatario());
        } catch (Exception e) {
            notificacion.setIntentos(notificacion.getIntentos() + 1);
            String error = String.valueOf(e.getMessage());
            notificacion.setErrorMsg(error.length() > 2000 ? error.substring(0, 2000) : error);
            if (notificacion.getIntentos() >= notificacion.getMaxIntentos()) {
                notificacion.setEstado(EstadoNotificacion.FALLIDO);
                log.error("Email fallido definitivamente para: {}", notificacion.getDestinatario());
            }
            log.warn("Error enviando email (intento {}): {}", notificacion.getIntentos(), e.getMessage());
        }
        notificacionRepository.save(notificacion);
    }

    /**
     * Correos PENDIENTE con intentos restantes cuya espera ya venció. Llamar en modo sistema
     * (TenantContext.ejecutarComoSistema) para ver los de todos los hoteles.
     */
    public List<Long> pendientesDeReintento(LocalDateTime ahora) {
        return notificacionRepository.findConIntentosRestantes(EstadoNotificacion.PENDIENTE, ahora.minus(caducidad))
                .stream()
                .filter(n -> !ahora.isBefore(proximoIntento(n)))
                .map(Notificacion::getId)
                .toList();
    }

    /**
     * Marca FALLIDO los correos PENDIENTE más antiguos que la caducidad (p. ej. los que
     * quedaron atascados antes de que existieran los reintentos). Llamar en modo sistema.
     */
    @Transactional
    public int caducarPendientes(LocalDateTime ahora) {
        int caducados = notificacionRepository.caducarPendientes(
                EstadoNotificacion.PENDIENTE, EstadoNotificacion.FALLIDO, ahora.minus(caducidad), ahora);
        if (caducados > 0) {
            log.warn("{} email(s) pendiente(s) desde hace más de {} h marcados FALLIDO sin enviar",
                    caducados, caducidad.toHours());
        }
        return caducados;
    }

    /**
     * Reintenta un correo. Bloquea la fila y vuelve a comprobar que siga pendiente y vencido:
     * si otra instancia ya lo reintentó, no se envía dos veces.
     */
    @Transactional
    public void reintentarEnvio(Long id, LocalDateTime ahora) {
        notificacionRepository.findByIdParaActualizar(id)
                .filter(n -> n.getEstado() == EstadoNotificacion.PENDIENTE
                        && n.getIntentos() < n.getMaxIntentos()
                        && !ahora.isBefore(proximoIntento(n)))
                .ifPresent(n -> {
                    log.info("Reintentando email {} a {} (intento {} de {})",
                            n.getId(), n.getDestinatario(), n.getIntentos() + 1, n.getMaxIntentos());
                    enviarEmail(n);
                });
    }

    /** Momento del siguiente intento: el último fallo (updatedAt) más esperaBase × 2^(intentos-1). */
    LocalDateTime proximoIntento(Notificacion n) {
        if (n.getIntentos() == 0) {
            return n.getUpdatedAt();
        }
        return n.getUpdatedAt().plus(esperaBase.multipliedBy(1L << Math.min(n.getIntentos() - 1, 10)));
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
