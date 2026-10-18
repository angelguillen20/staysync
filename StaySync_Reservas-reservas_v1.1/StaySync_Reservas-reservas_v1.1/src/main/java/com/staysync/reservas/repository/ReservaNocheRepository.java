package com.staysync.reservas.repository;

import com.staysync.reservas.model.ReservaNoche;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaNocheRepository extends JpaRepository<ReservaNoche, Long> {

    /** Libera las noches de una reserva cancelada para que otra pueda tomarlas. */
    @Modifying
    @Query("DELETE FROM ReservaNoche n WHERE n.reservaId = :reservaId")
    int liberarNoches(@Param("reservaId") Long reservaId);
}
