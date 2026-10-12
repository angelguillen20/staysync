package com.staysync.tenant;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TenantFilter")
class TenantFilterTest {

    private final TenantFilter filter = new TenantFilter();

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Con X-Tenant-Id válido el controlador ve el hotel y luego se limpia")
    void headerValido() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reservas");
        request.addHeader(TenantHeaders.HEADER, "hotel-andes");
        AtomicReference<String> visto = new AtomicReference<>();
        FilterChain chain = (req, res) -> visto.set(TenantContext.require());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(visto.get()).isEqualTo("hotel-andes");
        assertThat(TenantContext.get()).isEmpty();
    }

    @Test
    @DisplayName("Sin header la petición sigue sin hotel (health checks, públicos)")
    void sinHeader() throws Exception {
        AtomicReference<Boolean> llamado = new AtomicReference<>(false);
        FilterChain chain = (req, res) -> {
            llamado.set(true);
            assertThat(TenantContext.get()).isEmpty();
        };

        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), new MockHttpServletResponse(), chain);

        assertThat(llamado.get()).isTrue();
    }

    @Test
    @DisplayName("Header con formato inválido responde 400 y no llega al controlador")
    void headerInvalido() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reservas");
        request.addHeader(TenantHeaders.HEADER, "' OR 1=1 --");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> fail("no debió llegar al controlador");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("X-Tenant-Id");
    }

    @Test
    @DisplayName("Si el controlador falla igual se limpia el contexto")
    void limpiaAunqueFalle() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader(TenantHeaders.HEADER, "hotel-andes");
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
                .isInstanceOf(IllegalStateException.class);
        assertThat(TenantContext.get()).isEmpty();
    }
}
