package com.staysync.usuarios.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Credenciales de acceso al sistema")
public class LoginRequest {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    @Schema(description = "Email del usuario", example = "admin@staysync.com")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Schema(description = "Contraseña del usuario")
    private String password;

    @Schema(description = "Portal desde el que entra (slug). Un huésped queda en ese hotel; el personal, siempre en el suyo",
            example = "hotel-andes")
    private String hotelSlug;
}
