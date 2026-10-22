package com.staysync.bff.exception;

/** El usuario está autenticado pero el recurso no es suyo (p. ej. la reserva de otro huésped). */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String message) {
        super(message);
    }
}
