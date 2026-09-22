package com.staysync.habitaciones.dto.response;

import com.staysync.habitaciones.model.Habitacion.EstadoHabitacion;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class HabitacionResponse {
    private Long id;
    private String numero;
    private Integer piso;
    private String tipoNombre;
    private Integer capacidad;
    private EstadoHabitacion estado;
    private String descripcion;
    private BigDecimal precioPorNoche;
    private Boolean activa;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
