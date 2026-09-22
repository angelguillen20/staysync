package com.staysync.usuarios.dto.request;

import com.staysync.usuarios.model.Usuario.Rol;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "Datos para registro de nuevo usuario")
public class RegistroRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    @Schema(example = "Juan")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(min = 2, max = 100, message = "El apellido debe tener entre 2 y 100 caracteres")
    @Schema(example = "García")
    private String apellido;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    @Schema(example = "juan.garcia@email.com")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).+$",
             message = "La contraseña debe contener al menos una mayúscula, minúscula, número y carácter especial")
    @Schema(example = "MiPass@123")
    private String password;

    @Pattern(regexp = "^[+]?[0-9]{7,15}$", message = "El teléfono no tiene un formato válido")
    @Schema(example = "+573001234567")
    private String telefono;

    @Schema(example = "HUESPED", defaultValue = "HUESPED")
    private Rol rol = Rol.HUESPED;
}
