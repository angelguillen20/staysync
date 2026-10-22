package com.staysync.servicios.controller;

import com.staysync.servicios.model.TareaAseo;
import com.staysync.servicios.service.AseoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/aseo")
@RequiredArgsConstructor
@Tag(name = "Aseo", description = "Tareas de limpieza generadas por los eventos de reserva")
public class AseoController {

    private final AseoService aseoService;

    @GetMapping
    @Operation(summary = "Listar tareas de aseo del hotel, por fecha")
    public ResponseEntity<List<TareaAseo>> listar() {
        return ResponseEntity.ok(aseoService.listar());
    }

    @PatchMapping("/{id}/completar")
    @Operation(summary = "Marcar una tarea de aseo como completada")
    public ResponseEntity<TareaAseo> completar(@PathVariable Long id) {
        return ResponseEntity.ok(aseoService.completar(id));
    }
}
