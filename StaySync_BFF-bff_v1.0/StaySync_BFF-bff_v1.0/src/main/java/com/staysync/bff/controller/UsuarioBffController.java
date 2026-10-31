package com.staysync.bff.controller;

import com.staysync.bff.client.UsuariosClient;
import com.staysync.bff.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/bff/usuarios")
@RequiredArgsConstructor
@Tag(name = "BFF Usuarios", description = "Gestión de usuarios del sistema")
public class UsuarioBffController {

    private static final SecureRandom AZAR = new SecureRandom();

    private final UsuariosClient usuariosClient;
    private final JwtService jwtService;

    /**
     * Huésped que llega sin cuenta (walk-in). Se registra en el portal del hotel de quien lo
     * atiende, porque recepción solo lista los huéspedes de su hotel. La contraseña temporal se
     * muestra una sola vez para entregársela; el huésped la cambia desde su perfil.
     */
    @Operation(summary = "Registrar un huésped desde recepción (walk-in)",
               description = "Body: { nombre, apellido, email, telefono? }. Devuelve { usuario, passwordTemporal }")
    @PostMapping("/huespedes")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
    public ResponseEntity<Map<String, Object>> registrarHuesped(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String password = contrasenaTemporal();
        Map<String, Object> registro = new HashMap<>();
        for (String campo : List.of("nombre", "apellido", "email", "telefono")) {
            if (body.get(campo) != null && !body.get(campo).toString().isBlank()) {
                registro.put(campo, body.get(campo).toString().trim());
            }
        }
        registro.put("password", password);
        registro.put("hotelSlug", slugDelHotel(jwtService.extractTenantId(token)));
        Object usuario = usuariosClient.reenviar(authHeader, HttpMethod.POST, "/api/v1/auth/registro", registro).getBody();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("usuario", usuario, "passwordTemporal", password));
    }

    /** Cumple la política de usuarios-service: mayúscula, minúscula, número y símbolo (12 caracteres). */
    static String contrasenaTemporal() {
        String mayus = "ABCDEFGHJKLMNPQRSTUVWXYZ", minus = "abcdefghijkmnpqrstuvwxyz", nums = "23456789", simbolos = "@$!%*?&";
        StringBuilder sb = new StringBuilder()
                .append(uno(mayus)).append(uno(minus)).append(uno(nums)).append(uno(simbolos));
        for (int i = 0; i < 8; i++) {
            sb.append(uno(mayus + minus + nums));
        }
        return sb.toString();
    }

    private static char uno(String letras) {
        return letras.charAt(AZAR.nextInt(letras.length()));
    }

    private String slugDelHotel(String tenantId) {
        if (usuariosClient.listarHotelesPublicos().getBody() instanceof List<?> hoteles) {
            for (Object h : hoteles) {
                if (h instanceof Map<?, ?> hotel && Objects.equals(hotel.get("tenantId"), tenantId)) {
                    return String.valueOf(hotel.get("slug"));
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "No se encontró el portal de tu hotel para registrar al huésped.");
    }

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
            @RequestParam(required = false, defaultValue = "") String rol,
            @RequestParam(required = false) String tenantId) {
        return usuariosClient.buscarUsuarios(authHeader, q, rol, tenantId);
    }

    @Operation(summary = "Listar todos los usuarios")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> listarUsuarios(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(required = false) String tenantId) {
        return usuariosClient.listarUsuarios(authHeader, page, size, sort, tenantId);
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

    @Operation(summary = "Mover una cuenta de personal a otro hotel (solo SUPER_ADMIN)",
               description = "Body: {\"tenantId\": \"hotel-andes\"}. Cierra las sesiones del usuario.")
    @PatchMapping("/{id}/hotel")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Object> cambiarHotel(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Object body) {
        return usuariosClient.cambiarHotel(authHeader, id, body);
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
