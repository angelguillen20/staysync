package com.staysync.servicios.exception;

public class ServicioNotFoundException extends RuntimeException {
    public ServicioNotFoundException(Long id) { super("Servicio no encontrado con ID: " + id); }
}
