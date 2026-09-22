package com.staysync.habitaciones.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "amenidades")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Amenidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

    @Column(length = 100)
    private String icono;
}
