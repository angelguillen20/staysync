package com.staysync.notificaciones.messaging.event;

public record UsuarioRegistradoEvent(
        Long   usuarioId,
        String nombre,
        String email
) {}
