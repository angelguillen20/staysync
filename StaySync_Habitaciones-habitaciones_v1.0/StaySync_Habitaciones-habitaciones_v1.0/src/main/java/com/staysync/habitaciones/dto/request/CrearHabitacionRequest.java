package com.staysync.habitaciones.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

@Data
@Schema(description = "Datos para crear una habitación")
public class CrearHabitacionRequest {

    @NotBlank(message = "El número de habitación es obligatorio")
    @Size(max = 10, message = "El número no puede superar 10 caracteres")
    @Schema(example = "101")
    private String numero;

    @NotNull(message = "El piso es obligatorio")
    @Min(value = 1, message = "El piso debe ser al menos 1")
    @Schema(example = "1")
    private Integer piso;

    @NotNull(message = "El tipo de habitación es obligatorio")
    @Schema(example = "1")
    private Long tipoId;

    @Size(max = 1000, message = "La descripción no puede superar 1000 caracteres")
    private String descripcion;

    @NotNull(message = "El precio por noche es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    @Schema(example = "120.00")
    private BigDecimal precioPorNoche;

    @Schema(description = "IDs de amenidades a asociar")
    private Set<Long> amenidadIds;
}
