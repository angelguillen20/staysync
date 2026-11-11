package com.staysync.ota.exception;

public class OtaSincronizacionException extends RuntimeException {
    public OtaSincronizacionException(String canal, String detalle) {
        super("Error sincronizando con OTA " + canal + ": " + detalle);
    }
}
