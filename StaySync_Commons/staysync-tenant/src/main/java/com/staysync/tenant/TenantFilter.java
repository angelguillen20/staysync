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

/**
 * Lee X-Tenant-Id de cada petición y lo deja en {@link TenantContext}.
 *
 * <p>Solo el BFF pone este header (lo saca del JWT firmado), y los microservicios son
 * alcanzables únicamente desde el ALB interno. Sin header la petición sigue sin hotel
 * (health checks, endpoints públicos); el código de negocio usa TenantContext.require().
 */
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantId = request.getHeader(TenantHeaders.HEADER);

        if (tenantId != null && !TenantHeaders.esValido(tenantId)) {
            log.warn("X-Tenant-Id con formato inválido en {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":400,\"message\":\"X-Tenant-Id inválido\"}");
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
}
