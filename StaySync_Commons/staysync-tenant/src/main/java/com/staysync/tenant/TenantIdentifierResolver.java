package com.staysync.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Le dice a Hibernate el hotel de la sesión actual. Con él, toda entidad que tenga un
 * campo {@code @TenantId}:
 * <ul>
 *   <li>se filtra sola en cada consulta (findAll, findById, JPQL, Specifications);</li>
 *   <li>recibe el tenant_id automáticamente al insertarse.</li>
 * </ul>
 * Sin hotel en contexto se usa {@link #SIN_HOTEL}, que no coincide con ningún dato: una
 * petición que se "olvida" del hotel no ve nada en vez de ver todo.
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    /** Valor que no corresponde a ningún hotel (no pasa TenantHeaders.FORMATO). */
    public static final String SIN_HOTEL = "_sin_hotel_";

    /** Modo sistema: Hibernate no aplica el filtro de hotel. */
    public static final String RAIZ = "_sistema_";

    @Override
    public String resolveCurrentTenantIdentifier() {
        if (TenantContext.esSistema()) {
            return RAIZ;
        }
        return TenantContext.get().orElse(SIN_HOTEL);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(String tenantId) {
        return RAIZ.equals(tenantId);
    }
}
