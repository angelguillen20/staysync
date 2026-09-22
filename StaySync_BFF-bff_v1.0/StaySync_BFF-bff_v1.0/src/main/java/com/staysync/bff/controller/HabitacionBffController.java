package com.staysync.bff.controller;

import com.staysync.bff.client.HabitacionesClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bff/habitaciones")
@RequiredArgsConstructor
@Tag(name = "BFF Habitaciones", description = "Gestión del inventario de habitaciones")
public class HabitacionBffController {

    private final HabitacionesClient habitacionesClient;

    @Operation(summary = "Listar habitaciones disponibles con filtros opcionales")
    @GetMapping("/disponibles")
    public ResponseEntity<Object> listarDisponibles(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Integer capacidad,
            @RequestParam(required = false) String amenidad,
            @RequestParam(defaultValue = "precio_asc") String sort) {
        if (capacidad == null && (amenidad == null || amenidad.isBlank())) {
            return habitacionesClient.listarDisponibles(authHeader);
        }
        return habitacionesClient.buscarDisponibles(authHeader, capacidad, amenidad, sort);
    }

    @Operation(summary = "Listar todas las habitaciones")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public ResponseEntity<Object> listarTodas(
            @RequestHeader("Authorization") String authHeader) {
        return habitacionesClient.listarTodas(authHeader);
    }

    @Operation(summary = "Listar tipos de habitación (para el formulario de creación)")
    @GetMapping("/tipos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> listarTipos(
            @RequestHeader("Authorization") String authHeader) {
        return habitacionesClient.listarTipos(authHeader);
    }

    @Operation(summary = "Listar amenidades disponibles (para el formulario de creación)")
    @GetMapping("/amenidades")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> listarAmenidades(
            @RequestHeader("Authorization") String authHeader) {
        return habitacionesClient.listarAmenidades(authHeader);
    }

    @Operation(summary = "Obtener habitación por ID")
    @GetMapping("/{id}")
    public ResponseEntity<Object> getById(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        return habitacionesClient.getById(authHeader, id);
    }

    @Operation(summary = "Crear habitación")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> crear(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Object body) {
        var response = habitacionesClient.crear(authHeader, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(response.getBody());
    }

    @Operation(summary = "Cambiar estado de habitación (DISPONIBLE, MANTENIMIENTO, etc.)")
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public ResponseEntity<Object> cambiarEstado(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Object body) {
        return habitacionesClient.cambiarEstado(authHeader, id, body);
    }

    @Operation(summary = "Desactivar habitación (soft delete)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> desactivar(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        habitacionesClient.desactivar(authHeader, id);
        return ResponseEntity.noContent().build();
    }
}
