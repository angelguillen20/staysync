package com.staysync.habitaciones.repository;

import com.staysync.habitaciones.model.Habitacion;
import com.staysync.habitaciones.model.Habitacion.EstadoHabitacion;
import org.springframework.data.jpa.repository.*;

import java.util.List;
import java.util.Optional;

public interface HabitacionRepository extends JpaRepository<Habitacion, Long> {
    Optional<Habitacion> findByNumero(String numero);
    boolean existsByNumero(String numero);
    List<Habitacion> findByEstadoAndActivaTrue(EstadoHabitacion estado);
    List<Habitacion> findByActivaTrue();
    Optional<Habitacion> findByIdAndActivaTrue(Long id);

    @Query("SELECT DISTINCT h FROM Habitacion h JOIN FETCH h.amenidades WHERE h.estado = 'DISPONIBLE' AND h.activa = true")
    List<Habitacion> findDisponiblesConAmenidades();
}
