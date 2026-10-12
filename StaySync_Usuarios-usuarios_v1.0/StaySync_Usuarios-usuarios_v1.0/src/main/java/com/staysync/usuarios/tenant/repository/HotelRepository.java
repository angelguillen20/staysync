package com.staysync.usuarios.tenant.repository;

import com.staysync.usuarios.tenant.model.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HotelRepository extends JpaRepository<Hotel, String> {
    Optional<Hotel> findBySlugAndActivoTrue(String slug);
    Optional<Hotel> findByTenantIdAndActivoTrue(String tenantId);
    List<Hotel> findAllByActivoTrueOrderByNombreAsc();
}
