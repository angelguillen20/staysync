package com.staysync.usuarios.config;

import com.staysync.usuarios.repository.UsuarioRepository;
import com.staysync.usuarios.tenant.repository.HotelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Arranque con la configuración de producción (sin DEMO_DATA_ENABLED), de punta a punta sobre H2. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:datos_iniciales;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "staysync.superadmin-inicial.email=jefa@hotel.cl",
        "staysync.superadmin-inicial.password=Fuerte!2026x"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Datos iniciales - sin cuentas demo fuera del modo demo")
class DatosInicialesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private HotelRepository hotelRepository;

    private void login(String email, String password, int status) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(status));
    }

    @Test
    @DisplayName("No existen las cuentas demo ni el hotel de prueba; sí el hotel por defecto")
    void sinDatosDemo() {
        DataInitializer.CUENTAS_DEMO.forEach(c ->
                assertThat(usuarioRepository.existsByEmail(c.email())).as(c.email()).isFalse());
        assertThat(hotelRepository.existsById("staysync-demo")).isTrue();
        assertThat(hotelRepository.existsById(DataInitializer.TENANT_ANDES)).isFalse();
    }

    @Test
    @DisplayName("La contraseña demo publicada del super admin no da acceso")
    void superAdminDemoNoEntra() throws Exception {
        login("superadmin@staysync.com", "Super@1234", 401);
    }

    @Test
    @DisplayName("El super admin inicial configurado puede iniciar sesión")
    void superAdminInicialEntra() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jefa@hotel.cl\",\"password\":\"Fuerte!2026x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("SUPER_ADMIN"));
    }
}
