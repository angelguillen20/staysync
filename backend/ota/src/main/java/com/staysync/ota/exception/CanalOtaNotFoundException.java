package com.staysync.ota.exception;

public class CanalOtaNotFoundException extends RuntimeException {
    public CanalOtaNotFoundException(Long id) { super("Canal OTA no encontrado con ID: " + id); }
    public CanalOtaNotFoundException(String codigo) { super("Canal OTA no encontrado con código: " + codigo); }
}
