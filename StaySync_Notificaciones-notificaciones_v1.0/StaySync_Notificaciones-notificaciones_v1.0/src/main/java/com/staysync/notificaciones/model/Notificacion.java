package com.staysync.notificaciones.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notificaciones", indexes = {
        @Index(name = "idx_usuario_id", columnList = "usuario_id"),
        @Index(name = "idx_estado",     columnList = "estado"),
        @Index(name = "idx_canal",      columnList = "canal"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plantilla_id")
    private PlantillaNotificacion plantilla;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Canal canal;

    @Column(nullable = false, length = 255)
    private String destinatario;

    @Column(length = 255)
    private String asunto;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String cuerpo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private EstadoNotificacion estado = EstadoNotificacion.PENDIENTE;

    @Column(nullable = false)
    @Builder.Default
    private Integer intentos = 0;

    @Column(name = "max_intentos", nullable = false)
    @Builder.Default
    private Integer maxIntentos = 3;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "enviado_en")
    private LocalDateTime enviadoEn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum Canal { EMAIL, SMS, PUSH }
    public enum EstadoNotificacion { PENDIENTE, ENVIADO, FALLIDO, CANCELADO }
}
