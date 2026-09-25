package com.staysync.bff.controller;

import com.staysync.bff.client.WeatherClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Clima actual para cualquier usuario autenticado (huésped incluido). El dashboard de
 * recepción ya lo trae agregado en /bff/dashboard, pero ese endpoint es solo para staff.
 */
@RestController
@RequestMapping("/bff/clima")
@RequiredArgsConstructor
@Tag(name = "BFF Clima", description = "Clima actual de la ciudad del hotel")
public class ClimaBffController {

    private final WeatherClient weatherClient;

    @Operation(summary = "Obtener clima actual",
               description = "204 si no hay API key o WeatherAPI no responde")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getClima() {
        Map<String, Object> clima = weatherClient.obtenerClima();
        return clima == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(clima);
    }
}
