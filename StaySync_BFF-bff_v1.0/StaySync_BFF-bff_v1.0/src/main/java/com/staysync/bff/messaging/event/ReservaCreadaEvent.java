package com.staysync.bff.messaging.event;

public record ReservaCreadaEvent(
        Long   reservaId,
        Long   usuarioId,
        String email,
        String nombreUsuario,
        String codigo,
        String habitacion,
        String fechaEntrada,
        String fechaSalida,
        Double precioTotal
) {}
