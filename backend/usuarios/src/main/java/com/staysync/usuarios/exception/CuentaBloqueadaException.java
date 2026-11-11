package com.staysync.usuarios.exception;

/** Login rechazado porque la cuenta está bloqueada temporalmente por intentos fallidos. */
public class CuentaBloqueadaException extends RuntimeException {

    public CuentaBloqueadaException(long minutos) {
        super("Cuenta bloqueada temporalmente por varios intentos fallidos. Inténtalo de nuevo en "
                + minutos + (minutos == 1 ? " minuto." : " minutos."));
    }
}
