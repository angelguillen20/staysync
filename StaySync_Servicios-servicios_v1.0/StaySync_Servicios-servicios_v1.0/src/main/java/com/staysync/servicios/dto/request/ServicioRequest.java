package com.staysync.servicios.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Alta o edición de un servicio del catálogo del hotel")
public class ServicioRequest {

    @NotNull(message = "La categoría es obligatoria")
    private Long categoriaId;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String nombre;

    @Size(max = 1000, message = "La descripción no puede superar 1000 caracteres")
    private String descripcion;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0", message = "El precio no puede ser negativo")
    @Schema(example = "25000")
    private BigDecimal precio;

    private Boolean disponible = true;

    private Boolean requiereReserva = false;

    @Size(max = 500)
    @Pattern(regexp = "^(/media/|https://).*", message = "La imagen debe ser una subida (/media/...) o un enlace https")
    private String imagenUrl;
}
