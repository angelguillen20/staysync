package com.staysync.servicios.controller;

import com.staysync.servicios.dto.request.SolicitudServicioRequest;
import com.staysync.servicios.dto.response.ServicioResponse;
import com.staysync.servicios.dto.response.SolicitudServicioResponse;
import com.staysync.servicios.model.SolicitudServicio.EstadoSolicitud;
import com.staysync.servicios.service.ServicioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Servicios Hoteleros", description = "Gestión de servicios adicionales y solicitudes")
public class ServicioController {

    private final ServicioService servicioService;

    @GetMapping("/servicios")
    @Operation(summary = "Listar servicios disponibles")
    public ResponseEntity<List<ServicioResponse>> listar() {
        return ResponseEntity.ok(servicioService.listarDisponibles());
    }

    @GetMapping("/servicios/{id}")
    @Operation(summary = "Obtener servicio por ID")
    public ResponseEntity<ServicioResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(servicioService.obtenerPorId(id));
    }

    @GetMapping("/solicitudes")
    @Operation(summary = "Listar todas las solicitudes — uso de recepción/admin")
    public ResponseEntity<List<SolicitudServicioResponse>> listarTodas() {
        return ResponseEntity.ok(servicioService.listarTodasSolicitudes());
    }

    @GetMapping("/solicitudes/reserva/{reservaId}")
    @Operation(summary = "Listar solicitudes de una reserva")
    public ResponseEntity<List<SolicitudServicioResponse>> listarPorReserva(@PathVariable Long reservaId) {
        return ResponseEntity.ok(servicioService.listarSolicitudesPorReserva(reservaId));
    }

    @GetMapping("/solicitudes/usuario/{usuarioId}")
    @Operation(summary = "Listar solicitudes de un usuario")
    public ResponseEntity<List<SolicitudServicioResponse>> listarPorUsuario(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(servicioService.listarSolicitudesPorUsuario(usuarioId));
    }

    @PostMapping("/solicitudes")
    @Operation(summary = "Solicitar un servicio adicional")
    public ResponseEntity<SolicitudServicioResponse> crear(@Valid @RequestBody SolicitudServicioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicioService.crearSolicitud(request));
    }

    @PatchMapping("/solicitudes/{id}/estado")
    @Operation(summary = "Actualizar estado de una solicitud")
    public ResponseEntity<SolicitudServicioResponse> actualizarEstado(@PathVariable Long id,
                                                                       @RequestBody Map<String, String> body) {
        EstadoSolicitud estado = EstadoSolicitud.valueOf(body.get("estado"));
        return ResponseEntity.ok(servicioService.actualizarEstadoSolicitud(id, estado));
    }
}
