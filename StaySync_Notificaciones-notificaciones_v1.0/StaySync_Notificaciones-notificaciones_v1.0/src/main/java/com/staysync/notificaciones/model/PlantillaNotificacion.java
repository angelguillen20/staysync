package com.staysync.notificaciones.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "plantillas_notificacion", indexes = {
        @Index(name = "idx_codigo", columnList = "codigo"),
        @Index(name = "idx_canal",  columnList = "canal")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PlantillaNotificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Notificacion.Canal canal;

    @Column(length = 255)
    private String asunto;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String cuerpo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
