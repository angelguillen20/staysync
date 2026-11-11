package com.staysync.notificaciones.repository;

import com.staysync.notificaciones.model.ContactoReserva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContactoReservaRepository extends JpaRepository<ContactoReserva, Long> {
    Optional<ContactoReserva> findByReservaId(Long reservaId);
}
