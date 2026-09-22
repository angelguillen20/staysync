package com.staysync.servicios.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "solicitudes_servicio", indexes = {
        @Index(name = "idx_reserva_id",  columnList = "reserva_id"),
        @Index(name = "idx_usuario_id",  columnList = "usuario_id"),
        @Index(name = "idx_estado",      columnList = "estado"),
        @Index(name = "idx_fecha_servicio", columnList = "fecha_servicio")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SolicitudServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "servicio_id", nullable = false)
    private Servicio servicio;

    @Column(nullable = false)
    @Builder.Default
    private Integer cantidad = 1;

    @Column(name = "fecha_servicio", nullable = false)
    private LocalDateTime fechaServicio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(name = "precio_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioTotal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum EstadoSolicitud {
        PENDIENTE, EN_PROCESO, COMPLETADO, CANCELADO
    }
}
