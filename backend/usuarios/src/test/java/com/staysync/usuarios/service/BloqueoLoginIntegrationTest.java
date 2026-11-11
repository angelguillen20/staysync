package com.staysync.usuarios.service;

import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bloqueo temporal de la cuenta por intentos de login fallidos, de punta a punta sobre H2. */
@SpringBootTest(properties = "seguridad.login.max-intentos=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Login - bloqueo temporal por intentos fallidos")
class BloqueoLoginIntegrationTest {

    private static final String PASSWORD = "MiPass@123";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;

    /** Usuario propio de cada test, para no tocar los usuarios demo que usan otros tests. */
    private String registrar(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Rojas\",\"email\":\"" + email
                                + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        return email;
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    @DisplayName("Tras 3 contraseñas incorrectas la cuenta queda bloqueada, incluso con la contraseña correcta")
    void bloqueaTrasIntentosFallidos() throws Exception {
        String email = registrar("bloqueo1@test.com");
        for (int i = 0; i < 3; i++) {
            login(email, "incorrecta").andExpect(status().isUnauthorized());
        }

        login(email, PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.message", containsString("Cuenta bloqueada temporalmente")))
                .andExpect(jsonPath("$.message", containsString("15 minutos")));
    }

    @Test
    @DisplayName("Al vencer el bloqueo puede volver a entrar y el contador vuelve a cero")
    void desbloqueaAlVencer() throws Exception {
        String email = registrar("bloqueo2@test.com");
        for (int i = 0; i < 3; i++) {
            login(email, "incorrecta");
        }
        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        usuario.setBloqueadoHasta(LocalDateTime.now().minusSeconds(1));
        usuarioRepository.save(usuario);

        login(email, PASSWORD).andExpect(status().isOk());

        Usuario despues = usuarioRepository.findByEmail(email).orElseThrow();
        assertThat(despues.getIntentosFallidos()).isZero();
        assertThat(despues.getBloqueadoHasta()).isNull();
    }

    @Test
    @DisplayName("Un login correcto reinicia el contador: los fallos no se acumulan entre sesiones")
    void loginCorrectoReiniciaElContador() throws Exception {
        String email = registrar("bloqueo3@test.com");
        login(email, "incorrecta");
        login(email, "incorrecta");
        login(email, PASSWORD).andExpect(status().isOk());

        login(email, "incorrecta").andExpect(status().isUnauthorized());
        login(email, "incorrecta").andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un email inexistente responde 401 como siempre y no crea nada")
    void emailInexistente() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("nadie@test.com", "x").andExpect(status().isUnauthorized());
        }
        assertThat(usuarioRepository.findByEmail("nadie@test.com")).isEmpty();
    }
}
