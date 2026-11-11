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

    private static final String LOG_NO_DISPONIBLE = "reservas-service no disponible: {}";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ReservasClient(RestTemplate restTemplate, ServicesProperties services) {
        this.restTemplate = restTemplate;
        this.baseUrl = services.reservas().url();
    }

    @CircuitBreaker(name = "reservasCB", fallbackMethod = "listarFallback")
    public ResponseEntity<Object> listar(String authHeader) {
        // Sin size, reservas-service pagina de a 10 y recepción solo veía las 10 más recientes.
        // shortcut: tope de 500 por hotel; paginar en el panel si un hotel pasa de eso.
        return restTemplate.exchange(
                baseUrl + "/api/v1/reservas?size=500",
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
                recursoUrl(id),
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
                recursoUrl(id) + "/estado",
                HttpMethod.PATCH,
                new HttpEntity<>(body, UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "reservasCB", fallbackMethod = "mutarFallback")
    public ResponseEntity<Object> cancelar(String authHeader, Long id) {
        return restTemplate.exchange(
                recursoUrl(id) + "/cancelar",
                HttpMethod.PATCH,
                new HttpEntity<>(UsuariosClient.buildHeaders(authHeader)),
                Object.class);
    }

    private String recursoUrl(Long id) {
        return baseUrl + "/api/v1/reservas/" + id;
    }

    // Fallbacks de Resilience4j: deben repetir la firma del método protegido aunque no usen todos sus parámetros.
    @SuppressWarnings("java:S1172")
    public ResponseEntity<Object> listarFallback(String authHeader, Exception ex) {
        log.error(LOG_NO_DISPONIBLE, ex.getMessage());
        throw new DownstreamServiceException("El servicio de reservas no está disponible.");
    }

    @SuppressWarnings("java:S1172")
    public ResponseEntity<Object> listarFallback(String authHeader, Long usuarioId, int page, int size, Exception ex) {
        log.error(LOG_NO_DISPONIBLE, ex.getMessage());
        throw new DownstreamServiceException("El servicio de reservas no está disponible.");
    }

    @SuppressWarnings("java:S1172")
    public ResponseEntity<Object> getOneFallback(String authHeader, Long id, Exception ex) {
        log.warn("reservas-service no disponible para id={}: {}", id, ex.getMessage());
        throw new DownstreamServiceException("El servicio de reservas no está disponible.");
    }

    @SuppressWarnings("java:S1172")
    public ResponseEntity<Object> mutarFallback(String authHeader, Object body, Exception ex) {
        log.error(LOG_NO_DISPONIBLE, ex.getMessage());
        throw new DownstreamServiceException("El servicio de reservas no está disponible.");
    }

    @SuppressWarnings("java:S1172")
    public ResponseEntity<Object> mutarFallback(String authHeader, Long id, Object body, Exception ex) {
        log.error(LOG_NO_DISPONIBLE, ex.getMessage());
        throw new DownstreamServiceException("El servicio de reservas no está disponible.");
    }

    @SuppressWarnings("java:S1172")
    public ResponseEntity<Object> mutarFallback(String authHeader, Long id, Exception ex) {
        log.error(LOG_NO_DISPONIBLE, ex.getMessage());
        throw new DownstreamServiceException("El servicio de reservas no está disponible.");
    }

    /**
     * Llamada directa a reservas-service para los endpoints que el BFF solo reenvía. A diferencia
     * de los demás métodos, un 4xx llega tal cual al frontend (con su mensaje) y una caída responde
     * 503: nunca una lista vacía, que en la cuenta del huésped se leería como "sin pagos".
     */
    @CircuitBreaker(name = "reservasCB", fallbackMethod = "reenviarFallback")
    public ResponseEntity<Object> reenviar(String authHeader, HttpMethod metodo, String ruta, Object body) {
        return restTemplate.exchange(baseUrl + ruta, metodo,
                new HttpEntity<>(body, UsuariosClient.buildHeaders(authHeader)), Object.class);
    }

    public ResponseEntity<Object> reenviarFallback(String authHeader, HttpMethod metodo, String ruta, Object body,
                                                   Exception ex) {
        if (ex instanceof org.springframework.web.client.HttpClientErrorException e) {
            throw e;
        }
        log.error("reservas-service no disponible ({} {}): {}", metodo, ruta, ex.getMessage());
        throw new DownstreamServiceException("El servicio de reservas no está disponible.");
    }
}
