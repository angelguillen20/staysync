package com.staysync.bff.controller;

import com.staysync.bff.client.*;
import com.staysync.bff.config.SecurityConfig;
import com.staysync.bff.security.JwtAuthFilter;
import com.staysync.bff.security.JwtService;
import org.junit.jupiter.api.*;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ReservaBffController.class, HabitacionBffController.class, PagoBffController.class,
        ServicioBffController.class, UsuarioBffController.class, ClimaBffController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, com.staysync.bff.security.AccesoHuesped.class})
@DisplayName("Controladores del BFF")
class ControladoresBffTest {

    private static final String AUTH = "Bearer token";

    @Autowired MockMvc mockMvc;

    @MockitoBean ReservasClient reservasClient;
    @MockitoBean HabitacionesClient habitacionesClient;
    @MockitoBean PagosClient pagosClient;
    @MockitoBean ServiciosClient serviciosClient;
    @MockitoBean UsuariosClient usuariosClient;
    @MockitoBean WeatherClient weatherClient;
    @MockitoBean JwtService jwtService;

    private static ResponseEntity<Object> ok(Object body) {
        return ResponseEntity.ok(body);
    }

    @Nested
    @DisplayName("Un huésped solo opera sobre lo suyo")
    class AislamientoHuesped {

        @BeforeEach
        void comoHuesped7() {
            when(jwtService.extractRol("token")).thenReturn("HUESPED");
            when(jwtService.extractClaim(eq("token"), any())).thenReturn(7L);
        }

        @Test
        @WithMockUser(roles = "HUESPED")
        @DisplayName("Crear: el usuarioId sale del token aunque el body diga otro")
        @SuppressWarnings("unchecked")
        void crearUsaSuPropioId() throws Exception {
            when(usuariosClient.getPerfilPropio(AUTH)).thenReturn(ok(Map.of()));
            when(reservasClient.crear(eq(AUTH), any())).thenReturn(ok(Map.of("id", 1)));

            mockMvc.perform(post("/bff/reservas").header("Authorization", AUTH)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"usuarioId\":99,\"habitacionId\":3}"))
                    .andExpect(status().isCreated());

            verify(reservasClient).crear(eq(AUTH), argThat(b -> Long.valueOf(7).equals(((Map<String, Object>) b).get("usuarioId"))));
        }

        @Test
        @WithMockUser(roles = "HUESPED")
        @DisplayName("No ve las reservas de otro usuario")
        void noVeReservasAjenas() throws Exception {
            mockMvc.perform(get("/bff/reservas/usuario/99").header("Authorization", AUTH))
                    .andExpect(status().isForbidden());
            verify(reservasClient, never()).listarPorUsuario(any(), any(), anyInt(), anyInt());
        }

        @Test
        @WithMockUser(roles = "HUESPED")
        @DisplayName("No cancela la reserva de otro huésped")
        void noCancelaReservaAjena() throws Exception {
            when(reservasClient.getById(AUTH, 5L)).thenReturn(ok(Map.of("id", 5, "usuarioId", 99)));

            mockMvc.perform(patch("/bff/reservas/5/cancelar").header("Authorization", AUTH))
                    .andExpect(status().isForbidden());
            verify(reservasClient, never()).cancelar(any(), any());
        }

        @Test
        @WithMockUser(roles = "HUESPED")
        @DisplayName("Cancela su propia reserva")
        void cancelaSuReserva() throws Exception {
            when(reservasClient.getById(AUTH, 5L)).thenReturn(ok(Map.of("id", 5, "usuarioId", 7)));
            when(reservasClient.cancelar(AUTH, 5L)).thenReturn(ResponseEntity.noContent().build());

            mockMvc.perform(patch("/bff/reservas/5/cancelar").header("Authorization", AUTH))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(roles = "HUESPED")
        @DisplayName("No paga la reserva de otro huésped")
        void noPagaReservaAjena() throws Exception {
            when(reservasClient.getById(AUTH, 5L)).thenReturn(ok(Map.of("id", 5, "usuarioId", 99)));

            mockMvc.perform(post("/bff/pagos").header("Authorization", AUTH)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reservaId\":5,\"monto\":1000}"))
                    .andExpect(status().isForbidden());
            verify(pagosClient, never()).procesarPago(any(), any());
        }

