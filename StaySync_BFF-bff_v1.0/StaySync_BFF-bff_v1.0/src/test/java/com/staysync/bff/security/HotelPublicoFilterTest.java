package com.staysync.bff.security;

import com.staysync.bff.client.UsuariosClient;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("HotelPublicoFilter - portal del hotel en rutas públicas")
class HotelPublicoFilterTest {

    private final UsuariosClient usuariosClient = mock(UsuariosClient.class);
    private final HotelPublicoFilter filtro = new HotelPublicoFilter(usuariosClient);
    private final FilterChain chain = mock(FilterChain.class);

    private MockHttpServletRequest pedido(String hotel) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/bff/habitaciones/disponibles");
        if (hotel != null) r.addHeader(HotelPublicoFilter.HEADER, hotel);
        return r;
    }

    @Test
    @DisplayName("Traduce el slug a tenantId y lo cachea")
    void resuelveYCachea() throws Exception {
        when(usuariosClient.obtenerThemeHotel("hotel-andes"))
                .thenReturn(ResponseEntity.ok(Map.of("tenantId", "hotel-andes", "nombre", "Hotel Andes")));

        MockHttpServletRequest primero = pedido("hotel-andes");
        filtro.doFilter(primero, new MockHttpServletResponse(), chain);
        MockHttpServletRequest segundo = pedido("hotel-andes");
        filtro.doFilter(segundo, new MockHttpServletResponse(), chain);

        assertThat(primero.getAttribute(HotelPublicoFilter.ATRIBUTO)).isEqualTo("hotel-andes");
        assertThat(segundo.getAttribute(HotelPublicoFilter.ATRIBUTO)).isEqualTo("hotel-andes");
        verify(usuariosClient, times(1)).obtenerThemeHotel("hotel-andes");
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Un slug con formato inválido se ignora sin consultar a usuarios-service")
    void slugInvalido() throws Exception {
        MockHttpServletRequest r = pedido("Hotel Andes; DROP");
        filtro.doFilter(r, new MockHttpServletResponse(), chain);

        assertThat(r.getAttribute(HotelPublicoFilter.ATRIBUTO)).isNull();
        verifyNoInteractions(usuariosClient);
    }

    @Test
    @DisplayName("Un hotel inexistente deja la petición sin hotel (no la corta)")
    void hotelInexistente() throws Exception {
        when(usuariosClient.obtenerThemeHotel("no-existe")).thenThrow(new RuntimeException("404"));

        MockHttpServletRequest r = pedido("no-existe");
        filtro.doFilter(r, new MockHttpServletResponse(), chain);

        assertThat(r.getAttribute(HotelPublicoFilter.ATRIBUTO)).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("Sin header X-Hotel no hace nada")
    void sinHeader() throws Exception {
        filtro.doFilter(pedido(null), new MockHttpServletResponse(), chain);
        verifyNoInteractions(usuariosClient);
    }
}
