package com.staysync.bff.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
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
        } catch (Exception ignored) {
            // El cuerpo no es JSON: se devuelve tal cual
        }
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

    // @PreAuthorize denegado: sin esto caía en el handler genérico y respondía 500
    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<Map<String, Object>> handleAccesoDenegado(AccesoDenegadoException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // Reglas de negocio del BFF (p. ej. pagar una reserva que ya no está PENDIENTE)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return build(status, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase());
    }

    // Spring corta la subida antes de llegar al controlador si supera spring.servlet.multipart
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleArchivoGrande(Exception ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "La foto debe pesar hasta 5 MB.");
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleSinArchivo(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "Falta el archivo de la foto.");
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AuthorizationDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "No tienes permisos para esta operación.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Error inesperado en BFF: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno. Contacte al administrador.");
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", LocalDateTime.now(ZoneId.systemDefault()).toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        ));
    }
}
