package com.staysync.habitaciones.controller;

import com.staysync.habitaciones.dto.response.AmenidadResponse;
import com.staysync.habitaciones.dto.response.TipoHabitacionResponse;
import com.staysync.habitaciones.service.HabitacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Catálogo", description = "Tipos de habitación y amenidades disponibles para crear habitaciones")
public class CatalogoController {

    private final HabitacionService habitacionService;

    @GetMapping("/api/v1/tipos-habitacion")
    @Operation(summary = "Listar tipos de habitación activos")
    public ResponseEntity<List<TipoHabitacionResponse>> listarTipos() {
        return ResponseEntity.ok(habitacionService.listarTiposActivos());
    }

    @GetMapping("/api/v1/amenidades")
    @Operation(summary = "Listar amenidades disponibles")
    public ResponseEntity<List<AmenidadResponse>> listarAmenidades() {
        return ResponseEntity.ok(habitacionService.listarAmenidades());
    }
}
