package com.staysync.reservas.repository;

import com.staysync.reservas.model.Reserva;
import com.staysync.reservas.model.Reserva.EstadoReserva;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    Optional<Reserva> findByCodigo(String codigo);
    Page<Reserva> findByUsuarioId(Long usuarioId, Pageable pageable);
    List<Reserva> findByEstado(EstadoReserva estado);
    List<Reserva> findByEstadoAndFechaEntradaBefore(EstadoReserva estado, LocalDate fecha);

    @Query("""
        SELECT COUNT(r) > 0 FROM Reserva r
        WHERE r.habitacionId = :habitacionId
          AND r.estado IN ('PENDIENTE', 'CONFIRMADA', 'CHECKIN')
          AND r.fechaEntrada < :fechaSalida
          AND r.fechaSalida > :fechaEntrada
    """)
    boolean existeConflicto(Long habitacionId, LocalDate fechaEntrada, LocalDate fechaSalida);

    /** Sin pagar y ya sin sentido retenerlas: creadas antes del límite o con la llegada pasada. */
    @Query("""
        SELECT r FROM Reserva r
        WHERE r.estado = 'PENDIENTE'
          AND (r.createdAt < :creadaAntesDe OR r.fechaEntrada < :hoy)
    """)
    List<Reserva> findPendientesVencidas(@Param("creadaAntesDe") LocalDateTime creadaAntesDe,
                                         @Param("hoy") LocalDate hoy);

    boolean existsByCodigo(String codigo);
}
