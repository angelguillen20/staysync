package com.staysync.ota.exception;

/** Webhook sin firma, con firma incorrecta, caducado o de un canal sin secreto configurado. */
public class FirmaWebhookInvalidaException extends RuntimeException {

    public FirmaWebhookInvalidaException() {
        super("Firma del webhook inválida");
    }
}
