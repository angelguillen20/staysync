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
public class ServiciosClient {

    private static final String LOG_NO_DISPONIBLE = "servicios-service no disponible: {}";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ServiciosClient(RestTemplate restTemplate, ServicesProperties services) {
        this.restTemplate = restTemplate;
        this.baseUrl = services.servicios().url();
    }

    @CircuitBreaker(name = "serviciosCB", fallbackMethod = "listarFallback")
    public ResponseEntity<Object> listarDisponibles(String authHeader) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/servicios",
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "serviciosCB", fallbackMethod = "listarFallback")
    public ResponseEntity<Object> listarPorCategoria(String authHeader, Long categoriaId) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/servicios/categoria/" + categoriaId,
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "serviciosCB", fallbackMethod = "mutarFallback")
    public ResponseEntity<Object> solicitarServicio(String authHeader, Object body) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/solicitudes",
                HttpMethod.POST,
                new HttpEntity<>(body, UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "serviciosCB", fallbackMethod = "listarFallback")
    public ResponseEntity<Object> listarSolicitudes(String authHeader, Long usuarioId) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/solicitudes/usuario/" + usuarioId,
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "serviciosCB", fallbackMethod = "listarFallback")
    public ResponseEntity<Object> listarTodasSolicitudes(String authHeader) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/solicitudes",
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "serviciosCB", fallbackMethod = "mutarFallback")
    public ResponseEntity<Object> actualizarEstadoSolicitud(String authHeader, Long id, Object body) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/solicitudes/" + id + "/estado",
                HttpMethod.PATCH,
                new HttpEntity<>(body, UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "serviciosCB", fallbackMethod = "listarFallback")
    public ResponseEntity<Object> listarAseo(String authHeader) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/aseo",
                HttpMethod.GET,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "serviciosCB", fallbackMethod = "mutarFallback")
    public ResponseEntity<Object> completarAseo(String authHeader, Long id) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/aseo/" + id + "/completar",
                HttpMethod.PATCH,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    // Fallbacks de Resilience4j: deben repetir la firma del método protegido aunque no usen todos sus parámetros.
    @SuppressWarnings("java:S1172")
    public ResponseEntity<Object> listarFallback(String authHeader, Exception ex) {
        log.warn(LOG_NO_DISPONIBLE, ex.getMessage());
        return ResponseEntity.ok(Collections.emptyList());
    }

    @SuppressWarnings("java:S1172")
    public ResponseEntity<Object> listarFallback(String authHeader, Long usuarioId, Exception ex) {
        log.warn(LOG_NO_DISPONIBLE, ex.getMessage());
        return ResponseEntity.ok(Collections.emptyList());
    }

    @SuppressWarnings("java:S1172")
    public ResponseEntity<Object> mutarFallback(String authHeader, Object body, Exception ex) {
        log.error(LOG_NO_DISPONIBLE, ex.getMessage());
        throw new DownstreamServiceException("El servicio de servicios adicionales no está disponible.");
    }

    @SuppressWarnings("java:S1172")
    public ResponseEntity<Object> mutarFallback(String authHeader, Long id, Exception ex) {
        log.error(LOG_NO_DISPONIBLE, ex.getMessage());
        throw new DownstreamServiceException("El servicio de servicios adicionales no está disponible.");
    }

    @SuppressWarnings("java:S1172")
    public ResponseEntity<Object> mutarFallback(String authHeader, Long id, Object body, Exception ex) {
        log.error(LOG_NO_DISPONIBLE, ex.getMessage());
        throw new DownstreamServiceException("El servicio de servicios adicionales no está disponible.");
    }
}
