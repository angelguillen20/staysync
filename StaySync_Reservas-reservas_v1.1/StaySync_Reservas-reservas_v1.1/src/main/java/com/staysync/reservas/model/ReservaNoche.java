package com.staysync.reservas.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.time.LocalDate;

/**
 * Anti-overbooking: una fila por cada noche que ocupa una reserva activa. La restricción
 * única (hotel, habitación, fecha) la garantiza la base de datos: si dos reservas
 * concurrentes piden la misma noche, solo una transacción logra insertarla, aunque
 * corran en instancias distintas del servicio.
 */
@Entity
@Table(name = "reserva_noches",
        uniqueConstraints = @UniqueConstraint(name = "uk_noche_hotel_habitacion_fecha",
                columnNames = {"tenant_id", "habitacion_id", "fecha"}),
        indexes = @Index(name = "idx_noche_reserva", columnList = "reserva_id"))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ReservaNoche {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "habitacion_id", nullable = false)
    private Long habitacionId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;
}
