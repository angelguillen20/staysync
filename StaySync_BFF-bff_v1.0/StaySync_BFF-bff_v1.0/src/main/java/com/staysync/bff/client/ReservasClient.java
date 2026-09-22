package com.staysync.bff.client;

import com.staysync.bff.config.ServicesProperties;
import com.staysync.bff.exception.DownstreamServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;


@Slf4j
@Component
public class ReservasClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ReservasClient(RestTemplate restTemplate, ServicesProperties services) {
        this.restTemplate = restTemplate;
        this.baseUrl = services.reservas().url();
    }

    @CircuitBreaker(name = "reservasCB", fallbackMethod = "listarFallback")
    public ResponseEntity<Object> listar(String authHeader) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/reservas",
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "reservasCB", fallbackMethod = "listarFallback")
    public ResponseEntity<Object> getReservasHoy(String authHeader) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/reservas/hoy",
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "reservasCB", fallbackMethod = "getOneFallback")
    public ResponseEntity<Object> getById(String authHeader, Long id) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/reservas/" + id,
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "reservasCB", fallbackMethod = "listarFallback")
    public ResponseEntity<Object> listarPorUsuario(String authHeader, Long usuarioId, int page, int size) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/reservas/usuario/" + usuarioId
                        + "?page=" + page + "&size=" + size + "&sort=createdAt,desc",
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "reservasCB", fallbackMethod = "mutarFallback")
    public ResponseEntity<Object> crear(String authHeader, Object body) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/reservas",
                HttpMethod.POST,
                new HttpEntity<>(body, UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "reservasCB", fallbackMethod = "mutarFallback")
    public ResponseEntity<Object> cambiarEstado(String authHeader, Long id, Object body) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/reservas/" + id + "/estado",
                HttpMethod.PATCH,
                new HttpEntity<>(body, UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "reservasCB", fallbackMethod = "mutarFallback")
    public ResponseEntity<Object> cancelar(String authHeader, Long id) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/reservas/" + id + "/cancelar",
                HttpMethod.PATCH,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    public ResponseEntity<Object> listarFallback(String authHeader, Exception ex) {
        log.error("reservas-service no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de reservas no está disponible.");
    }

    public ResponseEntity<Object> listarFallback(String authHeader, Long usuarioId, int page, int size, Exception ex) {
        log.error("reservas-service no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de reservas no está disponible.");
    }

    public ResponseEntity<Object> getOneFallback(String authHeader, Long id, Exception ex) {
        log.warn("reservas-service no disponible para id={}: {}", id, ex.getMessage());
        throw new DownstreamServiceException("El servicio de reservas no está disponible.");
    }

    public ResponseEntity<Object> mutarFallback(String authHeader, Object body, Exception ex) {
        log.error("reservas-service no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de reservas no está disponible.");
    }

    public ResponseEntity<Object> mutarFallback(String authHeader, Long id, Object body, Exception ex) {
        log.error("reservas-service no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de reservas no está disponible.");
    }

    public ResponseEntity<Object> mutarFallback(String authHeader, Long id, Exception ex) {
        log.error("reservas-service no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de reservas no está disponible.");
    }
}
