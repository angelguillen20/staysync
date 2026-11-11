package com.staysync.habitaciones.exception;

public class HabitacionNumeroExistenteException extends RuntimeException {
    public HabitacionNumeroExistenteException(String numero) {
        super("Ya existe una habitación con el número: " + numero);
    }
}
