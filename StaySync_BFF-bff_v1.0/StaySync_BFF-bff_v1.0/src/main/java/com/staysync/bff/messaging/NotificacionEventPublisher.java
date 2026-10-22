package com.staysync.bff.messaging;

import com.staysync.bff.config.ServicesProperties;
import com.staysync.bff.messaging.event.UsuarioRegistradoEvent;
import com.staysync.bff.security.TenantDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Aviso de bienvenida al registrarse. Las notificaciones de reservas y pagos ya no pasan
 * por aquí: las genera notificaciones-service a partir de los eventos SNS/SQS.
 * Las fallas se registran como warning y nunca se propagan: un correo caído no debe
 * romper el registro.
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

    /**
     * El registro no tiene sesión: el hotel se envía explícitamente (viene de la respuesta
     * de usuarios-service, no del cliente).
     */
    public void publishUsuarioRegistrado(Long usuarioId, String nombre, String email, String tenantId) {
        if (!enabled) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (tenantId != null) headers.set(TenantDetails.HEADER, tenantId);
            var evento = new UsuarioRegistradoEvent(usuarioId, nombre, email);
            restTemplate.postForEntity(baseUrl + "/api/v1/notificaciones/usuario-registrado",
                    new HttpEntity<>(evento, headers), Void.class);
            log.info("Notificación enviada [usuario.registro]: userId={}", usuarioId);
        } catch (Exception e) {
            log.warn("No se pudo notificar registro de usuario={}: {}", usuarioId, e.getMessage());
        }
    }
}
