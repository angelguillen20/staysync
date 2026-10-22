package com.staysync.tenant;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Hotel de la petición en curso. Lo llena {@link TenantFilter} a partir del header
 * X-Tenant-Id (o un consumidor SQS a partir del mensaje) y lo limpia al terminar.
 *
 * <p>Con Hibernate, {@link TenantIdentifierResolver} usa este valor para filtrar
 * automáticamente toda consulta sobre entidades con {@code @TenantId}.
 *
 * <p>Es un ThreadLocal: no se propaga solo a otros hilos. Para tareas asíncronas usa
 * {@link #wrap(Runnable)} / {@link #wrap(Supplier)}.
 */
public final class TenantContext {

    private static final ThreadLocal<String> ACTUAL = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SISTEMA = new ThreadLocal<>();

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

    /** true dentro de {@link #ejecutarComoSistema}: las consultas ven todos los hoteles. */
    public static boolean esSistema() {
        return Boolean.TRUE.equals(SISTEMA.get());
    }

    /**
     * Ejecuta la tarea sin filtro de hotel. Solo para procesos internos que recorren todos
     * los hoteles (tareas programadas, migraciones). Debe llamarse fuera de una transacción
     * abierta: la sesión de Hibernate fija su hotel al abrirse.
     */
    public static <T> T ejecutarComoSistema(Supplier<T> tarea) {
        Boolean anterior = SISTEMA.get();
        SISTEMA.set(Boolean.TRUE);
        try {
            return tarea.get();
        } finally {
            if (anterior == null) {
                SISTEMA.remove();
            } else {
                SISTEMA.set(anterior);
            }
        }
    }

    public static void ejecutarComoSistema(Runnable tarea) {
        ejecutarComoSistema(() -> {
            tarea.run();
            return null;
        });
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

    public static void ejecutarCon(String tenantId, Runnable tarea) {
        ejecutarCon(tenantId, () -> {
            tarea.run();
            return null;
        });
    }
}
