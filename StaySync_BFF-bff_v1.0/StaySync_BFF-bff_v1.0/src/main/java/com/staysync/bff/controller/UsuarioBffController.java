package com.staysync.bff.controller;

import com.staysync.bff.client.UsuariosClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bff/usuarios")
@RequiredArgsConstructor
@Tag(name = "BFF Usuarios", description = "Gestión de usuarios del sistema")
public class UsuarioBffController {

    private final UsuariosClient usuariosClient;

    @Operation(summary = "Listar todos los huéspedes activos (rol HUESPED) — uso de recepción")
    @GetMapping("/huespedes")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
    public ResponseEntity<Object> listarHuespedes(
            @RequestHeader("Authorization") String authHeader) {
        return usuariosClient.listarHuespedes(authHeader);
    }

    @Operation(summary = "Buscar usuarios por nombre/apellido/email con filtro de rol")
    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
    public ResponseEntity<Object> buscarUsuarios(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "") String rol) {
        return usuariosClient.buscarUsuarios(authHeader, q, rol);
    }

    @Operation(summary = "Listar todos los usuarios")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> listarUsuarios(
            @RequestHeader("Authorization") String authHeader) {
        return usuariosClient.listarUsuarios(authHeader);
    }

    @Operation(summary = "Obtener perfil del usuario autenticado (cualquier rol)")
    @GetMapping("/perfil")
    public ResponseEntity<Object> getPerfilPropio(
            @RequestHeader("Authorization") String authHeader) {
        return usuariosClient.getPerfilPropio(authHeader);
    }

    @Operation(summary = "Actualizar perfil propio del usuario autenticado (cualquier rol)")
    @PutMapping("/perfil")
    public ResponseEntity<Object> updatePerfilPropio(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Object body) {
        return usuariosClient.updatePerfilPropio(authHeader, body);
    }

    @Operation(summary = "Obtener perfil de usuario por ID")
    @GetMapping("/{id}")
    public ResponseEntity<Object> getPerfil(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        return usuariosClient.getPerfil(authHeader, id);
    }

    @Operation(summary = "Actualizar perfil de usuario")
    @PutMapping("/{id}")
    public ResponseEntity<Object> updatePerfil(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Object body) {
        return usuariosClient.updatePerfil(authHeader, id, body);
    }

    @Operation(summary = "Cambiar rol de un usuario (solo ADMIN)")
    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> cambiarRol(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Object body) {
        return usuariosClient.cambiarRol(authHeader, id, body);
    }

    @Operation(summary = "Desactivar usuario (soft delete)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> desactivar(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        usuariosClient.desactivar(authHeader, id);
        return ResponseEntity.noContent().build();
    }
}
