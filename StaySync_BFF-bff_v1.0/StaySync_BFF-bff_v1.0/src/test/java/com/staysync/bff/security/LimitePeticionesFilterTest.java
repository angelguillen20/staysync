package com.staysync.bff.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("LimitePeticionesFilter - límite de peticiones por IP")
class LimitePeticionesFilterTest {

    private final RelojMovil reloj = new RelojMovil();
    private final LimitePeticionesFilter filtro = new LimitePeticionesFilter(3, 5, reloj);

    private MockHttpServletResponse peticion(String metodo, String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filtro.doFilter(req, res, mock(FilterChain.class));
        return res;
    }

    @Test
    @DisplayName("Al superar el límite de login responde 429 con Retry-After y un mensaje para el usuario")
    void limitaLogin() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(peticion("POST", "/bff/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse bloqueada = peticion("POST", "/bff/auth/login", "1.1.1.1");

        assertThat(bloqueada.getStatus()).isEqualTo(429);
        assertThat(bloqueada.getHeader("Retry-After")).isEqualTo("60");
        assertThat(bloqueada.getContentAsString()).contains("Demasiados intentos de inicio de sesión");
    }

    @Test
    @DisplayName("El límite es por IP: otra IP sigue pudiendo iniciar sesión")
    void limitePorIp() throws Exception {
        for (int i = 0; i < 4; i++) {
            peticion("POST", "/bff/auth/login", "1.1.1.1");
        }
        assertThat(peticion("POST", "/bff/auth/login", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Login, registro y Google comparten el límite estricto")
    void rutasAuthCompartenLimite() throws Exception {
        peticion("POST", "/bff/auth/login", "1.1.1.1");
        peticion("POST", "/bff/auth/registro", "1.1.1.1");
        peticion("POST", "/bff/auth/google", "1.1.1.1");

        assertThat(peticion("POST", "/bff/auth/registro", "1.1.1.1").getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("Pasado el minuto, la ventana se reinicia")
    void ventanaSeReinicia() throws Exception {
        for (int i = 0; i < 4; i++) {
            peticion("POST", "/bff/auth/login", "1.1.1.1");
        }
        reloj.avanzar(Duration.ofSeconds(60));

        assertThat(peticion("POST", "/bff/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("El resto de la API tiene su propio límite, más amplio, y no consume el de login")
    void limiteGeneralDeLaApi() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(peticion("GET", "/bff/reservas", "1.1.1.1").getStatus()).isEqualTo(200);
        }
        assertThat(peticion("GET", "/bff/reservas", "1.1.1.1").getStatus()).isEqualTo(429);
        assertThat(peticion("POST", "/bff/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("No limita rutas fuera de /bff (health checks, Swagger)")
    void noLimitaHealth() throws Exception {
        FilterChain cadena = mock(FilterChain.class);
        for (int i = 0; i < 20; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
            filtro.doFilter(req, new MockHttpServletResponse(), cadena);
        }
        verify(cadena, times(20)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** Reloj que solo avanza cuando el test lo pide. */
    static class RelojMovil extends Clock {
        private Instant ahora = Instant.parse("2026-11-04T12:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }
}
