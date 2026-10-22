package com.staysync.tenant;

import java.util.regex.Pattern;

/** Nombres compartidos entre el BFF, los microservicios y los mensajes SQS. */
public final class TenantHeaders {

    /** Header HTTP con el que el BFF informa el hotel a los microservicios internos. */
    public static final String HEADER = "X-Tenant-Id";

    /** Claim del JWT emitido por MS Usuarios. */
    public static final String CLAIM = "tenantId";

    /** Clave en el MDC de logback: permite filtrar los logs por hotel. */
    public static final String MDC_KEY = "tenantId";

    /** Mismo formato que hoteles.tenant_id: minúsculas, dígitos y guiones, 2–64 caracteres. */
    public static final Pattern FORMATO = Pattern.compile("^[a-z0-9][a-z0-9-]{1,63}$");

    private TenantHeaders() {
    }

    public static boolean esValido(String tenantId) {
        return tenantId != null && FORMATO.matcher(tenantId).matches();
    }
}
