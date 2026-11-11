package com.staysync.servicios.repository;

import com.staysync.servicios.model.SolicitudServicio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolicitudServicioRepository extends JpaRepository<SolicitudServicio, Long> {
    List<SolicitudServicio> findByReservaId(Long reservaId);
    List<SolicitudServicio> findByUsuarioId(Long usuarioId);
    List<SolicitudServicio> findAllByOrderByCreatedAtDesc();
}
