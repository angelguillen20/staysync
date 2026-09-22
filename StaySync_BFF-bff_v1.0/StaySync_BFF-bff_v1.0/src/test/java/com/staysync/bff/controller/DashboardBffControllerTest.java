package com.staysync.bff.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.bff.client.HabitacionesClient;
import com.staysync.bff.client.ReservasClient;
import com.staysync.bff.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardBffController.class)
@DisplayName("DashboardBffController - Tests")
class DashboardBffControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean ReservasClient reservasClient;
    @MockitoBean HabitacionesClient habitacionesClient;
    @MockitoBean JwtService jwtService;

    @Test
    @DisplayName("GET /bff/dashboard - debe retornar datos agregados correctamente")
    @WithMockUser(roles = "ADMIN")
    void debeRetornarDashboardAgregado() throws Exception {
        List<Map<String, Object>> reservas = List.of(
                Map.of("id", 1, "estado", "CONFIRMADA", "codigoReserva", "RES-001"),
                Map.of("id", 2, "estado", "PENDIENTE",  "codigoReserva", "RES-002"),
                Map.of("id", 3, "estado", "CHECKIN",    "codigoReserva", "RES-003")
        );
        List<Map<String, Object>> habitaciones = List.of(
                Map.of("id", 1, "estado", "DISPONIBLE"),
                Map.of("id", 2, "estado", "OCUPADA"),
                Map.of("id", 3, "estado", "MANTENIMIENTO")
        );

        when(reservasClient.listar(anyString())).thenReturn(ResponseEntity.ok(reservas));
        when(habitacionesClient.listarTodas(anyString())).thenReturn(ResponseEntity.ok(habitaciones));
        when(habitacionesClient.listarDisponibles(anyString()))
                .thenReturn(ResponseEntity.ok(List.of(habitaciones.get(0))));

        mockMvc.perform(get("/bff/dashboard")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservas.total").value(3))
                .andExpect(jsonPath("$.reservas.confirmadas").value(1))
                .andExpect(jsonPath("$.reservas.pendientes").value(1))
                .andExpect(jsonPath("$.reservas.enCheckin").value(1))
                .andExpect(jsonPath("$.habitaciones.totalHabitaciones").value(3))
                .andExpect(jsonPath("$.habitaciones.disponibles").value(1))
                .andExpect(jsonPath("$.habitaciones.ocupadas").value(1));
    }

    @Test
    @DisplayName("GET /bff/dashboard - debe funcionar con servicios vacíos (fallback)")
    @WithMockUser(roles = "RECEPCIONISTA")
    void debeRetornarDashboardConDatosVacios() throws Exception {
        when(reservasClient.listar(anyString())).thenReturn(ResponseEntity.ok(List.of()));
        when(habitacionesClient.listarTodas(anyString())).thenReturn(ResponseEntity.ok(List.of()));
        when(habitacionesClient.listarDisponibles(anyString())).thenReturn(ResponseEntity.ok(List.of()));

        mockMvc.perform(get("/bff/dashboard")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservas.total").value(0))
                .andExpect(jsonPath("$.habitaciones.totalHabitaciones").value(0));
    }

    @Test
    @DisplayName("GET /bff/dashboard - sin autenticación debe retornar 403")
    void sinAutenticacionDebe403() throws Exception {
        mockMvc.perform(get("/bff/dashboard"))
                .andExpect(status().isForbidden());
    }
}
