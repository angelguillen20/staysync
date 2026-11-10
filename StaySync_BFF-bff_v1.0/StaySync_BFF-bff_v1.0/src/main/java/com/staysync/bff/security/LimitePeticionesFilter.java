package com.staysync.bff.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita las peticiones por IP en ventanas de un minuto, antes de validar el JWT:
 * <ul>
 *   <li>login, registro y login con Google: pocas por minuto, contra fuerza bruta y relleno
 *       de credenciales (el bloqueo por cuenta lo aplica además usuarios-service);</li>
 *   <li>resto de la API: un tope amplio contra abuso o scripts descontrolados.</li>
 * </ul>
 * Los contadores viven en memoria de cada instancia: con N instancias el límite efectivo es
 * hasta N veces mayor. La IP real llega tras el balanceador con server.forward-headers-strategy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@Slf4j
public class LimitePeticionesFilter extends OncePerRequestFilter {

    static final Duration VENTANA = Duration.ofMinutes(1);
    private static final Set<String> RUTAS_AUTH = Set.of("/bff/auth/login", "/bff/auth/registro", "/bff/auth/google");
    private static final int MAX_CLAVES = 10_000;

    private final int maxAuth;
    private final int maxApi;
    private final Clock clock;
    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    @Autowired
    public LimitePeticionesFilter(@Value("${seguridad.limites.auth-por-minuto:10}") int maxAuth,
                                  @Value("${seguridad.limites.api-por-minuto:600}") int maxApi) {
        this(maxAuth, maxApi, Clock.systemUTC());
    }

    LimitePeticionesFilter(int maxAuth, int maxApi, Clock clock) {
        this.maxAuth = maxAuth;
        this.maxApi = maxApi;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/bff/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        boolean esAuth = "POST".equals(request.getMethod()) && RUTAS_AUTH.contains(request.getRequestURI());
        String ip = request.getRemoteAddr();
        long ahora = clock.millis();

        long esperaMs = esAuth ? registrar("auth:" + ip, maxAuth, ahora) : registrar("api:" + ip, maxApi, ahora);
        if (esperaMs > 0) {
            log.warn("Límite de peticiones superado: {} {} desde {}", request.getMethod(), request.getRequestURI(), ip);
            rechazar(response, esperaMs, esAuth);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Cuenta la petición; devuelve 0 si está dentro del límite o los ms hasta que se libere. */
    long registrar(String clave, int max, long ahora) {
        if (ventanas.size() > MAX_CLAVES) {
            ventanas.values().removeIf(v -> v.vencida(ahora));
        }
        Ventana ventana = ventanas.compute(clave, (k, v) -> v == null || v.vencida(ahora) ? new Ventana(ahora) : v);
        int cuenta = ventana.sumar();
        return cuenta <= max ? 0 : ventana.inicio + VENTANA.toMillis() - ahora;
    }

    private static void rechazar(HttpServletResponse response, long esperaMs, boolean esAuth) throws IOException {
        long segundos = Math.max(1, (esperaMs + 999) / 1000);
        String mensaje = esAuth
                ? "Demasiados intentos de inicio de sesión. Espera " + segundos + " segundos e inténtalo de nuevo."
                : "Demasiadas solicitudes. Espera unos segundos e inténtalo de nuevo.";
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(segundos));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":429,\"message\":\"" + mensaje + "\"}");
    }

    private static final class Ventana {
        final long inicio;
        private int cuenta;

        Ventana(long inicio) {
            this.inicio = inicio;
        }

        synchronized int sumar() {
            return ++cuenta;
        }

        boolean vencida(long ahora) {
            return ahora - inicio >= VENTANA.toMillis();
        }
    }
}
