package com.staysync.bff.controller;

import com.staysync.bff.client.ServiciosClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/bff/servicios")
@RequiredArgsConstructor
@Tag(name = "BFF Servicios", description = "Servicios adicionales y solicitudes de huéspedes")
public class ServicioBffController {

    private final ServiciosClient serviciosClient;

    @Operation(summary = "Listar servicios disponibles")
    @GetMapping("/disponibles")
    public ResponseEntity<Object> listarDisponibles(
            @RequestHeader("Authorization") String authHeader) {
        return serviciosClient.listarDisponibles(authHeader);
    }

    @Operation(summary = "Listar servicios por categoría")
    @GetMapping("/categoria/{categoriaId}")
    public ResponseEntity<Object> listarPorCategoria(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long categoriaId) {
        return serviciosClient.listarPorCategoria(authHeader, categoriaId);
    }

    @Operation(summary = "Solicitar un servicio adicional para una reserva")
    @PostMapping("/solicitudes")
    public ResponseEntity<Object> solicitarServicio(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Object body) {
        var response = serviciosClient.solicitarServicio(authHeader, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(response.getBody());
    }

    @Operation(summary = "Listar TODAS las solicitudes — uso de recepción/admin")
    @GetMapping("/solicitudes/todas")
    public ResponseEntity<Object> listarTodasSolicitudes(
            @RequestHeader("Authorization") String authHeader) {
        return serviciosClient.listarTodasSolicitudes(authHeader);
    }

    @Operation(summary = "Listar solicitudes de servicios de un usuario")
    @GetMapping("/solicitudes")
    public ResponseEntity<Object> listarSolicitudes(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Long usuarioId) {
        if (usuarioId == null) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("message", "El parámetro usuarioId es requerido y debe ser un número válido."));
        }
        return serviciosClient.listarSolicitudes(authHeader, usuarioId);
    }

    @Operation(summary = "Actualizar estado de una solicitud (EN_PROCESO, COMPLETADO, CANCELADO)")
    @PatchMapping("/solicitudes/{id}/estado")
    public ResponseEntity<Object> actualizarEstado(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Object body) {
        return serviciosClient.actualizarEstadoSolicitud(authHeader, id, body);
    }
}
