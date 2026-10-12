package com.staysync.usuarios.model;

import com.staysync.usuarios.crypto.AesGcmStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "usuarios", indexes = {
        @Index(name = "idx_email",  columnList = "email"),
        @Index(name = "idx_rol",    columnList = "rol"),
        @Index(name = "idx_activo", columnList = "activo"),
        @Index(name = "idx_tenant", columnList = "tenant_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Hotel al que pertenece el usuario (FK lógica a hoteles.tenant_id). Nullable en el
     * esquema para que ddl-auto pueda agregar la columna a tablas con datos; DataInitializer
     * asigna el hotel por defecto a las filas antiguas al arrancar.
     */
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Convert(converter = AesGcmStringConverter.class)
    @Column(length = 255)
    private String telefono;

    // varchar y no ENUM nativo de MySQL: así agregar un rol no exige migrar la columna
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private Rol rol;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneId.systemDefault());
        updatedAt = LocalDateTime.now(ZoneId.systemDefault());
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.systemDefault());
    }

    /**
     * SUPER_ADMIN: operador de la plataforma, ve todos los hoteles y asigna el hotel de cada usuario.
     * ADMIN: administra solo su hotel.
     */
    public enum Rol {
        SUPER_ADMIN, ADMIN, RECEPCIONISTA, HUESPED;

        /** Personal del hotel: las únicas cuentas que se pueden mover de hotel. */
        public boolean esPersonal() {
            return this == ADMIN || this == RECEPCIONISTA;
        }
    }
}
