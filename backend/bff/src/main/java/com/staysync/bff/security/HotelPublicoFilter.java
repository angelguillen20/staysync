package com.staysync.bff.security;

import com.staysync.bff.client.UsuariosClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Para rutas públicas (sin JWT), p. ej. buscar habitaciones antes de iniciar sesión: el
 * frontend indica el portal con el header X-Hotel (slug) y aquí se traduce al tenantId
 * consultando la marca pública del hotel. Con sesión iniciada manda siempre el JWT.
 */
@Component
@Slf4j
public class HotelPublicoFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Hotel";
    public static final String ATRIBUTO = "staysync.tenantPublico";
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{1,63}$");

    private final UsuariosClient usuariosClient;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public HotelPublicoFilter(UsuariosClient usuariosClient) {
        this.usuariosClient = usuariosClient;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String slug = request.getHeader(HEADER);
        if (slug != null && SLUG.matcher(slug).matches()) {
            String tenantId = resolver(slug);
            if (tenantId != null) {
                request.setAttribute(ATRIBUTO, tenantId);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolver(String slug) {
        String enCache = cache.get(slug);
        if (enCache != null) return enCache;
        try {
            ResponseEntity<Object> marca = usuariosClient.obtenerThemeHotel(slug);
            if (marca.getBody() instanceof Map<?, ?> datos && datos.get("tenantId") != null) {
                String tenantId = datos.get("tenantId").toString();
                cache.put(slug, tenantId);
                return tenantId;
            }
        } catch (Exception e) {
            log.debug("Hotel '{}' no resuelto: {}", slug, e.getMessage());
        }
        return null;
    }
}
