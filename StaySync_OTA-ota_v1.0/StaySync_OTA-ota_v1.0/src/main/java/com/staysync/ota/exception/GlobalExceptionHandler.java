package com.staysync.ota.exception;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(CanalOtaNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(CanalOtaNotFoundException ex, WebRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(OtaSincronizacionException.class)
    public ResponseEntity<ErrorResponse> handleSincronizacion(OtaSincronizacionException ex, WebRequest req) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, WebRequest req) {
        log.error("Error en OTA service: ", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", req);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus s, String m, WebRequest r) {
        return ResponseEntity.status(s).body(ErrorResponse.builder()
                .timestamp(LocalDateTime.now()).status(s.value()).error(s.getReasonPhrase())
                .message(m).path(r.getDescription(false).replace("uri=","")).build());
    }

    @Data @Builder
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private String path;
    }
}
