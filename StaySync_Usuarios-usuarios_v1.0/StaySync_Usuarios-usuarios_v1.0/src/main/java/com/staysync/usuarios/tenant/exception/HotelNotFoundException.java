package com.staysync.usuarios.tenant.exception;

public class HotelNotFoundException extends RuntimeException {
    public HotelNotFoundException(String identificador) {
        super("Hotel no encontrado: " + identificador);
    }
}
