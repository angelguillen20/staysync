package com.staysync.notificaciones.service;

import com.staysync.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Reintenta los correos que fallaron al enviarse (SMTP caído, timeout, credenciales): sin esto
 * quedarían en PENDIENTE para siempre. Cada correo espera cada vez más entre intentos y, al
 * agotar max_intentos o pasar la caducidad (24 h), queda FALLIDO. Corre en modo sistema
 * (todos los hoteles) y fuera de la transacción del servicio, para que la sesión de Hibernate
 * se abra ya en ese modo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReintentoNotificacionesScheduler {

    private final NotificacionService notificacionService;

    @Scheduled(fixedDelayString = "${notificaciones.reintentos.revision-ms:60000}",
            initialDelayString = "${notificaciones.reintentos.revision-ms:60000}")
    public void reintentarPendientes() {
        TenantContext.ejecutarComoSistema(() -> {
            LocalDateTime ahora = LocalDateTime.now(ZoneId.systemDefault());
            try {
                notificacionService.caducarPendientes(ahora);
            } catch (RuntimeException e) {
                log.warn("No se pudieron caducar los emails antiguos: {}", e.getMessage());
            }
            List<Long> ids = notificacionService.pendientesDeReintento(ahora);
            if (!ids.isEmpty()) {
                log.info("Reintentando {} email(s) pendiente(s)", ids.size());
            }
            for (Long id : ids) {
                try {
                    notificacionService.reintentarEnvio(id, ahora);
                } catch (RuntimeException e) {
                    log.warn("No se pudo reintentar la notificación {}: {}", id, e.getMessage());
                }
            }
        });
    }
}
