package com.staysync.pagos.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.staysync.pagos.config.StripeConfig;
import com.staysync.pagos.dto.request.StripeCheckoutRequest;
import com.staysync.pagos.dto.request.StripeConfirmarRequest;
import com.staysync.pagos.dto.response.PagoResponse;
import com.staysync.pagos.dto.response.StripeCheckoutResponse;
import com.staysync.pagos.exception.PagoFallidoException;
import com.staysync.pagos.model.Pago;
import com.staysync.pagos.repository.PagoRepository;
import com.staysync.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class StripeService {

    private final StripeConfig stripeConfig;
    private final PagoRepository pagoRepository;
    private final StripeGateway stripeGateway;
    private final PagoEventos pagoEventos;

    public StripeCheckoutResponse crearCheckout(StripeCheckoutRequest request) {
        long monto = request.getMonto().setScale(0, RoundingMode.HALF_UP).longValue();
        log.info("Creando Stripe Checkout — monto={} CLP reservaId={}", monto, request.getReservaId());

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(stripeConfig.getSuccessUrl() + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(stripeConfig.getCancelUrl())
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("clp")
                                                    .setUnitAmount(monto)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName(request.getTituloReserva())
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .putMetadata("reserva_id",
                            request.getReservaId() != null ? request.getReservaId().toString() : "")
                    // Para auditoría en el panel de Stripe: a qué hotel pertenece el cobro
                    .putMetadata("tenant_id", TenantContext.get().orElse(""))
                    .build();

            Session session = stripeGateway.createSession(params);
            log.info("Stripe Checkout creado — sessionId={}", session.getId());

            return StripeCheckoutResponse.builder()
                    .sessionId(session.getId())
                    .checkoutUrl(session.getUrl())
                    .reservaId(request.getReservaId())
                    .tituloReserva(request.getTituloReserva())
                    .build();

        } catch (StripeException e) {
            log.error("Stripe error — code={} mensaje={}", e.getCode(), e.getMessage());
            throw new PagoFallidoException("Error al crear sesión de pago: " + e.getMessage());
        }
    }

    @Transactional
    public PagoResponse confirmarPago(StripeConfirmarRequest request) {
        String sessionId = request.getSessionId();

        // Idempotencia: si ya procesamos esta sesión, devolver el pago existente
        if (pagoRepository.existsByGatewayId(sessionId)) {
            Pago existente = pagoRepository.findByGatewayId(sessionId).orElseThrow();
            log.info("Sesión Stripe ya confirmada anteriormente — sessionId={} pagoId={}", sessionId, existente.getId());
            return toResponse(existente);
        }

        try {
            Session session = stripeGateway.retrieveSession(sessionId);
            log.info("Stripe session recuperada — id={} paymentStatus={}", session.getId(), session.getPaymentStatus());

            if (!"paid".equals(session.getPaymentStatus())) {
                throw new PagoFallidoException("El pago de la sesión Stripe no está aprobado. Estado: " + session.getPaymentStatus());
            }

            String reservaIdStr = session.getMetadata() != null ? session.getMetadata().get("reserva_id") : null;
            Long reservaId = (reservaIdStr != null && !reservaIdStr.isBlank())
                    ? Long.parseLong(reservaIdStr) : null;

            // CLP es zero-decimal en Stripe — amountTotal ya está en pesos
            BigDecimal monto = BigDecimal.valueOf(session.getAmountTotal());

            String referencia = generarReferencia();

            Pago pago = Pago.builder()
                    .referencia(referencia)
                    .reservaId(reservaId)
                    .usuarioId(request.getUsuarioId())
                    .monto(monto)
                    .moneda("CLP")
                    .metodoPago(Pago.MetodoPago.STRIPE)
                    .estado(Pago.EstadoPago.COMPLETADO)
                    .gatewayId(sessionId)
                    .descripcion("Pago Stripe — " + sessionId)
                    .build();

            pago = pagoRepository.save(pago);
            log.info("Pago Stripe registrado — referencia={} reservaId={}", referencia, reservaId);

            if (reservaId != null) {
                // Reservas confirma la reserva al recibir PagoEfectuado
                pagoEventos.pagoEfectuado(pago);
            }

            return toResponse(pago);

        } catch (StripeException e) {
            log.error("Error recuperando sesión Stripe — sessionId={} error={}", sessionId, e.getMessage());
            throw new PagoFallidoException("No se pudo verificar el pago con Stripe: " + e.getMessage());
        }
    }

    private String generarReferencia() {
        String ref;
        do {
            ref = "STRIPE-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
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
