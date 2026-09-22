package com.staysync.notificaciones.repository;

import com.staysync.notificaciones.model.PlantillaNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlantillaNotificacionRepository extends JpaRepository<PlantillaNotificacion, Long> {
    Optional<PlantillaNotificacion> findByCodigo(String codigo);
}
