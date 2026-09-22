package com.staysync.ota.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "reservas_ota", indexes = {
        @Index(name = "idx_canal_id",          columnList = "canal_id"),
        @Index(name = "idx_reserva_id_local",  columnList = "reserva_id_local"),
        @Index(name = "idx_sincronizada",      columnList = "sincronizada")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ReservaOta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "canal_id", nullable = false)
    private CanalOta canal;

    @Column(name = "reserva_id_local")
    private Long reservaIdLocal;

    @Column(name = "reserva_id_ext", nullable = false, length = 100)
    private String reservaIdExt;

    @Column(name = "estado_ext", length = 50)
    private String estadoExt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_entrada", columnDefinition = "json")
    private Map<String, Object> payloadEntrada;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_salida", columnDefinition = "json")
    private Map<String, Object> payloadSalida;

    @Column(nullable = false)
    @Builder.Default
    private Boolean sincronizada = false;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
