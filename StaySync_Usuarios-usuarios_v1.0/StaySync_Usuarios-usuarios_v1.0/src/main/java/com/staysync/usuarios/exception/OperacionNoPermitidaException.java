package com.staysync.usuarios.exception;

/** El solicitante está autenticado pero su rol u hotel no le permiten esta operación (403). */
public class OperacionNoPermitidaException extends RuntimeException {
    public OperacionNoPermitidaException(String message) {
        super(message);
    }
}
