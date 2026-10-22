package com.staysync.usuarios.tenant.controller;

import com.staysync.usuarios.tenant.dto.CrearHotelRequest;
import com.staysync.usuarios.tenant.service.AltaHotelService;
import org.springframework.http.HttpStatus;
import com.staysync.usuarios.exception.UsuarioNotFoundException;
import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.UsuarioRepository;
import com.staysync.usuarios.tenant.dto.ActualizarMarcaRequest;
import com.staysync.usuarios.tenant.dto.HotelResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import com.staysync.usuarios.tenant.service.HotelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hoteles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Hoteles", description = "Administración de hoteles de la plataforma (solo SUPER_ADMIN)")
public class HotelAdminController {

    private final HotelService hotelService;
    private final AltaHotelService altaHotelService;
    private final UsuarioRepository usuarioRepository;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Crear un hotel con su administrador inicial (solo SUPER_ADMIN)")
    public ResponseEntity<HotelResponse> crear(@Valid @RequestBody CrearHotelRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(altaHotelService.crear(request, auth.getName()));
    }

    @PutMapping("/{tenantId}/marca")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Editar nombre, logo y colores del hotel (ADMIN: el suyo; SUPER_ADMIN: cualquiera)")
    public ResponseEntity<HotelResponse> actualizarMarca(@PathVariable String tenantId,
                                                         @Valid @RequestBody ActualizarMarcaRequest request,
                                                         Authentication auth) {
        Usuario solicitante = usuarioRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new UsuarioNotFoundException(auth.getName()));
        return ResponseEntity.ok(hotelService.actualizarMarca(
                tenantId, request, solicitante.getRol().name(), solicitante.getTenantId()));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Listar hoteles activos (selector de hotel en la pantalla de usuarios)")
    public ResponseEntity<List<HotelResponse>> listar() {
        return ResponseEntity.ok(hotelService.listarActivos());
    }
}
