package com.staysync.servicios.exception;

public class ReservaNoActivaException extends RuntimeException {
    public ReservaNoActivaException(Long reservaId) {
        super("La reserva " + reservaId + " no está activa. Solo se pueden solicitar servicios en reservas CONFIRMADAS o en CHECKIN.");
    }
}
