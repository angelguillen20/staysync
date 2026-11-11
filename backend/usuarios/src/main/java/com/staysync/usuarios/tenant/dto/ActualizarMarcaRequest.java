package com.staysync.usuarios.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.Map;

/**
 * Marca editable de un hotel. Se valida estrictamente porque los colores terminan como
 * variables CSS y el logo como imagen en el portal de todos los huéspedes del hotel.
 */
@Data
@Schema(description = "Nombre, logo y colores del hotel")
public class ActualizarMarcaRequest {

    @NotBlank(message = "El nombre del hotel es obligatorio")
    @Size(min = 2, max = 150)
    private String nombre;

    /** Solo https, para no mezclar contenido inseguro ni aceptar esquemas como javascript: o data:. */
    @Size(max = 500)
    @Pattern(regexp = "^https://[^\\s\"'<>()]+$", message = "El logo debe ser una URL https válida")
    private String logoUrl;

    /** Claves permitidas: primary, secondary, background, text. Valores #RRGGBB. */
    @NotNull
    private Map<
            @Pattern(regexp = "^(primary|secondary|background|text)$", message = "Color no reconocido") String,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Los colores deben tener el formato #RRGGBB") String>
            theme;
}
