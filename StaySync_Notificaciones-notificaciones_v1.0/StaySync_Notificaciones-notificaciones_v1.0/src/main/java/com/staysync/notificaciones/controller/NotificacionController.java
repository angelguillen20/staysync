package com.staysync.notificaciones.controller;

import com.staysync.notificaciones.messaging.event.ReservaCreadaEvent;
import com.staysync.notificaciones.messaging.event.ReservaEstadoCambiadoEvent;
import com.staysync.notificaciones.messaging.event.UsuarioRegistradoEvent;
import com.staysync.notificaciones.service.NotificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notificaciones", description = "Recibe eventos de otros servicios y envía las notificaciones correspondientes")
public class NotificacionController {

    private final NotificacionService notificacionService;

    @PostMapping("/usuario-registrado")
    @Operation(summary = "Notificar registro de usuario (envía email de bienvenida)")
    public ResponseEntity<Void> usuarioRegistrado(@RequestBody UsuarioRegistradoEvent evento) {
        if (!emailValido(evento.email(), "usuario.registro")) return ResponseEntity.badRequest().build();
        notificacionService.procesarRegistroUsuario(evento);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reserva-creada")
    @Operation(summary = "Notificar reserva creada (envía email de confirmación)")
    public ResponseEntity<Void> reservaCreada(@RequestBody ReservaCreadaEvent evento) {
        if (!emailValido(evento.email(), "reserva.creada")) return ResponseEntity.badRequest().build();
        notificacionService.procesarReservaCreada(evento);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reserva-estado")
    @Operation(summary = "Notificar cambio de estado de una reserva")
    public ResponseEntity<Void> reservaEstadoCambiado(@RequestBody ReservaEstadoCambiadoEvent evento) {
        if (!emailValido(evento.email(), "reserva.estado")) return ResponseEntity.badRequest().build();
        notificacionService.procesarCambioEstado(evento);
        return ResponseEntity.ok().build();
    }

    private boolean emailValido(String email, String origen) {
        if (email == null || email.isBlank()) {
            log.warn("[{}] Evento sin email — descartado", origen);
            return false;
        }
        return true;
    }
}
