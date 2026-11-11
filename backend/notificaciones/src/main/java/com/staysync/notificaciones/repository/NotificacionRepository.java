package com.staysync.notificaciones.repository;

import com.staysync.notificaciones.model.Notificacion;
import com.staysync.notificaciones.model.Notificacion.EstadoNotificacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByUsuarioId(Long usuarioId);
    List<Notificacion> findByEstadoAndIntentosLessThan(EstadoNotificacion estado, Integer maxIntentos);

    /** Notificaciones en el estado dado, creadas desde {@code desde}, a las que aún les quedan intentos. */
    @Query("SELECT n FROM Notificacion n WHERE n.estado = :estado AND n.intentos < n.maxIntentos "
            + "AND n.createdAt >= :desde ORDER BY n.id")
    List<Notificacion> findConIntentosRestantes(@Param("estado") EstadoNotificacion estado,
                                                @Param("desde") LocalDateTime desde);

    /** Marca FALLIDO lo que sigue PENDIENTE desde antes de {@code limite}: enviarlo ya no tiene sentido. */
    @Modifying
    @Query("UPDATE Notificacion n SET n.estado = :fallido, n.updatedAt = :ahora, "
            + "n.errorMsg = CONCAT('Caducado sin enviar. ', COALESCE(n.errorMsg, '')) "
            + "WHERE n.estado = :pendiente AND n.createdAt < :limite")
    int caducarPendientes(@Param("pendiente") EstadoNotificacion pendiente,
                          @Param("fallido") EstadoNotificacion fallido,
                          @Param("limite") LocalDateTime limite,
                          @Param("ahora") LocalDateTime ahora);

    /** Lee y bloquea la fila hasta el fin de la transacción: dos instancias no reintentan el mismo correo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Notificacion n WHERE n.id = :id")
    Optional<Notificacion> findByIdParaActualizar(@Param("id") Long id);
}
