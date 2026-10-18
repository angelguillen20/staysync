package com.staysync.servicios.service;

import com.staysync.servicios.exception.ServicioNotFoundException;
import com.staysync.servicios.model.TareaAseo;
import com.staysync.servicios.model.TareaAseo.EstadoAseo;
import com.staysync.servicios.repository.TareaAseoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Tareas de aseo del hotel. Las crean y actualizan los eventos de reserva; cada método es
 * idempotente para que un evento repetido no duplique tareas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AseoService {

    private final TareaAseoRepository repository;

    public List<TareaAseo> listar() {
        return repository.findAllByOrderByFechaProgramadaAsc();
    }

    /** ReservaConfirmada: programa la limpieza para el día del check-out. */
    @Transactional
    public TareaAseo programar(Long reservaId, Long habitacionId, LocalDate fechaSalida) {
        return repository.findByReservaId(reservaId).orElseGet(() -> {
            TareaAseo tarea = repository.save(TareaAseo.builder()
                    .reservaId(reservaId)
                    .habitacionId(habitacionId)
                    .fechaProgramada(fechaSalida)
                    .estado(EstadoAseo.PROGRAMADA)
                    .build());
            log.info("Aseo programado para la habitación {} el {} (reserva {})", habitacionId, fechaSalida, reservaId);
            return tarea;
        });
    }

    /** ReservaCancelada: la limpieza programada ya no hace falta. */
    @Transactional
    public void cancelar(Long reservaId) {
        repository.findByReservaId(reservaId)
                .filter(t -> t.getEstado() == EstadoAseo.PROGRAMADA)
                .ifPresent(t -> {
                    t.setEstado(EstadoAseo.CANCELADA);
                    log.info("Aseo de la reserva {} cancelado", reservaId);
                });
    }

    /** ReservaCheckout: el huésped se fue, la habitación está lista para limpiar. */
    @Transactional
    public void marcarPendiente(Long reservaId, Long habitacionId, LocalDate fechaSalida) {
        TareaAseo tarea = programar(reservaId, habitacionId, fechaSalida);
        if (tarea.getEstado() == EstadoAseo.PROGRAMADA) {
            tarea.setEstado(EstadoAseo.PENDIENTE);
            log.info("Habitación {} lista para limpiar (reserva {})", habitacionId, reservaId);
        }
    }

    @Transactional
    public TareaAseo completar(Long id) {
        TareaAseo tarea = repository.findById(id).orElseThrow(() -> new ServicioNotFoundException(id));
        tarea.setEstado(EstadoAseo.COMPLETADA);
        return tarea;
    }
}
