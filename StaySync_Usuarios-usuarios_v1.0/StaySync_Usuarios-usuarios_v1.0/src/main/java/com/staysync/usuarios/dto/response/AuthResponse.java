package com.staysync.usuarios.dto.response;

import com.staysync.usuarios.model.Usuario.Rol;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Respuesta de autenticación con tokens JWT")
public class AuthResponse {

    @Schema(description = "Token JWT de acceso")
    private String accessToken;

    @Schema(description = "Token de refresco")
    private String refreshToken;

    @Schema(description = "Tipo de token", example = "Bearer")
    @Builder.Default
    private String tokenType = "Bearer";

    @Schema(description = "Tiempo de expiración en milisegundos")
    private long expiresIn;

    @Schema(description = "ID del usuario autenticado")
    private Long userId;

    @Schema(description = "Nombre completo del usuario")
    private String nombreCompleto;

    @Schema(description = "Email del usuario")
    private String email;

    @Schema(description = "Rol del usuario")
    private Rol rol;
}
