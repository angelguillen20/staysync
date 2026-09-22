package com.staysync.usuarios.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "Datos para actualización de usuario")
public class ActualizarUsuarioRequest {

    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    private String nombre;

    @Size(min = 2, max = 100, message = "El apellido debe tener entre 2 y 100 caracteres")
    private String apellido;

    @Pattern(regexp = "^[+]?[0-9]{7,15}$", message = "El teléfono no tiene un formato válido")
    private String telefono;

    @Size(min = 8, max = 100)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).+$",
             message = "La contraseña debe contener al menos una mayúscula, minúscula, número y carácter especial")
    @Schema(description = "Nueva contraseña (opcional)")
    private String nuevaPassword;
}
