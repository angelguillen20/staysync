package com.staysync.habitaciones.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ImagenRequest {

    @NotBlank(message = "La URL de la imagen es obligatoria")
    @Size(max = 512)
    @Pattern(regexp = "^(/media/|https://).*", message = "La imagen debe ser una subida (/media/...) o un enlace https")
    private String url;

    private boolean esPrincipal;
}
