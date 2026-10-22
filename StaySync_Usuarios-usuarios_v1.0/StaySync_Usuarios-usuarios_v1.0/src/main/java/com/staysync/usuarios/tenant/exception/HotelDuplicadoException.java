package com.staysync.usuarios.tenant.exception;

public class HotelDuplicadoException extends RuntimeException {
    public HotelDuplicadoException(String slug) {
        super("Ya existe un hotel con el portal: " + slug);
    }
}
