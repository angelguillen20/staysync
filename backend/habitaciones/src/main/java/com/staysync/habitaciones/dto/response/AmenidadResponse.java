package com.staysync.habitaciones.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AmenidadResponse {
    private Long id;
    private String nombre;
    private String descripcion;
    private String icono;
}
