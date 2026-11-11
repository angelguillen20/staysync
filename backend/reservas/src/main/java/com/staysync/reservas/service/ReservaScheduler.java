package com.staysync.reservas.service;

import com.staysync.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cierra no-shows y reservas sin pagar de todos los hoteles. Corre en modo sistema (sin filtro
 * de hotel) y fuera de la transacción del servicio, para que la sesión de Hibernate se abra ya
 * en ese modo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservaScheduler {

    private final ReservaService reservaService;

    /** Cada hora: la retención de 24 h sin pago no espera a la medianoche. */
    @Scheduled(cron = "0 0 * * * *")
    public void cerrarVencidas() {
        TenantContext.ejecutarComoSistema(reservaService::cerrarReservasVencidas);
    }

    /** Al arrancar, cubre lo que venció mientras el servicio estaba caído. */
    @EventListener(ApplicationReadyEvent.class)
    public void ejecutarAlArranque() {
        log.info("Cerrando reservas vencidas al arranque del servicio...");
        TenantContext.ejecutarComoSistema(reservaService::cerrarReservasVencidas);
    }
}
