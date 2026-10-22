package com.staysync.usuarios.repository;

import com.staysync.usuarios.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<Usuario> findByIdAndActivoTrue(Long id);
    Page<Usuario> findByTenantId(String tenantId, Pageable pageable);
    Page<Usuario> findByTenantIdAndRolNot(String tenantId, Usuario.Rol rol, Pageable pageable);

    /** Migración multi-tenant: asigna un hotel a los usuarios creados antes de existir tenant_id. */
    @Transactional
    @Modifying
    @Query("UPDATE Usuario u SET u.tenantId = :tenantId WHERE u.tenantId IS NULL OR u.tenantId = ''")
    int asignarTenantASinHotel(@Param("tenantId") String tenantId);
}
