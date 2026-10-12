package com.staysync.usuarios.tenant.controller;

import com.staysync.usuarios.tenant.dto.HotelResponse;
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

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Listar hoteles activos (selector de hotel en la pantalla de usuarios)")
    public ResponseEntity<List<HotelResponse>> listar() {
        return ResponseEntity.ok(hotelService.listarActivos());
    }
}
