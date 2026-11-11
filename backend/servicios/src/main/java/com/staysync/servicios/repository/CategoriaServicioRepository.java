package com.staysync.servicios.repository;

import com.staysync.servicios.model.CategoriaServicio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaServicioRepository extends JpaRepository<CategoriaServicio, Long> {
    List<CategoriaServicio> findByActivaTrueOrderByNombre();
}
