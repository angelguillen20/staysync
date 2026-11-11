package com.staysync.notificaciones.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

/**
 * Copia local del contacto de cada reserva, tomada de los eventos de Reservas. Los eventos
 * de pago solo traen el reservaId: con esta tabla Notificaciones sabe a quién escribir sin
 * llamar a otro servicio.
 */
@Entity
@Table(name = "contactos_reserva",
        uniqueConstraints = @UniqueConstraint(name = "uk_contacto_hotel_reserva", columnNames = {"tenant_id", "reserva_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ContactoReserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(length = 20)
    private String codigo;

    @Column(length = 255)
    private String email;

    @Column(length = 200)
    private String nombre;
}
