package com.staysync.bff.controller;

import com.staysync.bff.client.CognitoClient;
import com.staysync.bff.client.UsuariosClient;
import com.staysync.bff.messaging.NotificacionEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/bff/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "BFF Auth", description = "Autenticación — proxy directo a usuarios-service")
public class AuthBffController {

    private final UsuariosClient             usuariosClient;
    private final NotificacionEventPublisher notificacionPublisher;
    private final CognitoClient              cognitoClient;

    @Operation(summary = "Login de usuario")
    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody Object body) {
        return usuariosClient.login(body);
    }

    @Operation(summary = "Registro de nuevo usuario")
    @PostMapping("/registro")
    public ResponseEntity<Object> registro(@RequestBody Object body) {
        ResponseEntity<Object> response = usuariosClient.registro(body);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> usuario = (Map<String, Object>) response.getBody();
                Long   usuarioId = toLong(usuario.get("id"));
                String nombre    = String.valueOf(usuario.getOrDefault("nombre", ""));
                String email     = String.valueOf(usuario.getOrDefault("email", ""));
                if (!email.isBlank()) {
                    notificacionPublisher.publishUsuarioRegistrado(usuarioId, nombre, email);
                }
            } catch (Exception e) {
                log.warn("No se pudo publicar notificación de registro: {}", e.getMessage());
            }
        }

        return response;
    }

    @Operation(summary = "Login vía Google (Cognito Hosted UI + PKCE)",
               description = "Intercambia el authorization code de Cognito por un JWT propio de StaySync")
    @PostMapping("/google")
    public ResponseEntity<Object> loginConGoogle(@RequestBody Map<String, String> body) {
        Map<String, Object> claims = cognitoClient.obtenerClaimsUsuario(
                body.get("code"), body.get("codeVerifier"), body.get("redirectUri"));

        String email     = String.valueOf(claims.getOrDefault("email", ""));
        String nombre    = String.valueOf(claims.getOrDefault("given_name", ""));
        String apellido  = String.valueOf(claims.getOrDefault("family_name", ""));

        return usuariosClient.loginConGoogle(Map.of(
                "email", email,
                "nombre", nombre,
                "apellido", apellido
        ));
    }

    @Operation(summary = "Renovar access token usando refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<Object> refresh(@RequestBody Object body) {
        return usuariosClient.refresh(body);
    }

    @Operation(summary = "Cerrar sesión")
    @PostMapping("/logout")
    public ResponseEntity<Object> logout(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Object body) {
        return usuariosClient.logout(authHeader, body);
    }

    private Long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        return null;
    }
}
