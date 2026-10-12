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
import java.util.Map;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Usuarios", description = "Gestión de usuarios del sistema hotelero")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/huespedes")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
    @Operation(summary = "Listar los huéspedes activos del hotel (uso de recepción)")
    public ResponseEntity<List<UsuarioResponse>> listarHuespedes(Authentication auth) {
        return ResponseEntity.ok(usuarioService.listarHuespedes(auth.getName()));
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
    @Operation(summary = "Buscar usuarios del hotel por nombre, apellido o email; filtrar por rol")
    public ResponseEntity<List<UsuarioResponse>> buscar(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "") String rol,
            Authentication auth) {
        return ResponseEntity.ok(usuarioService.buscar(q, rol, auth.getName()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar usuarios (ADMIN: los de su hotel; SUPER_ADMIN: todos)")
    public ResponseEntity<Page<UsuarioResponse>> listar(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            Authentication auth) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sort));
        return ResponseEntity.ok(usuarioService.listarTodos(pageable, auth.getName()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
    @Operation(summary = "Obtener usuario por ID",
               responses = {
                   @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = UsuarioResponse.class))),
                   @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
               })
    public ResponseEntity<UsuarioResponse> obtenerPorId(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(usuarioService.obtenerPorId(id, auth.getName()));
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
                                                       @Valid @RequestBody ActualizarUsuarioRequest request,
                                                       Authentication auth) {
        return ResponseEntity.ok(usuarioService.actualizar(id, request, auth.getName()));
    }

    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cambiar rol de un usuario (solo ADMIN)",
               description = "Body: {\"rol\": \"ADMIN\" | \"RECEPCIONISTA\" | \"HUESPED\"}. Se aplica en el siguiente login.")
    public ResponseEntity<UsuarioResponse> cambiarRol(@PathVariable Long id,
                                                       @RequestBody Map<String, String> body,
                                                       Authentication auth) {
        return ResponseEntity.ok(usuarioService.cambiarRol(id, body.get("rol"), auth.getName()));
    }

    @PatchMapping("/{id}/hotel")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Mover una cuenta de personal a otro hotel (solo SUPER_ADMIN)",
               description = "Body: {\"tenantId\": \"hotel-andes\"}. Cierra las sesiones del usuario; "
                       + "el nuevo hotel se aplica en su siguiente login.")
    public ResponseEntity<UsuarioResponse> cambiarHotel(@PathVariable Long id,
                                                         @RequestBody Map<String, String> body,
                                                         Authentication auth) {
        return ResponseEntity.ok(usuarioService.cambiarHotel(id, body.get("tenantId"), auth.getName()));
    }

    @PutMapping("/perfil")
    @Operation(summary = "Actualizar perfil propio")
    public ResponseEntity<UsuarioResponse> actualizarPerfil(Authentication auth,
                                                              @Valid @RequestBody ActualizarUsuarioRequest request) {
        UsuarioResponse usuario = usuarioService.obtenerPorEmail(auth.getName());
        return ResponseEntity.ok(usuarioService.actualizar(usuario.getId(), request, auth.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar usuario (soft delete, solo ADMIN)",
               responses = {
                   @ApiResponse(responseCode = "204", description = "Usuario desactivado"),
                   @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
               })
    public ResponseEntity<Void> desactivar(@PathVariable Long id, Authentication auth) {
        usuarioService.desactivar(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
