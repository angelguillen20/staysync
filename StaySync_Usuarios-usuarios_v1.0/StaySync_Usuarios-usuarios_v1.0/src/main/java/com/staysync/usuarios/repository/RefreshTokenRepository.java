package com.staysync.usuarios.repository;

import com.staysync.usuarios.model.RefreshToken;
import com.staysync.usuarios.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUsuario(Usuario usuario);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiraEn < :now")
    void deleteAllExpiredBefore(LocalDateTime now);
}
