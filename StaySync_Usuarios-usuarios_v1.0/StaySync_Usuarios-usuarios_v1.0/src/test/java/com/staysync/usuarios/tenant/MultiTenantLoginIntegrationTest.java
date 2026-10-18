package com.staysync.usuarios.tenant;

import com.staysync.usuarios.model.Usuario;
import com.staysync.usuarios.repository.UsuarioRepository;
import com.staysync.usuarios.service.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Arranca el servicio completo sobre H2: valida el mapeo de hoteles, la migración de
 * usuarios sin hotel y que el login entregue el tenantId en el JWT y en la respuesta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Multi-tenant - login de punta a punta")
class MultiTenantLoginIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Los usuarios demo quedan asignados a su hotel")
    void usuariosDemoTienenHotel() {
        assertThat(usuarioRepository.findByEmail("admin@staysync.com"))
                .map(Usuario::getTenantId).contains("staysync-demo");
        assertThat(usuarioRepository.findByEmail("admin@hotelandes.com"))
                .map(Usuario::getTenantId).contains("hotel-andes");
    }

    @Test
    @DisplayName("Login devuelve tenantId, marca del hotel y JWT con el claim")
    void loginDevuelveHotel() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@hotelandes.com\",\"password\":\"Admin@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("hotel-andes"))
                .andExpect(jsonPath("$.hotel.nombre").value("Hotel Andes"))
                .andExpect(jsonPath("$.hotel.theme.primary").value("#7A2E1F"))
                .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        assertThat(jwtService.<String>extractClaim(token, c -> c.get("tenantId", String.class)))
                .isEqualTo("hotel-andes");
    }

    @Test
    @DisplayName("Registro con hotelSlug asigna ese hotel; sin slug, el hotel por defecto")
    void registroAsignaHotel() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana","apellido":"Rojas","email":"ana@test.com",
                                 "password":"MiPass@123","hotelSlug":"hotel-andes"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("hotel-andes"));

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Luis","apellido":"Soto","email":"luis@test.com",
                                 "password":"MiPass@123"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("staysync-demo"));
    }

    @Test
    @DisplayName("Registro con un hotel inexistente responde 404")
    void registroHotelInexistente() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Eva","apellido":"Paz","email":"eva@test.com",
                                 "password":"MiPass@123","hotelSlug":"no-existe"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El registro público ignora un rol enviado y crea un huésped")
    void registroIgnoraRol() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Malo","apellido":"Actor","email":"malo@test.com",
                                 "password":"MiPass@123","rol":"SUPER_ADMIN"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("HUESPED"));
    }

    @Test
    @DisplayName("SUPER_ADMIN lista hoteles y usuarios de todos los hoteles; ADMIN no")
    void superAdminVeTodosLosHoteles() throws Exception {
        String superToken = login("superadmin@staysync.com", "Super@1234");
        String andesToken = login("admin@hotelandes.com", "Admin@1234");

        mockMvc.perform(get("/api/v1/hoteles").header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/hoteles").header("Authorization", "Bearer " + andesToken))
                .andExpect(status().isForbidden());

        // hasRole('ADMIN') acepta a SUPER_ADMIN por la jerarquía de roles
        mockMvc.perform(get("/api/v1/usuarios/buscar").param("q", "andes")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("admin@hotelandes.com"));

        // El admin de Andes no ve a los usuarios de StaySync Demo
        mockMvc.perform(get("/api/v1/usuarios/buscar").param("q", "staysync.com")
                        .header("Authorization", "Bearer " + andesToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("SUPER_ADMIN mueve a un recepcionista de hotel; un ADMIN recibe 403")
    void cambiarHotelDePersonal() throws Exception {
        String superToken = login("superadmin@staysync.com", "Super@1234");
        String adminDemoToken = login("admin@staysync.com", "Admin@1234");
        Long recepId = usuarioRepository.findByEmail("recep@staysync.com").orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/usuarios/" + recepId + "/hotel")
                        .header("Authorization", "Bearer " + adminDemoToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tenantId\":\"hotel-andes\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/usuarios/" + recepId + "/hotel")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tenantId\":\"hotel-andes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("hotel-andes"));

        // Ya no pertenece a StaySync Demo: su antiguo admin deja de verlo
        mockMvc.perform(get("/api/v1/usuarios/" + recepId).header("Authorization", "Bearer " + adminDemoToken))
                .andExpect(status().isNotFound());

        // Se deja como estaba para no afectar a otros tests
        mockMvc.perform(patch("/api/v1/usuarios/" + recepId + "/hotel")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tenantId\":\"staysync-demo\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Marca del hotel: el admin edita la suya, no la de otro hotel, y los colores se validan")
    void editarMarca() throws Exception {
        String andesToken = login("admin@hotelandes.com", "Admin@1234");
        String body = """
                {"nombre":"Hotel Andes Boutique","logoUrl":"https://cdn.example.com/andes.png",
                 "theme":{"primary":"#123456","secondary":"#ABCDEF"}}""";

        mockMvc.perform(put("/api/v1/hoteles/hotel-andes/marca").header("Authorization", "Bearer " + andesToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Hotel Andes Boutique"))
                .andExpect(jsonPath("$.theme.primary").value("#123456"));

        mockMvc.perform(put("/api/v1/hoteles/staysync-demo/marca").header("Authorization", "Bearer " + andesToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/hoteles/hotel-andes/marca").header("Authorization", "Bearer " + andesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Andes","theme":{"primary":"red; background:url(x)"}}"""))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/hoteles/hotel-andes/marca").header("Authorization", "Bearer " + andesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Andes","logoUrl":"javascript:alert(1)","theme":{"primary":"#123456"}}"""))
                .andExpect(status().isBadRequest());

        // Se deja como estaba para no afectar a otros tests
        mockMvc.perform(put("/api/v1/hoteles/hotel-andes/marca").header("Authorization", "Bearer " + andesToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Hotel Andes","theme":{"primary":"#7A2E1F","secondary":"#2F6B4F",
                                 "background":"#FBF8F4","text":"#2B211C"}}"""))
                .andExpect(status().isOk());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    @DisplayName("El theme público responde sin token")
    void themePublico() throws Exception {
        mockMvc.perform(get("/api/v1/public/hoteles/staysync-demo/theme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("StaySync Demo"));
    }
}
