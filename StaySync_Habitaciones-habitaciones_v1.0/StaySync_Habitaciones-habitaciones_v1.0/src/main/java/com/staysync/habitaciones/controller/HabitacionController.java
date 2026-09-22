package com.staysync.habitaciones.controller;

import com.staysync.habitaciones.dto.request.CrearHabitacionRequest;
import com.staysync.habitaciones.dto.response.HabitacionResponse;
import com.staysync.habitaciones.model.Habitacion.EstadoHabitacion;
import com.staysync.habitaciones.service.HabitacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/habitaciones")
@RequiredArgsConstructor
@Tag(name = "Habitaciones", description = "Gestión del inventario de habitaciones del hotel")
public class HabitacionController {

    private final HabitacionService habitacionService;

    @GetMapping
    @Operation(summary = "Listar todas las habitaciones activas")
    public ResponseEntity<List<HabitacionResponse>> listar() {
        return ResponseEntity.ok(habitacionService.listarTodas());
    }

    @GetMapping("/disponibles")
    @Operation(summary = "Listar habitaciones disponibles con filtros opcionales",
               description = "Parámetros opcionales: capacidad (mínima), amenidad (nombre parcial), sort (precio_asc|precio_desc)")
    public ResponseEntity<List<HabitacionResponse>> listarDisponibles(
            @RequestParam(required = false) Integer capacidad,
            @RequestParam(required = false) String amenidad,
            @RequestParam(defaultValue = "precio_asc") String sort) {
        return ResponseEntity.ok(habitacionService.buscarDisponibles(capacidad, amenidad, sort));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener habitación por ID")
    public ResponseEntity<HabitacionResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(habitacionService.obtenerPorId(id));
    }

    @GetMapping("/numero/{numero}")
    @Operation(summary = "Obtener habitación por número")
    public ResponseEntity<HabitacionResponse> obtenerPorNumero(@PathVariable String numero) {
        return ResponseEntity.ok(habitacionService.obtenerPorNumero(numero));
    }

    @PostMapping
    @Operation(summary = "Crear nueva habitación")
    public ResponseEntity<HabitacionResponse> crear(@Valid @RequestBody CrearHabitacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(habitacionService.crear(request));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Cambiar estado de una habitación")
    public ResponseEntity<HabitacionResponse> cambiarEstado(@PathVariable Long id,
                                                             @RequestBody Map<String, String> body) {
        String estadoRaw = body != null ? body.get("estado") : null;
        if (estadoRaw == null || estadoRaw.isBlank()) {
            throw new IllegalArgumentException("El campo 'estado' es obligatorio.");
        }
        EstadoHabitacion estado;
        try {
            estado = EstadoHabitacion.valueOf(estadoRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Estado inválido: '" + estadoRaw + "'. Valores aceptados: " +
                java.util.Arrays.toString(EstadoHabitacion.values()));
        }
        return ResponseEntity.ok(habitacionService.cambiarEstado(id, estado));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactivar habitación (soft delete)")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        habitacionService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
