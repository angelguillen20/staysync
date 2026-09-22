package com.staysync.usuarios.controller;

import com.staysync.usuarios.dto.request.ActualizarUsuarioRequest;
import com.staysync.usuarios.dto.response.UsuarioResponse;
import com.staysync.usuarios.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Usuarios", description = "Gestión de usuarios del sistema hotelero")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/huespedes")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
    @Operation(summary = "Listar todos los huéspedes activos (uso de recepción)")
    public ResponseEntity<List<UsuarioResponse>> listarHuespedes() {
        return ResponseEntity.ok(usuarioService.listarHuespedes());
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
    @Operation(summary = "Buscar usuarios por nombre, apellido o email; filtrar por rol")
    public ResponseEntity<List<UsuarioResponse>> buscar(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "") String rol) {
        return ResponseEntity.ok(usuarioService.buscar(q, rol));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar todos los usuarios (solo ADMIN)")
    public ResponseEntity<Page<UsuarioResponse>> listar(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sort));
        return ResponseEntity.ok(usuarioService.listarTodos(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
    @Operation(summary = "Obtener usuario por ID",
               responses = {
                   @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = UsuarioResponse.class))),
                   @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
               })
    public ResponseEntity<UsuarioResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtenerPorId(id));
    }

    @GetMapping("/perfil")
    @Operation(summary = "Obtener perfil del usuario autenticado")
    public ResponseEntity<UsuarioResponse> perfil(Authentication auth) {
        return ResponseEntity.ok(usuarioService.obtenerPorEmail(auth.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar usuario (solo ADMIN)")
    public ResponseEntity<UsuarioResponse> actualizar(@PathVariable Long id,
                                                       @Valid @RequestBody ActualizarUsuarioRequest request) {
        return ResponseEntity.ok(usuarioService.actualizar(id, request));
    }

    @PutMapping("/perfil")
    @Operation(summary = "Actualizar perfil propio")
    public ResponseEntity<UsuarioResponse> actualizarPerfil(Authentication auth,
                                                              @Valid @RequestBody ActualizarUsuarioRequest request) {
        UsuarioResponse usuario = usuarioService.obtenerPorEmail(auth.getName());
        return ResponseEntity.ok(usuarioService.actualizar(usuario.getId(), request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar usuario (soft delete, solo ADMIN)",
               responses = {
                   @ApiResponse(responseCode = "204", description = "Usuario desactivado"),
                   @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
               })
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        usuarioService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
