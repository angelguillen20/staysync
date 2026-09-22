package com.staysync.bff.client;

import com.staysync.bff.config.ServicesProperties;
import com.staysync.bff.exception.DownstreamServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Slf4j
@Component
public class PagosClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PagosClient(RestTemplate restTemplate, ServicesProperties services) {
        this.restTemplate = restTemplate;
        this.baseUrl = services.pagos().url();
    }

    @CircuitBreaker(name = "pagosCB", fallbackMethod = "mutarFallback")
    public ResponseEntity<Object> procesarPago(String authHeader, Object body) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/pagos",
                HttpMethod.POST,
                new HttpEntity<>(body, UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "pagosCB", fallbackMethod = "getOneFallback")
    public ResponseEntity<Object> getPagoById(String authHeader, Long id) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/pagos/" + id,
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "pagosCB", fallbackMethod = "listarFallback")
    public ResponseEntity<Object> getPagosPorReserva(String authHeader, Long reservaId) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/pagos/reserva/" + reservaId,
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "pagosCB", fallbackMethod = "mutarFallback")
    public ResponseEntity<Object> solicitarReembolso(String authHeader, Long pagoId, Object body) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/pagos/" + pagoId + "/reembolso",
                HttpMethod.POST,
                new HttpEntity<>(body, UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "pagosCB", fallbackMethod = "stripeFallback")
    public ResponseEntity<Object> crearCheckoutStripe(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                baseUrl + "/api/pagos/stripe/checkout",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Object.class);
    }

    @CircuitBreaker(name = "pagosCB", fallbackMethod = "stripeFallback")
    public ResponseEntity<Object> confirmarCheckoutStripe(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                baseUrl + "/api/pagos/stripe/confirmar",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Object.class);
    }

    public ResponseEntity<Object> stripeFallback(Object body, Exception ex) {
        log.error("pagos-service Stripe no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de pago Stripe no está disponible. Intente nuevamente.");
    }

    public ResponseEntity<Object> mutarFallback(String authHeader, Object body, Exception ex) {
        log.error("pagos-service no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de pagos no está disponible. Intente nuevamente.");
    }

    public ResponseEntity<Object> mutarFallback(String authHeader, Long id, Object body, Exception ex) {
        log.error("pagos-service no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de pagos no está disponible.");
    }

    public ResponseEntity<Object> getOneFallback(String authHeader, Long id, Exception ex) {
        log.warn("pagos-service no disponible para id={}: {}", id, ex.getMessage());
        throw new DownstreamServiceException("El servicio de pagos no está disponible.");
    }

    public ResponseEntity<Object> listarFallback(String authHeader, Long reservaId, Exception ex) {
        log.warn("pagos-service no disponible: {}", ex.getMessage());
        return ResponseEntity.ok(Collections.emptyList());
    }
}
