package com.staysync.ota.controller;

import com.staysync.ota.dto.request.SincronizarEstadoRequest;
import com.staysync.ota.model.CanalOta;
import com.staysync.ota.model.ReservaOta;
import com.staysync.ota.service.CanalOtaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "OTA Integration", description = "Gestión de canales de distribución y sincronización OTA")
public class CanalOtaController {

    private final CanalOtaService canalOtaService;

    @GetMapping("/canales")
    @Operation(summary = "Listar canales OTA activos")
    public ResponseEntity<List<CanalOta>> listarCanales() {
        return ResponseEntity.ok(canalOtaService.listarActivos());
    }

    @GetMapping("/canales/{id}")
    @Operation(summary = "Obtener canal OTA por ID")
    public ResponseEntity<CanalOta> obtenerCanal(@PathVariable Long id) {
        return ResponseEntity.ok(canalOtaService.obtenerPorId(id));
    }

    @GetMapping("/sincronizacion/pendientes")
    @Operation(summary = "Listar reservas OTA pendientes de sincronización")
    public ResponseEntity<List<ReservaOta>> listarPendientes() {
        return ResponseEntity.ok(canalOtaService.listarPendientesSincronizacion());
    }

    @PostMapping("/sincronizacion/estado")
    @Operation(summary = "Sincronizar canales OTA tras un cambio de estado de habitación")
    public ResponseEntity<Void> sincronizarEstado(@RequestBody SincronizarEstadoRequest request) {
        canalOtaService.sincronizarEstadoHabitacion(
                request.getHabitacionId(), request.getEstadoAnterior(), request.getEstadoNuevo());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/webhooks/{codigoCanal}")
    @Operation(summary = "Recibir reserva externa de un canal OTA (webhook)")
    public ResponseEntity<ReservaOta> recibirWebhook(@PathVariable String codigoCanal,
                                                       @RequestBody Map<String, Object> payload) {
        CanalOta canal = canalOtaService.listarActivos().stream()
                .filter(c -> c.getCodigo().equalsIgnoreCase(codigoCanal))
                .findFirst()
                .orElseThrow(() -> new com.staysync.ota.exception.CanalOtaNotFoundException(codigoCanal));

        String reservaIdExt = (String) payload.getOrDefault("id", "EXT-" + System.currentTimeMillis());
        ReservaOta reservaOta = canalOtaService.registrarReservaExterna(canal.getId(), reservaIdExt, payload);
        return ResponseEntity.ok(reservaOta);
    }
}
