package com.staysync.pagos.service;

import com.staysync.pagos.dto.request.ProcesarPagoRequest;
import com.staysync.pagos.dto.response.PagoResponse;
import com.staysync.pagos.exception.PagoFallidoException;
import com.staysync.pagos.exception.PagoNotFoundException;
import com.staysync.pagos.model.Pago;
import com.staysync.pagos.model.Pago.EstadoPago;
import com.staysync.pagos.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PagoService {

    private final PagoRepository pagoRepository;
    private final PagoEventos pagoEventos;

    /**
     * Pago recibido en recepción (efectivo, transferencia o tarjeta en el POS del hotel): el
     * dinero ya está en caja, así que queda COMPLETADO. El huésped paga en línea con Stripe; el
     * BFF solo deja llegar aquí al personal y fija el monto desde la reserva.
     */
    @Transactional
    public PagoResponse procesar(ProcesarPagoRequest request) {
        Pago pago = pagoRepository.save(Pago.builder()
                .referencia(generarReferencia())
                .reservaId(request.getReservaId())
                .usuarioId(request.getUsuarioId())
                .monto(request.getMonto())
                .moneda(request.getMoneda() != null ? request.getMoneda() : "CLP")
                .metodoPago(request.getMetodoPago())
                .descripcion(request.getDescripcion())
                .estado(EstadoPago.COMPLETADO)
                .build());
        // Reservas confirma la reserva al recibir PagoEfectuado
        pagoEventos.pagoEfectuado(pago);
        log.info("Pago en recepción registrado: {} — reservaId={}", pago.getReferencia(), pago.getReservaId());
        return toResponse(pago);
    }

    public PagoResponse obtenerPorId(Long id) {
        return toResponse(pagoRepository.findById(id)
                .orElseThrow(() -> new PagoNotFoundException(id)));
    }

    public PagoResponse obtenerPorReferencia(String referencia) {
        return toResponse(pagoRepository.findByReferencia(referencia)
                .orElseThrow(() -> new PagoNotFoundException(referencia)));
    }

    public List<PagoResponse> obtenerPorReserva(Long reservaId) {
        return pagoRepository.findByReservaId(reservaId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public PagoResponse solicitarReembolso(Long pagoId) {
        Pago pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new PagoNotFoundException(pagoId));
        if (pago.getEstado() != Pago.EstadoPago.COMPLETADO) {
            throw new PagoFallidoException(
                    "Solo se pueden reembolsar pagos COMPLETADOS. Estado actual: " + pago.getEstado());
        }
        pago.setEstado(Pago.EstadoPago.REEMBOLSADO);
        pagoRepository.save(pago);
        pagoEventos.reembolsoEfectuado(pago);
        log.info("Reembolso procesado: {}", pago.getReferencia());
        return toResponse(pago);
    }

    /**
     * Reacción a ReservaCancelada: devuelve los pagos completados de esa reserva. Si ya
     * estaban reembolsados (evento duplicado) no hace nada.
     */
    @Transactional
    public int reembolsarPorCancelacion(Long reservaId) {
        List<Pago> completados = pagoRepository.findByReservaId(reservaId).stream()
                .filter(p -> p.getEstado() == Pago.EstadoPago.COMPLETADO)
                .toList();
        completados.forEach(p -> {
            p.setEstado(Pago.EstadoPago.REEMBOLSADO);
            pagoRepository.save(p);
            pagoEventos.reembolsoEfectuado(p);
            log.info("Reembolso automático de {} por cancelación de la reserva {}", p.getReferencia(), reservaId);
        });
        return completados.size();
    }

    private String generarReferencia() {
        String ref;
        do {
            ref = "PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        } while (pagoRepository.existsByReferencia(ref));
        return ref;
    }

    private PagoResponse toResponse(Pago p) {
        return PagoResponse.builder()
                .id(p.getId()).referencia(p.getReferencia())
                .reservaId(p.getReservaId()).usuarioId(p.getUsuarioId())
                .monto(p.getMonto()).moneda(p.getMoneda())
                .metodoPago(p.getMetodoPago()).estado(p.getEstado())
                .gatewayId(p.getGatewayId()).descripcion(p.getDescripcion())
                .createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt())
                .build();
    }
}
