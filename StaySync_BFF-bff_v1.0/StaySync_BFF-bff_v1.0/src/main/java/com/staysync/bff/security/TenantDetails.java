package com.staysync.bff.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Hotel del usuario autenticado, guardado en Authentication.getDetails() por JwtAuthFilter.
 * Sale siempre del JWT firmado: nunca de un header, query param o body del cliente.
 */
public record TenantDetails(String tenantId) {

    /** Claim del JWT emitido por usuarios-service. */
    public static final String CLAIM = "tenantId";

    /** Header con el que el BFF informa el hotel a los microservicios internos. */
    public static final String HEADER = "X-Tenant-Id";

    public static Optional<String> actual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof TenantDetails details) {
            return Optional.ofNullable(details.tenantId());
        }
        return Optional.empty();
    }
}
