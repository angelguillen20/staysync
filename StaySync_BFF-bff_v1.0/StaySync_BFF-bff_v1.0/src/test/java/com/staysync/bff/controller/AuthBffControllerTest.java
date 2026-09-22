package com.staysync.bff.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.bff.client.UsuariosClient;
import com.staysync.bff.messaging.NotificacionEventPublisher;
import com.staysync.bff.security.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthBffController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthBffController - Tests")
class AuthBffControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean UsuariosClient usuariosClient;
    @MockitoBean NotificacionEventPublisher notificacionPublisher;
    @MockitoBean JwtService jwtService;

    @Test
    @DisplayName("POST /bff/auth/login - debe delegar al usuarios-service y retornar tokens")
    void debeHacerProxyDeLogin() throws Exception {
        Map<String, Object> respuesta = Map.of("accessToken", "jwt-abc", "refreshToken", "ref-xyz");
        when(usuariosClient.login(any())).thenReturn(ResponseEntity.ok(respuesta));

        mockMvc.perform(post("/bff/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@hotel.com\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-abc"))
                .andExpect(jsonPath("$.refreshToken").value("ref-xyz"));
    }

    @Test
    @DisplayName("POST /bff/auth/registro - debe publicar evento cuando el registro es exitoso")
    void debePublicarEventoDeRegistro() throws Exception {
        Map<String, Object> usuario = Map.of("id", 5, "nombre", "Ana López", "email", "ana@hotel.com");
        when(usuariosClient.registro(any())).thenReturn(ResponseEntity.ok(usuario));

        mockMvc.perform(post("/bff/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana López\",\"email\":\"ana@hotel.com\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk());

        verify(notificacionPublisher).publishUsuarioRegistrado(5L, "Ana López", "ana@hotel.com");
    }

    @Test
    @DisplayName("POST /bff/auth/registro - NO debe publicar evento si el downstream retorna error")
    void noDebePublicarEventoSiRegistroFalla() throws Exception {
        when(usuariosClient.registro(any())).thenReturn(ResponseEntity.badRequest().build());

        mockMvc.perform(post("/bff/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@hotel.com\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());

        verify(notificacionPublisher, never()).publishUsuarioRegistrado(any(), any(), any());
    }

    @Test
    @DisplayName("POST /bff/auth/refresh - debe delegar y retornar nuevo accessToken")
    void debeHacerProxyDeRefresh() throws Exception {
        Map<String, Object> nuevo = Map.of("accessToken", "nuevo-jwt");
        when(usuariosClient.refresh(any())).thenReturn(ResponseEntity.ok(nuevo));

        mockMvc.perform(post("/bff/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"ref-xyz\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("nuevo-jwt"));
    }

    @Test
    @DisplayName("POST /bff/auth/logout - sin cabecera Authorization debe retornar 400")
    void logoutSinCabeceraDebeDar400() throws Exception {
        mockMvc.perform(post("/bff/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(usuariosClient, never()).logout(any(), any());
    }
}
