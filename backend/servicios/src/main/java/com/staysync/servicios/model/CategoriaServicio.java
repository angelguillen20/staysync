package com.staysync.servicios.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categorias_servicio")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CategoriaServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = true;
}
