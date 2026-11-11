package com.staysync.ota.repository;

import com.staysync.ota.model.CanalOta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CanalOtaRepository extends JpaRepository<CanalOta, Long> {
    List<CanalOta> findByActivoTrue();
    Optional<CanalOta> findByCodigo(String codigo);
}
