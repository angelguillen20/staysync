package com.staysync.tenant;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Hotel de la petición en curso. Lo llena {@link TenantFilter} a partir del header
 * X-Tenant-Id (o un consumidor SQS a partir del mensaje) y lo limpia al terminar.
 *
 * <p>Es un ThreadLocal: no se propaga solo a otros hilos. Para tareas asíncronas usa
 * {@link #wrap(Runnable)} / {@link #wrap(Supplier)}.
 */
public final class TenantContext {

    private static final ThreadLocal<String> ACTUAL = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        if (!TenantHeaders.esValido(tenantId)) {
            throw new IllegalArgumentException("tenantId inválido: " + tenantId);
        }
        ACTUAL.set(tenantId);
        MDC.put(TenantHeaders.MDC_KEY, tenantId);
    }

    public static Optional<String> get() {
        return Optional.ofNullable(ACTUAL.get());
    }

    /** Para código que nunca debe ejecutarse sin hotel (consultas de negocio). */
    public static String require() {
        String tenantId = ACTUAL.get();
        if (tenantId == null) {
            throw new TenantNotResolvedException();
        }
        return tenantId;
    }

    public static void clear() {
        ACTUAL.remove();
        MDC.remove(TenantHeaders.MDC_KEY);
    }

    /** Captura el hotel del hilo actual y lo restaura en el hilo que ejecute la tarea. */
    public static Runnable wrap(Runnable tarea) {
        String capturado = ACTUAL.get();
        return () -> ejecutarCon(capturado, () -> {
            tarea.run();
            return null;
        });
    }

    public static <T> Supplier<T> wrap(Supplier<T> tarea) {
        String capturado = ACTUAL.get();
        return () -> ejecutarCon(capturado, tarea);
    }

    /** Ejecuta la tarea con el hotel indicado y deja el contexto como estaba. */
    public static <T> T ejecutarCon(String tenantId, Supplier<T> tarea) {
        String anterior = ACTUAL.get();
        try {
            if (tenantId == null) {
                clear();
            } else {
                set(tenantId);
            }
            return tarea.get();
        } finally {
            if (anterior == null) {
                clear();
            } else {
                set(anterior);
            }
        }
    }
}
