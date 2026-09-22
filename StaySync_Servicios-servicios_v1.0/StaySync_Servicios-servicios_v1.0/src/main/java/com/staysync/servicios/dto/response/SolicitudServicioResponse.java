package com.staysync.servicios.dto.response;

import com.staysync.servicios.model.SolicitudServicio.EstadoSolicitud;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SolicitudServicioResponse {
    private Long id;
    private Long reservaId;
    private Long usuarioId;
    private Long servicioId;
    private String servicioNombre;
    private Integer cantidad;
    private LocalDateTime fechaServicio;
    private EstadoSolicitud estado;
    private String notas;
    private BigDecimal precioTotal;
    private LocalDateTime createdAt;
}
