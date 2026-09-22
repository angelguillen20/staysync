package com.staysync.servicios.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ServicioResponse {
    private Long id;
    private Long categoriaId;
    private String categoriaNombre;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private Boolean disponible;
    private Boolean requiereReserva;
    private LocalDateTime createdAt;
}
