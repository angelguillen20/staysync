package com.staysync.tenant.jpa;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;

/** Entidad mínima de prueba con filtrado por hotel. */
@Entity
@Table(name = "cuartos")
public class Cuarto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @TenantId
    @Column(name = "tenant_id", length = 64)
    String tenantId;

    String numero;

    public Cuarto() {
    }

    Cuarto(String numero) {
        this.numero = numero;
    }
}
