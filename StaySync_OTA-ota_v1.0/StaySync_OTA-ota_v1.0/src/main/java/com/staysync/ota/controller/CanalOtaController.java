package com.staysync.ota.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.ota.dto.request.SincronizarEstadoRequest;
import com.staysync.ota.model.CanalOta;
import com.staysync.ota.model.ReservaOta;
import com.staysync.ota.security.FirmaWebhook;
import com.staysync.ota.service.CanalOtaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "OTA Integration", description = "Gestión de canales de distribución y sincronización OTA")
public class CanalOtaController {

    private final CanalOtaService canalOtaService;
    private final FirmaWebhook firmaWebhook;
    private final ObjectMapper objectMapper;

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
    @Operation(summary = "Recibir reserva externa de un canal OTA (webhook)",
               description = "Requiere los headers X-Webhook-Timestamp y X-Webhook-Signature: "
                       + "sha256=hex(HMAC-SHA256(secreto del canal, timestamp + \".\" + cuerpo))")
    public ResponseEntity<ReservaOta> recibirWebhook(
            @PathVariable String codigoCanal,
            @RequestHeader(value = FirmaWebhook.HEADER_TIMESTAMP, required = false) String timestamp,
            @RequestHeader(value = FirmaWebhook.HEADER_FIRMA, required = false) String firma,
            @RequestBody byte[] cuerpo) {
        // La firma se calcula sobre los bytes exactos recibidos: se verifica antes de interpretarlos
        firmaWebhook.verificar(codigoCanal, timestamp, firma, cuerpo);
        Map<String, Object> payload = leerPayload(cuerpo);

        CanalOta canal = canalOtaService.listarActivos().stream()
                .filter(c -> c.getCodigo().equalsIgnoreCase(codigoCanal))
                .findFirst()
                .orElseThrow(() -> new com.staysync.ota.exception.CanalOtaNotFoundException(codigoCanal));

        String reservaIdExt = (String) payload.getOrDefault("id", "EXT-" + System.currentTimeMillis());
        ReservaOta reservaOta = canalOtaService.registrarReservaExterna(canal.getId(), reservaIdExt, payload);
        return ResponseEntity.ok(reservaOta);
    }

    private Map<String, Object> leerPayload(byte[] cuerpo) {
        try {
            return objectMapper.readValue(cuerpo, new TypeReference<>() { });
        } catch (IOException e) {
            throw new IllegalArgumentException("El cuerpo del webhook no es un JSON válido");
        }
    }
}
