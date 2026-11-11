package com.staysync.habitaciones.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TipoHabitacionResponse {
    private Long id;
    private String nombre;
    private String descripcion;
    private Integer capacidad;
    private BigDecimal precioBase;
}