        @Test
        @WithMockUser(roles = "HUESPED")
        @DisplayName("Las rutas de operación del hotel le responden 403")
        void rutasDeOperacionProhibidas() throws Exception {
            mockMvc.perform(get("/bff/reservas").header("Authorization", AUTH)).andExpect(status().isForbidden());
            mockMvc.perform(get("/bff/reservas/5/detalle").header("Authorization", AUTH)).andExpect(status().isForbidden());
            mockMvc.perform(patch("/bff/reservas/5/estado").header("Authorization", AUTH)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"CONFIRMADA\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/bff/pagos/reserva/5").header("Authorization", AUTH)).andExpect(status().isForbidden());
            mockMvc.perform(get("/bff/servicios/solicitudes/todas").header("Authorization", AUTH)).andExpect(status().isForbidden());
            verifyNoInteractions(reservasClient, pagosClient, serviciosClient);
        }
    }

    @Nested
    @DisplayName("Reservas")
    class Reservas {

        @Test
        @WithMockUser(roles = "HUESPED")
        @DisplayName("El huésped que reserva para sí manda su email y nombre a reservas-service")
        @SuppressWarnings("unchecked")
        void crearAgregaContactoDelHuesped() throws Exception {
            when(jwtService.extractRol("token")).thenReturn("HUESPED");
            when(usuariosClient.getPerfilPropio(AUTH))
                    .thenReturn(ok(Map.of("email", "ana@correo.cl", "nombre", "Ana", "apellido", "Rojas")));
            when(reservasClient.crear(eq(AUTH), any())).thenReturn(ok(Map.of("id", 1)));

            mockMvc.perform(post("/bff/reservas").header("Authorization", AUTH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"usuarioId\":7,\"habitacionId\":3}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1));

            verify(reservasClient).crear(eq(AUTH), argThat(b -> {
                Map<String, Object> body = (Map<String, Object>) b;
                return "ana@correo.cl".equals(body.get("huespedEmail")) && "Ana Rojas".equals(body.get("huespedNombre"));
            }));
        }

        @Test
        @WithMockUser(roles = "RECEPCIONISTA")
        @DisplayName("Recepción reservando para un huésped usa el perfil de ese huésped")
        void crearDesdeRecepcion() throws Exception {
            when(jwtService.extractRol("token")).thenReturn("RECEPCIONISTA");
            when(usuariosClient.getPerfil(AUTH, 7L)).thenReturn(ok(Map.of("email", "luis@correo.cl", "nombre", "Luis")));
            when(reservasClient.crear(eq(AUTH), any())).thenReturn(ok(Map.of("id", 2)));

            mockMvc.perform(post("/bff/reservas").header("Authorization", AUTH)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"usuarioId\":7}"))
                    .andExpect(status().isCreated());

            verify(usuariosClient).getPerfil(AUTH, 7L);
            verify(usuariosClient, never()).getPerfilPropio(any());
        }

        @Test
        @WithMockUser(roles = "HUESPED")
        @DisplayName("Si no se puede leer el perfil, la reserva se crea igual")
        void crearSinContacto() throws Exception {
            when(jwtService.extractRol("token")).thenReturn("HUESPED");
            when(usuariosClient.getPerfilPropio(AUTH)).thenThrow(new RuntimeException("caído"));
            when(reservasClient.crear(eq(AUTH), any())).thenReturn(ok(Map.of("id", 3)));

            mockMvc.perform(post("/bff/reservas").header("Authorization", AUTH)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"usuarioId\":7}"))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Listar, hoy, cambiar estado, cancelar y por usuario delegan en reservas-service")
        void delegaciones() throws Exception {
            when(reservasClient.listar(AUTH)).thenReturn(ok(List.of()));
            when(reservasClient.getReservasHoy(AUTH)).thenReturn(ok(Map.of()));
            when(reservasClient.cambiarEstado(eq(AUTH), eq(5L), any())).thenReturn(ok(Map.of("estado", "CHECKIN")));
            when(reservasClient.cancelar(AUTH, 5L)).thenReturn(ResponseEntity.noContent().build());
            when(reservasClient.listarPorUsuario(AUTH, 7L, 0, 20)).thenReturn(ok(Map.of()));

            mockMvc.perform(get("/bff/reservas").header("Authorization", AUTH)).andExpect(status().isOk());
            mockMvc.perform(get("/bff/reservas/hoy").header("Authorization", AUTH)).andExpect(status().isOk());
            mockMvc.perform(patch("/bff/reservas/5/estado").header("Authorization", AUTH)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"CHECKIN\"}"))
                    .andExpect(jsonPath("$.estado").value("CHECKIN"));
            mockMvc.perform(patch("/bff/reservas/5/cancelar").header("Authorization", AUTH))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/bff/reservas/usuario/7").header("Authorization", AUTH)).andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("El detalle junta la reserva con los datos de su habitación")
        void detalle() throws Exception {
            when(reservasClient.getById(AUTH, 5L)).thenReturn(ok(Map.of("id", 5, "habitacionId", 3,
                    "fechaEntrada", "2026-11-18", "fechaSalida", "2026-11-20", "estado", "CONFIRMADA")));
            when(habitacionesClient.getById(AUTH, 3L)).thenReturn(ok(Map.of("numero", "101",
                    "tipo", Map.of("nombre", "Doble"), "estado", "DISPONIBLE")));

            mockMvc.perform(get("/bff/reservas/5/detalle").header("Authorization", AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.habitacion.numero").value("101"))
                    .andExpect(jsonPath("$.habitacion.tipo").value("Doble"))
                    .andExpect(jsonPath("$.noches").value(2));
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Habitaciones: delegan en habitaciones-service")
    void habitaciones() throws Exception {
        when(habitacionesClient.listarTodas(AUTH)).thenReturn(ok(List.of()));
        when(habitacionesClient.listarTipos(AUTH)).thenReturn(ok(List.of()));
        when(habitacionesClient.listarAmenidades(AUTH)).thenReturn(ok(List.of()));
        when(habitacionesClient.getById(AUTH, 3L)).thenReturn(ok(Map.of("id", 3)));
        when(habitacionesClient.crear(eq(AUTH), any())).thenReturn(ok(Map.of("id", 4)));
        when(habitacionesClient.cambiarEstado(eq(AUTH), eq(3L), any())).thenReturn(ok(Map.of()));

        mockMvc.perform(get("/bff/habitaciones").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(get("/bff/habitaciones/tipos").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(get("/bff/habitaciones/amenidades").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(get("/bff/habitaciones/3").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(post("/bff/habitaciones").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(patch("/bff/habitaciones/3/estado").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Habitaciones disponibles es pública (portal del hotel)")
    void disponiblesPublico() throws Exception {
        when(habitacionesClient.buscarDisponibles(any(), any(), any(), any())).thenReturn(ok(List.of()));
        when(habitacionesClient.listarDisponibles(any())).thenReturn(ok(List.of()));

        mockMvc.perform(get("/bff/habitaciones/disponibles")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Pagos: delegan en pagos-service")
    void pagos() throws Exception {
        when(pagosClient.procesarPago(eq(AUTH), any())).thenReturn(ok(Map.of("id", 1)));
        when(pagosClient.getPagoById(AUTH, 1L)).thenReturn(ok(Map.of("id", 1)));
        when(pagosClient.getPagosPorReserva(AUTH, 9L)).thenReturn(ok(List.of()));
        when(pagosClient.solicitarReembolso(eq(AUTH), eq(1L), any())).thenReturn(ok(Map.of()));
        when(pagosClient.crearCheckoutStripe(any())).thenReturn(ok(Map.of("checkoutUrl", "https://stripe")));
        when(pagosClient.confirmarCheckoutStripe(any())).thenReturn(ok(Map.of("estado", "COMPLETADO")));

        mockMvc.perform(post("/bff/pagos").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/bff/pagos/1").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(get("/bff/pagos/reserva/9").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(post("/bff/pagos/1/reembolso").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/bff/pagos/stripe/checkout")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/bff/pagos/stripe/confirmar")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().is2xxSuccessful());
    }

    @Test
    @WithMockUser(roles = "RECEPCIONISTA")
    @DisplayName("Servicios y aseo: delegan en servicios-service")
    void servicios() throws Exception {
        when(serviciosClient.listarDisponibles(AUTH)).thenReturn(ok(List.of()));
        when(serviciosClient.listarPorCategoria(AUTH, 2L)).thenReturn(ok(List.of()));
        when(serviciosClient.solicitarServicio(eq(AUTH), any())).thenReturn(ok(Map.of()));
        when(serviciosClient.listarTodasSolicitudes(AUTH)).thenReturn(ok(List.of()));
        when(serviciosClient.listarSolicitudes(AUTH, 7L)).thenReturn(ok(List.of()));
        when(serviciosClient.actualizarEstadoSolicitud(eq(AUTH), eq(4L), any())).thenReturn(ok(Map.of()));
        when(serviciosClient.listarAseo(AUTH)).thenReturn(ok(List.of(Map.of("id", 1, "estado", "PENDIENTE"))));
        when(serviciosClient.completarAseo(AUTH, 1L)).thenReturn(ok(Map.of("id", 1, "estado", "COMPLETADA")));

        mockMvc.perform(get("/bff/servicios/disponibles").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(get("/bff/servicios/categoria/2").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(post("/bff/servicios/solicitudes").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/bff/servicios/solicitudes/todas").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(get("/bff/servicios/solicitudes").param("usuarioId", "7").header("Authorization", AUTH))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/bff/servicios/solicitudes/4/estado").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mockMvc.perform(get("/bff/servicios/aseo").header("Authorization", AUTH))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
        mockMvc.perform(patch("/bff/servicios/aseo/1/completar").header("Authorization", AUTH))
                .andExpect(jsonPath("$.estado").value("COMPLETADA"));
    }

    @Test
    @WithMockUser(roles = "HUESPED")
    @DisplayName("Un huésped no puede ver las tareas de aseo")
    void aseoSoloPersonal() throws Exception {
        mockMvc.perform(get("/bff/servicios/aseo").header("Authorization", AUTH)).andExpect(status().isForbidden());
        verifyNoInteractions(serviciosClient);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Usuarios: delegan en usuarios-service")
    void usuarios() throws Exception {
        when(usuariosClient.listarHuespedes(AUTH)).thenReturn(ok(List.of()));
        when(usuariosClient.buscarUsuarios(AUTH, "ana", "", null)).thenReturn(ok(List.of()));
        when(usuariosClient.listarUsuarios(AUTH, 0, 10, "id", null)).thenReturn(ok(Map.of()));
        when(usuariosClient.getPerfilPropio(AUTH)).thenReturn(ok(Map.of()));
        when(usuariosClient.updatePerfilPropio(eq(AUTH), any())).thenReturn(ok(Map.of()));
        when(usuariosClient.getPerfil(AUTH, 5L)).thenReturn(ok(Map.of()));
        when(usuariosClient.updatePerfil(eq(AUTH), eq(5L), any())).thenReturn(ok(Map.of()));
        when(usuariosClient.cambiarRol(eq(AUTH), eq(5L), any())).thenReturn(ok(Map.of()));

        mockMvc.perform(get("/bff/usuarios/huespedes").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(get("/bff/usuarios/buscar").param("q", "ana").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(get("/bff/usuarios").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(get("/bff/usuarios/perfil").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(put("/bff/usuarios/perfil").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mockMvc.perform(get("/bff/usuarios/5").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(put("/bff/usuarios/5").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mockMvc.perform(patch("/bff/usuarios/5/rol").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"RECEPCIONISTA\"}")).andExpect(status().isOk());
        mockMvc.perform(delete("/bff/usuarios/5").header("Authorization", AUTH)).andExpect(status().isNoContent());
        verify(usuariosClient).desactivar(AUTH, 5L);
    }

    @Test
    @WithMockUser(roles = "HUESPED")
    @DisplayName("Clima: 204 si no hay datos, 200 con datos")
    void clima() throws Exception {
        when(weatherClient.obtenerClima()).thenReturn(null);
        mockMvc.perform(get("/bff/clima")).andExpect(status().isNoContent());

        when(weatherClient.obtenerClima()).thenReturn(Map.of("temperatura", 18));
        mockMvc.perform(get("/bff/clima")).andExpect(jsonPath("$.temperatura").value(18));
    }
}
