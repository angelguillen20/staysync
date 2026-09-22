package com.staysync.habitaciones.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "habitaciones", indexes = {
        @Index(name = "idx_estado",   columnList = "estado"),
        @Index(name = "idx_tipo_id",  columnList = "tipo_id"),
        @Index(name = "idx_numero",   columnList = "numero")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Habitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String numero;

    @Column(nullable = false)
    private Integer piso;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_id", nullable = false)
    private TipoHabitacion tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private EstadoHabitacion estado = EstadoHabitacion.DISPONIBLE;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "precio_por_noche", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioPorNoche;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "habitacion_amenidades",
            joinColumns        = @JoinColumn(name = "habitacion_id"),
            inverseJoinColumns = @JoinColumn(name = "amenidad_id"))
    @Builder.Default
    private Set<Amenidad> amenidades = new HashSet<>();

    @OneToMany(mappedBy = "habitacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ImagenHabitacion> imagenes = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum EstadoHabitacion {
        DISPONIBLE, OCUPADA, EN_LIMPIEZA, MANTENIMIENTO, FUERA_DE_SERVICIO
    }
}
