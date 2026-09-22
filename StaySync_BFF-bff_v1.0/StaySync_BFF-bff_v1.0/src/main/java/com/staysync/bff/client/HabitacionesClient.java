package com.staysync.bff.client;

import com.staysync.bff.config.ServicesProperties;
import com.staysync.bff.exception.DownstreamServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class HabitacionesClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public HabitacionesClient(RestTemplate restTemplate, ServicesProperties services) {
        this.restTemplate = restTemplate;
        this.baseUrl = services.habitaciones().url();
    }

    @CircuitBreaker(name = "habitacionesCB", fallbackMethod = "listarFallback")
    public ResponseEntity<Object> listarDisponibles(String authHeader) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/habitaciones/disponibles",
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "habitacionesCB", fallbackMethod = "buscarFallback")
    public ResponseEntity<Object> buscarDisponibles(String authHeader, Integer capacidad, String amenidad, String sort) {
        UriComponentsBuilder uri = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/api/v1/habitaciones/disponibles");
        if (capacidad != null)                          uri.queryParam("capacidad", capacidad);
        if (amenidad != null && !amenidad.isBlank())    uri.queryParam("amenidad", amenidad);
        if (sort != null && !sort.isBlank())            uri.queryParam("sort", sort);
        return restTemplate.exchange(
                uri.toUriString(),
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "habitacionesCB", fallbackMethod = "listarFallback")
    public ResponseEntity<Object> listarTodas(String authHeader) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/habitaciones",
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "habitacionesCB", fallbackMethod = "getOneFallback")
    public ResponseEntity<Object> getById(String authHeader, Long id) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/habitaciones/" + id,
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "habitacionesCB", fallbackMethod = "passthruFallback")
    public ResponseEntity<Object> crear(String authHeader, Object body) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/habitaciones",
                HttpMethod.POST,
                new HttpEntity<>(body, UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "habitacionesCB", fallbackMethod = "passthruFallback")
    public ResponseEntity<Object> cambiarEstado(String authHeader, Long id, Object body) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/habitaciones/" + id + "/estado",
                HttpMethod.PATCH,
                new HttpEntity<>(body, UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "habitacionesCB", fallbackMethod = "listarFallback")
    public ResponseEntity<Object> listarTipos(String authHeader) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/tipos-habitacion",
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "habitacionesCB", fallbackMethod = "listarFallback")
    public ResponseEntity<Object> listarAmenidades(String authHeader) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/amenidades",
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "habitacionesCB", fallbackMethod = "desactivarFallback")
    public void desactivar(String authHeader, Long id) {
        restTemplate.exchange(
                baseUrl + "/api/v1/habitaciones/" + id,
                HttpMethod.DELETE,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Void.class);
    }

    public void desactivarFallback(String authHeader, Long id, Exception ex) {
        log.error("habitaciones-service no disponible al desactivar id={}: {}", id, ex.getMessage());
        throw new DownstreamServiceException("El servicio de habitaciones no está disponible.");
    }

    public ResponseEntity<Object> listarFallback(String authHeader, Exception ex) {
        log.warn("habitaciones-service no disponible, retornando lista vacía: {}", ex.getMessage());
        return ResponseEntity.ok(Collections.emptyList());
    }

    public ResponseEntity<Object> buscarFallback(String authHeader, Integer capacidad, String amenidad, String sort, Exception ex) {
        log.warn("habitaciones-service no disponible para búsqueda filtrada: {}", ex.getMessage());
        return ResponseEntity.ok(Collections.emptyList());
    }

    public ResponseEntity<Object> getOneFallback(String authHeader, Long id, Exception ex) {
        log.warn("habitaciones-service no disponible para id={}: {}", id, ex.getMessage());
        return ResponseEntity.ok(null);
    }

    public ResponseEntity<Object> passthruFallback(String authHeader, Object body, Exception ex) {
        log.error("habitaciones-service no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de habitaciones no está disponible.");
    }

    public ResponseEntity<Object> passthruFallback(String authHeader, Long id, Object body, Exception ex) {
        log.error("habitaciones-service no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de habitaciones no está disponible.");
    }
}
