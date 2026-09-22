package com.staysync.bff.messaging.event;

public record ReservaEstadoCambiadoEvent(
        Long   reservaId,
        Long   usuarioId,
        String email,
        String nombreUsuario,
        String codigo,
        String habitacion,
        String estadoNuevo
) {}
