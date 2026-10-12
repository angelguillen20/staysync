package com.staysync.usuarios.tenant.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Hotel (tenant) de la plataforma. Cada usuario pertenece a un hotel y el tenantId
 * viaja dentro del JWT para que el resto de servicios filtre sus datos por hotel.
 * El slug es el identificador público que aparece en la URL del portal (/h/{slug}).
 */
@Entity
@Table(name = "hoteles", indexes = {
        @Index(name = "idx_hotel_slug", columnList = "slug", unique = true)
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Hotel {

    @Id
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /** Tokens de diseño en JSON, p. ej. {"primary":"#1F4E79","secondary":"#F2A900"}. */
    @Column(name = "theme", columnDefinition = "TEXT")
    private String theme;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneId.systemDefault());
        updatedAt = LocalDateTime.now(ZoneId.systemDefault());
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.systemDefault());
    }
}
