package com.staysync.habitaciones.repository;

import com.staysync.habitaciones.model.Amenidad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmenidadRepository extends JpaRepository<Amenidad, Long> {
    boolean existsByNombre(String nombre);
}
