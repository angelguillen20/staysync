package com.staysync.bff.controller;

import com.staysync.bff.client.UsuariosClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * Hoteles de la plataforma. La marca (nombre, logo, colores) es pública: la pantalla de
 * login de cada portal la necesita antes de que el usuario inicie sesión. El listado
 * completo es solo para SUPER_ADMIN (selector de hotel en la gestión de usuarios).
 */
@RestController
@RequestMapping("/bff")
@RequiredArgsConstructor
@Tag(name = "BFF Hoteles", description = "Personalización visual pública de cada hotel")
public class HotelBffController {

    private final UsuariosClient usuariosClient;

    @Operation(summary = "Obtener la marca de un hotel por su slug", description = "404 si el hotel no existe")
    @GetMapping("/public/hoteles/{slug}/theme")
    public ResponseEntity<Object> theme(@PathVariable String slug) {
        ResponseEntity<Object> response = usuariosClient.obtenerThemeHotel(slug);
        return ResponseEntity.status(response.getStatusCode())
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(response.getBody());
    }

    @Operation(summary = "Editar nombre, logo y colores de un hotel",
               description = "ADMIN: solo su propio hotel. SUPER_ADMIN: cualquiera. Lo valida usuarios-service.")
    @PutMapping("/hoteles/{tenantId}/marca")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> actualizarMarca(@RequestHeader("Authorization") String authHeader,
                                                  @PathVariable String tenantId,
                                                  @RequestBody Object body) {
        return usuariosClient.actualizarMarca(authHeader, tenantId, body);
    }

    @Operation(summary = "Listar hoteles activos (solo SUPER_ADMIN)")
    @GetMapping("/hoteles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Object> listar(@RequestHeader("Authorization") String authHeader) {
        return usuariosClient.listarHoteles(authHeader);
    }
}
