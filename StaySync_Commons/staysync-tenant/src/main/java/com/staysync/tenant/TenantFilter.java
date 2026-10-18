package com.staysync.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee X-Tenant-Id de cada petición y lo deja en {@link TenantContext}.
 *
 * <p>Solo el BFF pone este header (lo saca del JWT firmado), y los microservicios son
 * alcanzables únicamente desde el ALB interno.
 *
 * <p>Con {@code requerido = true} (staysync.tenant.requerido) una petición de negocio sin
 * header responde 400; quedan libres health checks, Swagger y /error.
 */
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);
    private static final List<String> RUTAS_LIBRES =
            List.of("/actuator", "/swagger-ui", "/api-docs", "/v3/api-docs", "/error");

    private final boolean requerido;

    public TenantFilter() {
        this(false);
    }

    public TenantFilter(boolean requerido) {
        this.requerido = requerido;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantId = request.getHeader(TenantHeaders.HEADER);

        if (tenantId != null && !TenantHeaders.esValido(tenantId)) {
            log.warn("X-Tenant-Id con formato inválido en {} {}", request.getMethod(), request.getRequestURI());
            rechazar(response, "X-Tenant-Id inválido");
            return;
        }
        if (tenantId == null && requerido && !esRutaLibre(request.getRequestURI())) {
            log.warn("Petición sin X-Tenant-Id en {} {}", request.getMethod(), request.getRequestURI());
            rechazar(response, "Falta el hotel de la petición (X-Tenant-Id)");
            return;
        }

        try {
            if (tenantId != null) {
                TenantContext.set(tenantId);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static boolean esRutaLibre(String uri) {
        return RUTAS_LIBRES.stream().anyMatch(uri::startsWith);
    }

    private static void rechazar(HttpServletResponse response, String mensaje) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":400,\"message\":\"" + mensaje + "\"}");
    }
}
