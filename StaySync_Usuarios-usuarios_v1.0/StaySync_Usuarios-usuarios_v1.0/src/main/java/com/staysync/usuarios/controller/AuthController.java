package com.staysync.usuarios.controller;

import com.staysync.usuarios.dto.request.LoginRequest;
import com.staysync.usuarios.dto.request.RegistroRequest;
import com.staysync.usuarios.dto.response.AuthResponse;
import com.staysync.usuarios.dto.response.UsuarioResponse;
import com.staysync.usuarios.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Endpoints de registro, login y gestión de tokens")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/registro")
    @Operation(summary = "Registrar nuevo usuario",
               responses = {
                   @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente",
                       content = @Content(schema = @Schema(implementation = UsuarioResponse.class))),
                   @ApiResponse(responseCode = "409", description = "Email ya registrado"),
                   @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos")
               })
    public ResponseEntity<UsuarioResponse> registro(@Valid @RequestBody RegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registro(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Autenticar usuario y obtener JWT",
               responses = {
                   @ApiResponse(responseCode = "200", description = "Login exitoso",
                       content = @Content(schema = @Schema(implementation = AuthResponse.class))),
                   @ApiResponse(responseCode = "401", description = "Credenciales incorrectas")
               })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar access token con refresh token",
               responses = {
                   @ApiResponse(responseCode = "200", description = "Token renovado"),
                   @ApiResponse(responseCode = "401", description = "Refresh token inválido o expirado")
               })
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        return ResponseEntity.ok(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión e invalidar refresh token")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body) {
        authService.logout(body.get("refreshToken"));
        return ResponseEntity.noContent().build();
    }
}
