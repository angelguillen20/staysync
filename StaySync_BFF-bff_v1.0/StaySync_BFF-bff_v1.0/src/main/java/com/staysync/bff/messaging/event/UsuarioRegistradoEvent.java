package com.staysync.bff.messaging.event;

public record UsuarioRegistradoEvent(
        Long   usuarioId,
        String nombre,
        String email
) {}
