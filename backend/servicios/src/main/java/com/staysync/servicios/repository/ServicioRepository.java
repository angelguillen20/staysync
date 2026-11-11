package com.staysync.servicios.repository;

import com.staysync.servicios.model.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {
    List<Servicio> findByDisponibleTrue();
    List<Servicio> findByCategoriaIdAndDisponibleTrue(Long categoriaId);
}
