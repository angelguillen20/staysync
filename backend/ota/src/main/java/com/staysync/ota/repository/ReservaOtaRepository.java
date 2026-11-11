package com.staysync.ota.repository;

import com.staysync.ota.model.ReservaOta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservaOtaRepository extends JpaRepository<ReservaOta, Long> {
    Optional<ReservaOta> findByCanalIdAndReservaIdExt(Long canalId, String reservaIdExt);
    List<ReservaOta> findBySincronizadaFalse();
}
