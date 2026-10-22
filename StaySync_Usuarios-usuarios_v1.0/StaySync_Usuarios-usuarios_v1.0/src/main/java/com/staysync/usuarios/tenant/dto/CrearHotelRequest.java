package com.staysync.usuarios.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * Alta de un hotel nuevo (solo SUPER_ADMIN). El slug es la URL del portal y también el
 * tenantId, que no cambia nunca: reservas, pagos y habitaciones del hotel quedan ligados a él.
 */
@Data
@Schema(description = "Hotel nuevo con su marca y su administrador inicial")
public class CrearHotelRequest {

    @NotBlank(message = "El nombre del hotel es obligatorio")
    @Size(min = 2, max = 150)
    private String nombre;

    @NotBlank(message = "El slug del portal es obligatorio")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,63}$",
             message = "El slug solo admite minúsculas, números y guiones (2 a 64 caracteres)")
    @Schema(example = "hotel-pacifico")
    private String slug;

    @Size(max = 500)
    @Pattern(regexp = "^https://[^\\s\"'<>()]+$", message = "El logo debe ser una URL https válida")
    private String logoUrl;

    /** Opcional: sin colores el portal usa los de StaySync. Mismas reglas que la marca. */
    private Map<
            @Pattern(regexp = "^(primary|secondary|background|text)$", message = "Color no reconocido") String,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Los colores deben tener el formato #RRGGBB") String>
            theme;

    @NotNull(message = "El hotel necesita un administrador inicial")
    @Valid
    private AdminInicial admin;

    @Data
    @Schema(description = "Cuenta ADMIN que gestionará el hotel")
    public static class AdminInicial {

        @NotBlank(message = "El nombre del administrador es obligatorio")
        @Size(min = 2, max = 100)
        private String nombre;

        @NotBlank(message = "El apellido del administrador es obligatorio")
        @Size(min = 2, max = 100)
        private String apellido;

        @NotBlank(message = "El email del administrador es obligatorio")
        @Email(message = "El email del administrador no es válido")
        private String email;

        @NotBlank(message = "La contraseña del administrador es obligatoria")
        @Size(min = 8, max = 100)
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).+$",
                 message = "La contraseña debe contener mayúscula, minúscula, número y carácter especial")
        private String password;
    }
}
