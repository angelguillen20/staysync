package com.staysync.servicios.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Limpieza de una habitación al terminar una estadía. Se crea sola con los eventos de
 * reserva: PROGRAMADA al confirmarse, PENDIENTE al hacer check-out (lista para limpiar),
 * CANCELADA si la reserva se cancela. El personal la marca COMPLETADA.
 */
@Entity
@Table(name = "tareas_aseo",
        uniqueConstraints = @UniqueConstraint(name = "uk_aseo_hotel_reserva", columnNames = {"tenant_id", "reserva_id"}),
        indexes = {
                @Index(name = "idx_aseo_fecha", columnList = "fecha_programada"),
                @Index(name = "idx_aseo_estado", columnList = "estado")
        })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TareaAseo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Column(name = "habitacion_id", nullable = false)
    private Long habitacionId;

    /** Día del check-out previsto: cuando la habitación queda libre para limpiarse. */
    @Column(name = "fecha_programada", nullable = false)
    private LocalDate fechaProgramada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private EstadoAseo estado = EstadoAseo.PROGRAMADA;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(ZoneId.systemDefault()); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(ZoneId.systemDefault()); }

    public enum EstadoAseo {
        PROGRAMADA, PENDIENTE, COMPLETADA, CANCELADA
    }
}
