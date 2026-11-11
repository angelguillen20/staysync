package com.staysync.bff.controller;

import com.staysync.bff.client.UsuariosClient;
import com.staysync.bff.config.SecurityConfig;
import com.staysync.bff.security.JwtAuthFilter;
import com.staysync.bff.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {HotelBffController.class, UsuarioBffController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class})
@DisplayName("Permisos SUPER_ADMIN / ADMIN en el BFF")
class SuperAdminAccesoTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean UsuariosClient usuariosClient;
    @MockitoBean JwtService jwtService;

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("SUPER_ADMIN lista hoteles")
    void superAdminListaHoteles() throws Exception {
        when(usuariosClient.listarHoteles(anyString()))
                .thenReturn(ResponseEntity.ok(List.of(Map.of("tenantId", "hotel-andes"))));

        mockMvc.perform(get("/bff/hoteles").header("Authorization", "Bearer t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenantId").value("hotel-andes"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN de hotel no puede listar hoteles ni cambiar hotel")
    void adminNoGestionaHoteles() throws Exception {
        mockMvc.perform(get("/bff/hoteles").header("Authorization", "Bearer t"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/bff/usuarios/5/hotel").header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tenantId\":\"hotel-andes\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usuariosClient);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("SUPER_ADMIN crea un hotel")
    void superAdminCreaHotel() throws Exception {
        when(usuariosClient.crearHotel(anyString(), any()))
                .thenReturn(ResponseEntity.status(201).body(Map.of("tenantId", "hotel-pacifico")));

        mockMvc.perform(post("/bff/hoteles").header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Hotel Pacifico\",\"slug\":\"hotel-pacifico\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Un ADMIN de hotel no puede crear hoteles")
    void adminNoCreaHotel() throws Exception {
        mockMvc.perform(post("/bff/hoteles").header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verify(usuariosClient, never()).crearHotel(anyString(), any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("SUPER_ADMIN hereda los permisos de ADMIN (listar usuarios)")
    void superAdminHeredaAdmin() throws Exception {
        when(usuariosClient.listarUsuarios(anyString(), eq(0), eq(10), eq("id"), eq("hotel-andes")))
                .thenReturn(ResponseEntity.ok(Map.of("content", List.of())));

        mockMvc.perform(get("/bff/usuarios").param("tenantId", "hotel-andes").header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("SUPER_ADMIN cambia el hotel de un usuario")
    void superAdminCambiaHotel() throws Exception {
        when(usuariosClient.cambiarHotel(anyString(), eq(5L), any()))
                .thenReturn(ResponseEntity.ok(Map.of("id", 5, "tenantId", "hotel-andes")));

        mockMvc.perform(patch("/bff/usuarios/5/hotel").header("Authorization", "Bearer t")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tenantId\":\"hotel-andes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("hotel-andes"));
    }

    @Test
    @DisplayName("La marca del hotel es pública")
    void themeEsPublico() throws Exception {
        when(usuariosClient.obtenerThemeHotel("hotel-andes"))
                .thenReturn(ResponseEntity.ok(Map.of("nombre", "Hotel Andes")));

        mockMvc.perform(get("/bff/public/hoteles/hotel-andes/theme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Hotel Andes"));
    }
}
