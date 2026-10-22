package com.staysync.bff.controller;

import com.staysync.bff.client.PagosClient;
import com.staysync.bff.security.AccesoHuesped;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/bff/pagos")
@RequiredArgsConstructor
@Tag(name = "BFF Pagos", description = "Procesamiento de pagos y reembolsos")
public class PagoBffController {

    private final PagosClient pagosClient;
    private final AccesoHuesped accesoHuesped;

    @Operation(summary = "Procesar pago de una reserva")
    @PostMapping
    public ResponseEntity<Object> procesarPago(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> pago = new HashMap<>(body);
        // Un huésped solo paga sus propias reservas
        if (accesoHuesped.esHuesped(authHeader)) {
            accesoHuesped.verificarReservaPropia(authHeader, pago.get("reservaId"));
            pago.put("usuarioId", accesoHuesped.usuarioId(authHeader));
        }
        var response = pagosClient.procesarPago(authHeader, pago);
        return ResponseEntity.status(HttpStatus.CREATED).body(response.getBody());
    }

    @Operation(summary = "Obtener pago por ID")
    @GetMapping("/{id}")
    public ResponseEntity<Object> getPagoById(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        return pagosClient.getPagoById(authHeader, id);
    }

    @Operation(summary = "Obtener pagos de una reserva")
    @GetMapping("/reserva/{reservaId}")
    public ResponseEntity<Object> getPagosPorReserva(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long reservaId) {
        return pagosClient.getPagosPorReserva(authHeader, reservaId);
    }

    @Operation(summary = "Solicitar reembolso de un pago")
    @PostMapping("/{pagoId}/reembolso")
    public ResponseEntity<Object> solicitarReembolso(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long pagoId,
            @RequestBody Object body) {
        var response = pagosClient.solicitarReembolso(authHeader, pagoId, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(response.getBody());
    }

    @Operation(summary = "Crear sesión de pago en Stripe Checkout")
    @PostMapping("/stripe/checkout")
    public ResponseEntity<Object> crearCheckoutStripe(@RequestBody Object body) {
        var response = pagosClient.crearCheckoutStripe(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(response.getBody());
    }

    @Operation(summary = "Confirmar pago Stripe tras redirección exitosa")
    @PostMapping("/stripe/confirmar")
    public ResponseEntity<Object> confirmarCheckoutStripe(@RequestBody Object body) {
        var response = pagosClient.confirmarCheckoutStripe(body);
        return ResponseEntity.ok(response.getBody());
    }
}
