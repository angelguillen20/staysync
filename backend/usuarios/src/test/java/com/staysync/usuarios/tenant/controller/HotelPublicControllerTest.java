package com.staysync.usuarios.tenant.controller;

import com.staysync.usuarios.config.SecurityConfig;
import com.staysync.usuarios.security.JwtAuthFilter;
import com.staysync.usuarios.service.JwtService;
import com.staysync.usuarios.tenant.dto.HotelResponse;
import com.staysync.usuarios.tenant.exception.HotelNotFoundException;
import com.staysync.usuarios.tenant.service.HotelService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HotelPublicController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
@DisplayName("HotelPublicController - Tests de Integración Web")
class HotelPublicControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private HotelService hotelService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /public/hoteles/{slug}/theme - debe responder sin token")
    void debeResponderSinAutenticacion() throws Exception {
        when(hotelService.obtenerPorSlug("hotel-andes")).thenReturn(HotelResponse.builder()
                .tenantId("hotel-andes")
                .slug("hotel-andes")
                .nombre("Hotel Andes")
                .theme(Map.of("primary", "#7A2E1F"))
                .build());

        mockMvc.perform(get("/api/v1/public/hoteles/hotel-andes/theme"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=300")))
                .andExpect(jsonPath("$.nombre").value("Hotel Andes"))
                .andExpect(jsonPath("$.theme.primary").value("#7A2E1F"));
    }

    @Test
    @DisplayName("GET /public/hoteles/{slug}/theme - slug inexistente debe dar 404")
    void slugInexistenteDa404() throws Exception {
        when(hotelService.obtenerPorSlug("no-existe")).thenThrow(new HotelNotFoundException("no-existe"));

        mockMvc.perform(get("/api/v1/public/hoteles/no-existe/theme"))
                .andExpect(status().isNotFound());
    }
}
