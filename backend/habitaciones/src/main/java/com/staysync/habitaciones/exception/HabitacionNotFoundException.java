package com.staysync.habitaciones.exception;

public class HabitacionNotFoundException extends RuntimeException {
    public HabitacionNotFoundException(Long id) {
        super("Habitación no encontrada con ID: " + id);
    }
    public HabitacionNotFoundException(String numero) {
        super("Habitación no encontrada con número: " + numero);
    }
}
