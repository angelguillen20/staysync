package com.staysync.notificaciones.repository;

import com.staysync.notificaciones.model.Notificacion;
import com.staysync.notificaciones.model.Notificacion.EstadoNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByUsuarioId(Long usuarioId);
    List<Notificacion> findByEstadoAndIntentosLessThan(EstadoNotificacion estado, Integer maxIntentos);
}
