package com.staysync.reservas.service;

import com.staysync.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Auto check-in de todos los hoteles. Corre en modo sistema (sin filtro de hotel) y fuera
 * de la transacción del servicio, para que la sesión de Hibernate se abra ya en ese modo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservaScheduler {

    private final ReservaService reservaService;

    /** Todos los días a medianoche. */
    @Scheduled(cron = "0 0 0 * * *")
    public void autoCheckinDiario() {
        TenantContext.ejecutarComoSistema(reservaService::autoTransicionarACheckin);
    }

    /**
     * Ejecuta la auto-transición al inicio para cubrir cualquier reserva
     * que debió pasar a CHECKIN mientras el servicio estaba caído.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ejecutarAlArranque() {
        log.info("Ejecutando auto-checkin al arranque del servicio...");
        TenantContext.ejecutarComoSistema(reservaService::autoTransicionarACheckin);
    }
}
