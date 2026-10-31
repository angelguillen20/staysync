package com.staysync.bff.security;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * La plataforma (SUPER_ADMIN) consulta datos de otro hotel: mientras corre el bloque, las
 * llamadas a los microservicios llevan ese X-Tenant-Id en vez del de su JWT. Ninguna petición
 * puede activarlo: solo lo usa código del BFF que ya verificó el rol.
 */
public final class HotelVista {

    private static final ThreadLocal<String> ACTUAL = new ThreadLocal<>();

    private HotelVista() {
    }

    public static Optional<String> actual() {
        return Optional.ofNullable(ACTUAL.get());
    }

    public static <T> T con(String tenantId, Supplier<T> bloque) {
        ACTUAL.set(tenantId);
        try {
            return bloque.get();
        } finally {
            ACTUAL.remove();
        }
    }
}
