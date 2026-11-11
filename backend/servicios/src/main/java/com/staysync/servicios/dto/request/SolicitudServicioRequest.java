package com.staysync.servicios.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Datos para solicitar un servicio adicional")
public class SolicitudServicioRequest {

    @NotNull(message = "El ID de reserva es obligatorio")
    private Long reservaId;

    @NotNull(message = "El ID de usuario es obligatorio")
    private Long usuarioId;

    @NotNull(message = "El ID de servicio es obligatorio")
    private Long servicioId;

    @Min(value = 1, message = "La cantidad mínima es 1")
    @Max(value = 10, message = "La cantidad máxima es 10")
    private Integer cantidad = 1;

    @NotNull(message = "La fecha del servicio es obligatoria")
    @Future(message = "La fecha del servicio debe ser futura")
    private LocalDateTime fechaServicio;

    @Size(max = 500, message = "Las notas no pueden superar 500 caracteres")
    private String notas;
}
