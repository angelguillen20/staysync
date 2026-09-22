package com.staysync.bff.messaging;

import com.staysync.bff.config.ServicesProperties;
import com.staysync.bff.messaging.event.ReservaCreadaEvent;
import com.staysync.bff.messaging.event.ReservaEstadoCambiadoEvent;
import com.staysync.bff.messaging.event.UsuarioRegistradoEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Llama directamente a notificaciones-service vía RestTemplate para disparar el envío de
 * emails. Las fallas se registran como warning y nunca se propagan: una notificación caída
 * no debe romper el flujo principal (login, registro, creación/cambio de estado de reserva).
 */
@Component
@Slf4j
public class NotificacionEventPublisher {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Value("${notificaciones.enabled:true}")
    private boolean enabled;

    public NotificacionEventPublisher(RestTemplate restTemplate, ServicesProperties services) {
        this.restTemplate = restTemplate;
        this.baseUrl = services.notificaciones().url();
    }

    public void publishUsuarioRegistrado(Long usuarioId, String nombre, String email) {
        if (!enabled) return;
        try {
            var evento = new UsuarioRegistradoEvent(usuarioId, nombre, email);
            restTemplate.postForEntity(baseUrl + "/api/v1/notificaciones/usuario-registrado", evento, Void.class);
            log.info("Notificación enviada [usuario.registro]: userId={}", usuarioId);
        } catch (Exception e) {
            log.warn("No se pudo notificar registro de usuario={}: {}", usuarioId, e.getMessage());
        }
    }

    public void publishReservaCreada(Long reservaId, Long usuarioId, String email,
                                     String nombreUsuario, String codigo, String habitacion,
                                     String fechaEntrada, String fechaSalida, Double precioTotal) {
        if (!enabled) return;
        try {
            var evento = new ReservaCreadaEvent(
                    reservaId, usuarioId, email, nombreUsuario,
                    codigo, habitacion, fechaEntrada, fechaSalida, precioTotal);
            restTemplate.postForEntity(baseUrl + "/api/v1/notificaciones/reserva-creada", evento, Void.class);
            log.info("Notificación enviada [reserva.creada]: reservaId={}", reservaId);
        } catch (Exception e) {
            log.warn("No se pudo notificar reserva creada={}: {}", reservaId, e.getMessage());
        }
    }

    public void publishReservaEstadoCambiado(Long reservaId, Long usuarioId, String email,
                                             String nombreUsuario, String codigo,
                                             String habitacion, String estadoNuevo) {
        if (!enabled) return;
        try {
            var evento = new ReservaEstadoCambiadoEvent(
                    reservaId, usuarioId, email, nombreUsuario, codigo, habitacion, estadoNuevo);
            restTemplate.postForEntity(baseUrl + "/api/v1/notificaciones/reserva-estado", evento, Void.class);
            log.info("Notificación enviada [reserva.estado]: reservaId={} estado={}", reservaId, estadoNuevo);
        } catch (Exception e) {
            log.warn("No se pudo notificar estado de reserva={}: {}", reservaId, e.getMessage());
        }
    }
}
