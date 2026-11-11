package com.staysync.servicios.model;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "servicios", indexes = {
        @Index(name = "idx_categoria_id", columnList = "categoria_id"),
        @Index(name = "idx_disponible",   columnList = "disponible")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Hotel dueño del registro: Hibernate lo asigna al guardar y filtra por él en cada consulta. */
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaServicio categoria;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false)
    @Builder.Default
    private Boolean disponible = true;

    @Column(name = "requiere_reserva", nullable = false)
    @Builder.Default
    private Boolean requiereReserva = false;

    /** Foto del servicio: "/media/..." (subida al bucket) o un enlace https. */
    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(ZoneId.systemDefault()); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(ZoneId.systemDefault()); }
}
