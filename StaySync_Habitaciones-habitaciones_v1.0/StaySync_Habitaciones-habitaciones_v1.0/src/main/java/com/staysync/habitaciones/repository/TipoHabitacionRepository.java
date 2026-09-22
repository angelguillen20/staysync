package com.staysync.habitaciones.repository;

import com.staysync.habitaciones.model.TipoHabitacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoHabitacionRepository extends JpaRepository<TipoHabitacion, Long> {
    boolean existsByNombre(String nombre);
    List<TipoHabitacion> findByActivoTrue();
}
