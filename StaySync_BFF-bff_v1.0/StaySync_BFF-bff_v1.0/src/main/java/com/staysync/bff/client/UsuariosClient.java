package com.staysync.bff.client;

import com.staysync.bff.config.ServicesProperties;
import com.staysync.bff.exception.DownstreamServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class UsuariosClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public UsuariosClient(RestTemplate restTemplate, ServicesProperties services) {
        this.restTemplate = restTemplate;
        this.baseUrl = services.usuarios().url();
    }

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "loginFallback")
    public ResponseEntity<Object> login(Object body) {
        return restTemplate.postForEntity(baseUrl + "/api/v1/auth/login", body, Object.class);
    }

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "passthruFallback")
    public ResponseEntity<Object> registro(Object body) {
        return restTemplate.postForEntity(baseUrl + "/api/v1/auth/registro", body, Object.class);
    }

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "passthruFallback")
    public ResponseEntity<Object> refresh(Object body) {
        return restTemplate.postForEntity(baseUrl + "/api/v1/auth/refresh", body, Object.class);
    }

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "authFallback")
    public ResponseEntity<Object> logout(String authHeader, Object body) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(body, buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "authFallback")
    public ResponseEntity<Object> getPerfilPropio(String authHeader) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/usuarios/perfil",
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "authFallback")
    public ResponseEntity<Object> updatePerfilPropio(String authHeader, Object body) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/usuarios/perfil",
                HttpMethod.PUT,
                new HttpEntity<>(body, buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "authFallback")
    public ResponseEntity<Object> getPerfil(String authHeader, Long id) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/usuarios/" + id,
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "authFallback")
    public ResponseEntity<Object> updatePerfil(String authHeader, Long id, Object body) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/usuarios/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(body, buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "authFallback")
    public ResponseEntity<Object> listarUsuarios(String authHeader) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/usuarios",
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "buscarFallback")
    public ResponseEntity<Object> buscarUsuarios(String authHeader, String q, String rol) {
        String url = UriComponentsBuilder
                .fromUriString(baseUrl + "/api/v1/usuarios/buscar")
                .queryParam("q",   q   != null ? q   : "")
                .queryParam("rol", rol != null ? rol : "")
                .toUriString();
        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(buildHeaders(authHeader)), Object.class);
    }

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "listarHuespedesFallback")
    public ResponseEntity<Object> listarHuespedes(String authHeader) {
        return restTemplate.exchange(
                baseUrl + "/api/v1/usuarios/huespedes",
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders(authHeader)),
                Object.class);
    }

    @CircuitBreaker(name = "usuariosCB", fallbackMethod = "desactivarFallback")
    public void desactivar(String authHeader, Long id) {
        restTemplate.exchange(
                baseUrl + "/api/v1/usuarios/" + id,
                HttpMethod.DELETE,
                new HttpEntity<>(buildHeaders(authHeader)),
                Void.class);
    }

    public void desactivarFallback(String authHeader, Long id, Exception ex) {
        log.error("usuarios-service no disponible al desactivar id={}: {}", id, ex.getMessage());
        throw new DownstreamServiceException("El servicio de usuarios no está disponible.");
    }

    public ResponseEntity<Object> buscarFallback(String authHeader, String q, String rol, Exception ex) {
        log.warn("usuarios-service buscar no disponible: {}", ex.getMessage());
        return ResponseEntity.ok(Collections.emptyList());
    }

    public ResponseEntity<Object> listarHuespedesFallback(String authHeader, Exception ex) {
        log.warn("usuarios-service huespedes no disponible: {}", ex.getMessage());
        return ResponseEntity.ok(Collections.emptyList());
    }

    public ResponseEntity<Object> loginFallback(Object body, Exception ex) {
        log.error("usuarios-service no disponible en login: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de autenticación no está disponible. Intente nuevamente.");
    }

    public ResponseEntity<Object> passthruFallback(Object body, Exception ex) {
        log.error("usuarios-service no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de usuarios no está disponible.");
    }

    public ResponseEntity<Object> authFallback(String authHeader, Exception ex) {
        log.error("usuarios-service no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de usuarios no está disponible.");
    }

    public ResponseEntity<Object> authFallback(String authHeader, Object body, Exception ex) {
        log.error("usuarios-service no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de usuarios no está disponible.");
    }

    public ResponseEntity<Object> authFallback(String authHeader, Long id, Exception ex) {
        log.error("usuarios-service no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de usuarios no está disponible.");
    }

    public ResponseEntity<Object> authFallback(String authHeader, Long id, Object body, Exception ex) {
        log.error("usuarios-service no disponible: {}", ex.getMessage());
        throw new DownstreamServiceException("El servicio de usuarios no está disponible.");
    }

    static HttpHeaders buildHeaders(String authHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authHeader != null) headers.set("Authorization", authHeader);
        return headers;
    }
}
