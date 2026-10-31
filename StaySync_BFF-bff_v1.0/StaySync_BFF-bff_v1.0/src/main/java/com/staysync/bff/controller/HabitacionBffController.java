package com.staysync.bff.controller;

import com.staysync.bff.client.HabitacionesClient;
import com.staysync.bff.client.UsuariosClient;
import com.staysync.bff.exception.AccesoDenegadoException;
import com.staysync.bff.security.HotelVista;
import com.staysync.bff.security.JwtService;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
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
    private final UsuariosClient usuariosClient;
    private final JwtService jwtService;

    @Operation(summary = "Listar habitaciones disponibles con filtros opcionales",
               description = "Pública: sin sesión se usa el hotel del portal (header X-Hotel)")
    @GetMapping("/disponibles")
    public ResponseEntity<Object> listarDisponibles(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) Integer capacidad,
            @RequestParam(required = false) String amenidad,
            @RequestParam(defaultValue = "precio_asc") String sort) {
        if (capacidad == null && (amenidad == null || amenidad.isBlank())) {
            return habitacionesClient.listarDisponibles(authHeader);
        }
        return habitacionesClient.buscarDisponibles(authHeader, capacidad, amenidad, sort);
    }

    @Operation(summary = "Listar todas las habitaciones",
               description = "SUPER_ADMIN: ?hotel=todos o ?hotel=<tenantId> lista las de otros hoteles, "
                       + "con hotelId y hotelNombre en cada una")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public ResponseEntity<Object> listarTodas(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String hotel) {
        if (hotel == null || hotel.isBlank()) {
            return habitacionesClient.listarTodas(authHeader);
        }
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        if (!"SUPER_ADMIN".equals(jwtService.extractRol(token))) {
            throw new AccesoDenegadoException("Solo la plataforma ve las habitaciones de otros hoteles.");
        }
        List<Map<String, Object>> hoteles = lista(usuariosClient.listarHoteles(authHeader).getBody()).stream()
                .filter(h -> "todos".equals(hotel) || hotel.equals(h.get("tenantId")))
                .toList();
        if (hoteles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el hotel " + hotel + ".");
        }
        List<Map<String, Object>> todas = new ArrayList<>();
        for (Map<String, Object> h : hoteles) {
            String tenantId = String.valueOf(h.get("tenantId"));
            Object habitaciones = HotelVista.con(tenantId, () ->
                    habitacionesClient.reenviar(authHeader, HttpMethod.GET, "/api/v1/habitaciones", null).getBody());
            for (Map<String, Object> hab : lista(habitaciones)) {
                Map<String, Object> conHotel = new LinkedHashMap<>(hab);
                conHotel.put("hotelId", tenantId);
                conHotel.put("hotelNombre", h.get("nombre"));
                todas.add(conHotel);
            }
        }
        return ResponseEntity.ok(todas);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Object body) {
        return body instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
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

    @Operation(summary = "Agregar una foto (body: { url, esPrincipal }); la primera queda de portada")
    @PostMapping("/{id}/imagenes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> agregarImagen(@RequestHeader("Authorization") String authHeader,
                                                @PathVariable Long id, @RequestBody Object body) {
        return habitacionesClient.reenviar(authHeader, HttpMethod.POST, "/api/v1/habitaciones/" + id + "/imagenes", body);
    }

    @Operation(summary = "Quitar una foto de la habitación")
    @DeleteMapping("/{id}/imagenes/{imagenId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> eliminarImagen(@RequestHeader("Authorization") String authHeader,
                                                 @PathVariable Long id, @PathVariable Long imagenId) {
        return habitacionesClient.reenviar(authHeader, HttpMethod.DELETE,
                "/api/v1/habitaciones/" + id + "/imagenes/" + imagenId, null);
    }
}
