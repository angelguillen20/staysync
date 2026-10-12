package com.staysync.usuarios.tenant.controller;

import com.staysync.usuarios.tenant.dto.HotelResponse;
import com.staysync.usuarios.tenant.service.HotelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/public/hoteles")
@RequiredArgsConstructor
@Tag(name = "Hoteles (público)", description = "Marca del hotel para pintar el portal antes del login")
public class HotelPublicController {

    private final HotelService hotelService;

    @GetMapping("/{slug}/theme")
    @Operation(summary = "Obtener nombre, logo y colores de un hotel por su slug",
               responses = {
                   @ApiResponse(responseCode = "200",
                       content = @Content(schema = @Schema(implementation = HotelResponse.class))),
                   @ApiResponse(responseCode = "404", description = "Hotel no encontrado o inactivo")
               })
    public ResponseEntity<HotelResponse> theme(@PathVariable String slug) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(hotelService.obtenerPorSlug(slug));
    }
}
