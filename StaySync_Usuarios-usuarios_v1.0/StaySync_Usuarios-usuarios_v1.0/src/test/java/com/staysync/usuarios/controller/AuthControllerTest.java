package com.staysync.usuarios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.usuarios.config.SecurityConfig;
import com.staysync.usuarios.dto.request.LoginRequest;
import com.staysync.usuarios.dto.request.RegistroRequest;
import com.staysync.usuarios.dto.response.AuthResponse;
import com.staysync.usuarios.dto.response.UsuarioResponse;
import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.security.JwtAuthFilter;
import com.staysync.usuarios.service.AuthService;
import com.staysync.usuarios.service.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
@DisplayName("AuthController - Tests de Integración Web")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthService authService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /auth/login - debe retornar 200 con credenciales válidas")
    void debeRetornar200EnLoginValido() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@test.com");
        request.setPassword("Pass@123");

        AuthResponse response = AuthResponse.builder()
                .accessToken("jwt-token")
                .refreshToken("refresh-token")
                .email("test@test.com")
                .rol(Usuario.Rol.ADMIN)
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("test@test.com"));
    }

    @Test
    @DisplayName("POST /auth/login - debe retornar 400 con email inválido")
    void debeRetornar400ConEmailInvalido() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("no-es-email");
        request.setPassword("Pass@123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/registro - debe retornar 201 con datos válidos")
    void debeRetornar201EnRegistroValido() throws Exception {
        RegistroRequest request = new RegistroRequest();
        request.setNombre("Juan");
        request.setApellido("García");
        request.setEmail("juan@test.com");
        request.setPassword("Pass@123Seg");
        request.setRol(Usuario.Rol.HUESPED);

        UsuarioResponse response = UsuarioResponse.builder()
                .id(1L)
                .email("juan@test.com")
                .nombre("Juan")
                .build();

        when(authService.registro(any(RegistroRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("juan@test.com"));
    }

    @Test
    @DisplayName("POST /auth/registro - debe retornar 400 con contraseña débil")
    void debeRetornar400ConPasswordDebil() throws Exception {
        RegistroRequest request = new RegistroRequest();
        request.setNombre("Juan");
        request.setApellido("García");
        request.setEmail("juan@test.com");
        request.setPassword("debil");

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
