package com.staysync.bff.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Cabecera requerida faltante: {}", ex.getHeaderName());
        return build(HttpStatus.BAD_REQUEST,
                "Cabecera requerida faltante: '" + ex.getHeaderName() + "'");
    }

    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<Map<String, Object>> handleDownstream(DownstreamServiceException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    // Propaga errores 4xx del downstream extrayendo solo el campo "message"
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<Map<String, Object>> handleClientError(HttpClientErrorException ex) {
        log.warn("Error del downstream (4xx): {} - {}", ex.getStatusCode(), ex.getMessage());
        return build(HttpStatus.valueOf(ex.getStatusCode().value()), extractMessage(ex.getResponseBodyAsString()));
    }

    private String extractMessage(String body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = new ObjectMapper().readValue(body, Map.class);
            Object msg = parsed.get("message");
            if (msg != null) return msg.toString();
        } catch (Exception ignored) {}
        return body;
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<Map<String, Object>> handleServerError(HttpServerErrorException ex) {
        log.error("Error del downstream (5xx): {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "Error interno en un servicio dependiente.");
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> handleTimeout(ResourceAccessException ex) {
        log.error("Timeout conectando al servicio: {}", ex.getMessage());
        return build(HttpStatus.GATEWAY_TIMEOUT, "El servicio no respondió en el tiempo esperado.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Error inesperado en BFF: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno. Contacte al administrador.");
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        ));
    }
}
