package com.staysync.servicios.repository;

import com.staysync.servicios.model.TareaAseo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TareaAseoRepository extends JpaRepository<TareaAseo, Long> {
    Optional<TareaAseo> findByReservaId(Long reservaId);
    List<TareaAseo> findAllByOrderByFechaProgramadaAsc();
}
