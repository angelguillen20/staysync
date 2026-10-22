package com.staysync.tenant;

/** Se pidió el hotel de la petición pero no llegó X-Tenant-Id (o el mensaje no traía tenantId). */
public class TenantNotResolvedException extends RuntimeException {
    public TenantNotResolvedException() {
        super("No se pudo determinar el hotel (tenant) de la petición");
    }
}
