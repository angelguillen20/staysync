package com.staysync.bff.controller;

import com.staysync.bff.client.ServiciosClient;
import com.staysync.bff.security.AccesoHuesped;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/bff/servicios")
@RequiredArgsConstructor
@Tag(name = "BFF Servicios", description = "Servicios adicionales y solicitudes de huéspedes")
public class ServicioBffController {

    private final ServiciosClient serviciosClient;
    private final AccesoHuesped accesoHuesped;

    @Operation(summary = "Listar servicios disponibles")
    @GetMapping("/disponibles")
    public ResponseEntity<Object> listarDisponibles(
            @RequestHeader("Authorization") String authHeader) {
        return serviciosClient.listarDisponibles(authHeader);
    }

    @Operation(summary = "Catálogo completo del hotel, incluidos los no disponibles")
    @GetMapping("/catalogo")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> catalogo(@RequestHeader("Authorization") String authHeader) {
        return serviciosClient.reenviar(authHeader, HttpMethod.GET, "/api/v1/servicios/catalogo", null);
    }

    @Operation(summary = "Categorías de servicios")
    @GetMapping("/categorias")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> categorias(@RequestHeader("Authorization") String authHeader) {
        return serviciosClient.reenviar(authHeader, HttpMethod.GET, "/api/v1/servicios/categorias", null);
    }

    @Operation(summary = "Agregar un servicio al catálogo")
    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> crear(@RequestHeader("Authorization") String authHeader,
                                        @RequestBody Object body) {
        return serviciosClient.reenviar(authHeader, HttpMethod.POST, "/api/v1/servicios", body);
    }

    @Operation(summary = "Editar un servicio (disponible=false lo retira)")
    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> actualizar(@RequestHeader("Authorization") String authHeader,
                                             @PathVariable Long id, @RequestBody Object body) {
        return serviciosClient.reenviar(authHeader, HttpMethod.PUT, "/api/v1/servicios/" + id, body);
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
            @RequestBody Map<String, Object> body) {
        Map<String, Object> solicitud = new HashMap<>(body);
        // Un huésped pide servicios para sí y solo sobre sus reservas
        if (accesoHuesped.esHuesped(authHeader)) {
            solicitud.put("usuarioId", accesoHuesped.usuarioId(authHeader));
            if (solicitud.get("reservaId") != null) {
                accesoHuesped.verificarReservaPropia(authHeader, solicitud.get("reservaId"));
            }
        }
        var response = serviciosClient.solicitarServicio(authHeader, solicitud);
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
        accesoHuesped.verificarMismoUsuario(authHeader, usuarioId);
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

    @Operation(summary = "Tareas de aseo del hotel (las generan los eventos de reserva)")
    @GetMapping("/aseo")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
    public ResponseEntity<Object> listarAseo(@RequestHeader("Authorization") String authHeader) {
        return serviciosClient.listarAseo(authHeader);
    }

    @Operation(summary = "Marcar una tarea de aseo como completada")
    @PatchMapping("/aseo/{id}/completar")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")
    public ResponseEntity<Object> completarAseo(@RequestHeader("Authorization") String authHeader,
                                                @PathVariable Long id) {
        return serviciosClient.completarAseo(authHeader, id);
    }
}
