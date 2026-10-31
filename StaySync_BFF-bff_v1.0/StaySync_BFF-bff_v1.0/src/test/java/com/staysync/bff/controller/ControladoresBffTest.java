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
@Import({SecurityConfig.class, JwtAuthFilter.class, com.staysync.bff.security.AccesoHuesped.class,
        com.staysync.bff.service.CuentaService.class, com.staysync.bff.service.PlataformaService.class})
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
        @DisplayName("No registra pagos de recepción: quedarían COMPLETADOS sin cobrar nada")
        void noRegistraPagoDeRecepcion() throws Exception {
            mockMvc.perform(post("/bff/pagos").header("Authorization", AUTH)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reservaId\":5,\"monto\":1000}"))
                    .andExpect(status().isForbidden());
            verify(pagosClient, never()).procesarPago(any(), any());
        }

        @Test
        @WithMockUser(roles = "HUESPED")
        @DisplayName("No paga con Stripe la reserva de otro huésped")
        void noPagaReservaAjena() throws Exception {
            when(reservasClient.getById(AUTH, 5L)).thenReturn(ok(Map.of("id", 5, "usuarioId", 99, "estado", "PENDIENTE")));

            mockMvc.perform(post("/bff/pagos/stripe/checkout").header("Authorization", AUTH)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reservaId\":5}"))
                    .andExpect(status().isForbidden());
            verify(pagosClient, never()).crearCheckoutStripe(any());
        }

        @Test
        @WithMockUser(roles = "HUESPED")
        @DisplayName("Stripe cobra el total de la reserva aunque el navegador mande otro monto")
        @SuppressWarnings("unchecked")
        void stripeCobraElTotalDeLaReserva() throws Exception {
            when(reservasClient.getById(AUTH, 5L)).thenReturn(ok(Map.of("id", 5, "usuarioId", 7,
                    "estado", "PENDIENTE", "precioTotal", 180000)));
            when(pagosClient.crearCheckoutStripe(any())).thenReturn(ok(Map.of("checkoutUrl", "https://stripe")));

            mockMvc.perform(post("/bff/pagos/stripe/checkout").header("Authorization", AUTH)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reservaId\":5,\"monto\":1}"))
                    .andExpect(status().isCreated());

            verify(pagosClient).crearCheckoutStripe(argThat(b -> Integer.valueOf(180000).equals(((Map<String, Object>) b).get("monto"))));
        }

        @Test
        @WithMockUser(roles = "HUESPED")
        @DisplayName("Una reserva que ya no está PENDIENTE no se vuelve a cobrar")
        void noCobraReservaNoPendiente() throws Exception {
            when(reservasClient.getById(AUTH, 5L)).thenReturn(ok(Map.of("id", 5, "usuarioId", 7, "estado", "CANCELADA")));

            mockMvc.perform(post("/bff/pagos/stripe/checkout").header("Authorization", AUTH)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reservaId\":5}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CANCELADA")));
            verify(pagosClient, never()).crearCheckoutStripe(any());
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
            when(reservasClient.getById(AUTH, 5L)).thenReturn(ok(Map.of("id", 5, "codigo", "RES-AB12CD34", "habitacionId", 3,
                    "fechaEntrada", "2026-11-18", "fechaSalida", "2026-11-20", "estado", "CONFIRMADA")));
            when(habitacionesClient.getById(AUTH, 3L)).thenReturn(ok(Map.of("numero", "101", "precioPorNoche", 45000,
                    "tipo", Map.of("nombre", "Doble"), "estado", "DISPONIBLE")));

            mockMvc.perform(get("/bff/reservas/5/detalle").header("Authorization", AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.codigoReserva").value("RES-AB12CD34"))
                    .andExpect(jsonPath("$.notas").value(""))
                    .andExpect(jsonPath("$.habitacion.precioPorNoche").value(45000))
                    .andExpect(jsonPath("$.habitacion.numero").value("101"))
                    .andExpect(jsonPath("$.habitacion.tipo").value("Doble"))
                    .andExpect(jsonPath("$.noches").value(2));
        }
    }

    @Nested
    @DisplayName("Cuenta del huésped")
    class Cuenta {

        private static final org.springframework.http.HttpMethod GET = org.springframework.http.HttpMethod.GET;

        /** Estadía 100.000 + consumo 20.000 (el cancelado no cuenta) − pago 100.000 (el reembolsado no cuenta) */
        @BeforeEach
        void cuentaConSaldo() {
            when(reservasClient.reenviar(AUTH, GET, "/api/v1/reservas/5", null)).thenReturn(ok(Map.of(
                    "id", 5, "codigo", "RES-1", "estado", "CHECKIN", "usuarioId", 7, "precioTotal", 100000)));
            when(serviciosClient.reenviar(AUTH, GET, "/api/v1/solicitudes/reserva/5", null)).thenReturn(ok(List.of(
                    Map.of("estado", "COMPLETADO", "precioTotal", 20000),
                    Map.of("estado", "CANCELADO", "precioTotal", 99999))));
            when(pagosClient.reenviar(AUTH, GET, "/api/v1/pagos/reserva/5", null)).thenReturn(ok(List.of(
                    Map.of("estado", "COMPLETADO", "monto", 100000),
                    Map.of("estado", "REEMBOLSADO", "monto", 5000))));
        }

        @Test
        @WithMockUser(roles = "RECEPCIONISTA")
        @DisplayName("Suma estadía y consumos, resta lo pagado")
        void calculaSaldo() throws Exception {
            mockMvc.perform(get("/bff/reservas/5/cuenta").header("Authorization", AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalConsumos").value(20000))
                    .andExpect(jsonPath("$.totalPagado").value(100000))
                    .andExpect(jsonPath("$.saldo").value(20000));
        }

        @Test
        @WithMockUser(roles = "RECEPCIONISTA")
        @DisplayName("No hay check-out con saldo pendiente")
        void checkoutConSaldoBloqueado() throws Exception {
            mockMvc.perform(patch("/bff/reservas/5/estado").header("Authorization", AUTH)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"CHECKOUT\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("20.000")));
            verify(reservasClient, never()).cambiarEstado(any(), any(), any());
        }

        @Test
        @WithMockUser(roles = "RECEPCIONISTA")
        @DisplayName("Cobrar el saldo manda a pagos exactamente lo que falta, a nombre del huésped")
        @SuppressWarnings("unchecked")
        void pagaSaldo() throws Exception {
            when(pagosClient.reenviar(eq(AUTH), eq(org.springframework.http.HttpMethod.POST), eq("/api/v1/pagos"), any()))
                    .thenReturn(ok(Map.of("id", 9)));

            mockMvc.perform(post("/bff/reservas/5/cuenta/pago").header("Authorization", AUTH)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\":\"EFECTIVO\"}"))
                    .andExpect(status().isCreated());

            verify(pagosClient).reenviar(eq(AUTH), eq(org.springframework.http.HttpMethod.POST), eq("/api/v1/pagos"),
                    argThat(b -> {
                        Map<String, Object> p = (Map<String, Object>) b;
                        return new java.math.BigDecimal("20000").compareTo((java.math.BigDecimal) p.get("monto")) == 0
                                && Integer.valueOf(7).equals(p.get("usuarioId")) && "EFECTIVO".equals(p.get("metodoPago"));
                    }));
        }

        @Test
        @WithMockUser(roles = "HUESPED")
        @DisplayName("Un huésped no ve la cuenta de otra reserva ni cobra saldos")
        void huespedSoloLoSuyo() throws Exception {
            when(jwtService.extractRol("token")).thenReturn("HUESPED");
            when(jwtService.extractClaim(eq("token"), any())).thenReturn(8L);
            when(reservasClient.getById(AUTH, 5L)).thenReturn(ok(Map.of("id", 5, "usuarioId", 7)));

            mockMvc.perform(get("/bff/reservas/5/cuenta").header("Authorization", AUTH)).andExpect(status().isForbidden());
            mockMvc.perform(post("/bff/reservas/5/cuenta/pago").header("Authorization", AUTH)
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("Plataforma: ve las habitaciones de todos los hoteles, cada consulta con el hotel que pide")
    void habitacionesDeTodosLosHoteles() throws Exception {
        when(jwtService.extractRol("token")).thenReturn("SUPER_ADMIN");
        when(usuariosClient.listarHoteles(AUTH)).thenReturn(ok(List.of(
                Map.of("tenantId", "staysync-demo", "nombre", "StaySync Demo"),
                Map.of("tenantId", "hotel-andes", "nombre", "Hotel Andes"))));
        // La respuesta anota con qué hotel salió la llamada: tiene que ser el consultado, no el del JWT
        when(habitacionesClient.reenviar(AUTH, org.springframework.http.HttpMethod.GET, "/api/v1/habitaciones", null))
                .thenAnswer(inv -> ok(List.of(Map.of("numero", "101",
                        "vista", com.staysync.bff.security.HotelVista.actual().orElse("jwt")))));

        mockMvc.perform(get("/bff/habitaciones").param("hotel", "todos").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].vista").value("staysync-demo"))
                .andExpect(jsonPath("$[1].vista").value("hotel-andes"))
                .andExpect(jsonPath("$[1].hotelNombre").value("Hotel Andes"));
        org.assertj.core.api.Assertions.assertThat(com.staysync.bff.security.HotelVista.actual()).isEmpty();
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    @DisplayName("Plataforma: reservas de un hotel elegido y rango del calendario, con el nombre del hotel")
    void reservasDeOtroHotel() throws Exception {
        when(jwtService.extractRol("token")).thenReturn("SUPER_ADMIN");
        when(usuariosClient.listarHoteles(AUTH)).thenReturn(ok(List.of(
                Map.of("tenantId", "staysync-demo", "nombre", "StaySync Demo"),
                Map.of("tenantId", "hotel-andes", "nombre", "Hotel Andes"))));
        // reservas-service pagina: la respuesta trae { content: [...] }
        when(reservasClient.reenviar(AUTH, org.springframework.http.HttpMethod.GET, "/api/v1/reservas?size=500", null))
                .thenAnswer(inv -> ok(Map.of("content", List.of(Map.of("codigo", "RES-1",
                        "vista", com.staysync.bff.security.HotelVista.actual().orElse("jwt"))))));
        when(reservasClient.reenviar(AUTH, org.springframework.http.HttpMethod.GET,
                "/api/v1/reservas/rango?desde=2026-11-01&hasta=2026-12-01", null)).thenReturn(ok(List.of(Map.of("id", 1))));

        mockMvc.perform(get("/bff/reservas").param("hotel", "hotel-andes").header("Authorization", AUTH))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].vista").value("hotel-andes"))
                .andExpect(jsonPath("$[0].hotelNombre").value("Hotel Andes"));
        mockMvc.perform(get("/bff/reservas/rango").param("desde", "2026-11-01").param("hasta", "2026-12-01")
                        .param("hotel", "todos").header("Authorization", AUTH))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Un ADMIN de hotel no ve las habitaciones de otros hoteles")
    void adminNoVeOtrosHoteles() throws Exception {
        when(jwtService.extractRol("token")).thenReturn("ADMIN");

        mockMvc.perform(get("/bff/habitaciones").param("hotel", "hotel-andes").header("Authorization", AUTH))
                .andExpect(status().isForbidden());
        verifyNoInteractions(habitacionesClient);
        verify(usuariosClient, never()).listarHoteles(any());
    }

    @Test
    @WithMockUser(roles = "RECEPCIONISTA")
    @DisplayName("Calendario: fechas mal escritas son 400, no 500")
    void rangoFechaInvalida() throws Exception {
        mockMvc.perform(get("/bff/reservas/rango").param("desde", "31-10-2026").param("hasta", "2026-11-30")
                .header("Authorization", AUTH)).andExpect(status().isBadRequest());
        verifyNoInteractions(reservasClient);
    }

    @Test
    @WithMockUser(roles = "RECEPCIONISTA")
    @DisplayName("Walk-in: se registra en el portal del hotel de recepción con una contraseña temporal válida")
    @SuppressWarnings("unchecked")
    void registraHuespedWalkIn() throws Exception {
        when(jwtService.extractTenantId("token")).thenReturn("hotel-andes");
        when(usuariosClient.listarHotelesPublicos()).thenReturn(ok(List.of(
                Map.of("tenantId", "staysync-demo", "slug", "staysync-demo"),
                Map.of("tenantId", "hotel-andes", "slug", "andes"))));
        when(usuariosClient.reenviar(eq(AUTH), eq(org.springframework.http.HttpMethod.POST), eq("/api/v1/auth/registro"), any()))
                .thenReturn(ok(Map.of("id", 50)));

        mockMvc.perform(post("/bff/usuarios/huespedes").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Rojas\",\"email\":\"ana@correo.cl\",\"rol\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.id").value(50))
                .andExpect(jsonPath("$.passwordTemporal").value(
                        org.hamcrest.Matchers.matchesPattern("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{12}$")));

        verify(usuariosClient).reenviar(eq(AUTH), any(), any(), argThat(b -> {
            Map<String, Object> r = (Map<String, Object>) b;
            return "andes".equals(r.get("hotelSlug")) && !r.containsKey("rol"); // el body no elige rol
        }));
    }

    @Test
    @WithMockUser(roles = "RECEPCIONISTA")
    @DisplayName("El catálogo y las fotos de habitaciones solo los administra el ADMIN")
    void catalogoSoloAdmin() throws Exception {
        mockMvc.perform(post("/bff/servicios").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/bff/habitaciones/3/imagenes").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(serviciosClient, habitacionesClient);
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
    @WithMockUser(roles = "RECEPCIONISTA")
    @DisplayName("Pago en recepción: cobra el total de la reserva a nombre de su huésped")
    @SuppressWarnings("unchecked")
    void pagoEnRecepcionUsaLaReserva() throws Exception {
        when(jwtService.extractRol("token")).thenReturn("RECEPCIONISTA");
        when(reservasClient.getById(AUTH, 9L)).thenReturn(ok(Map.of("id", 9, "usuarioId", 7,
                "estado", "PENDIENTE", "precioTotal", 150000)));
        when(pagosClient.procesarPago(eq(AUTH), any())).thenReturn(ok(Map.of("id", 1)));

        mockMvc.perform(post("/bff/pagos").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservaId\":9,\"monto\":10,\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isCreated());

        verify(pagosClient).procesarPago(eq(AUTH), argThat(b -> {
            Map<String, Object> pago = (Map<String, Object>) b;
            return Integer.valueOf(150000).equals(pago.get("monto")) && Integer.valueOf(7).equals(pago.get("usuarioId"));
        }));
    }

    @Test
    @DisplayName("El checkout de Stripe exige sesión: sin ella no hay reserva ni monto que cobrar")
    void checkoutSinSesion() throws Exception {
        mockMvc.perform(post("/bff/pagos/stripe/checkout")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reservaId\":5}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(pagosClient);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Pagos: delegan en pagos-service")
    void pagos() throws Exception {
        when(reservasClient.getById(AUTH, 9L)).thenReturn(ok(Map.of("id", 9, "usuarioId", 7,
                "estado", "PENDIENTE", "precioTotal", 150000)));
        when(pagosClient.procesarPago(eq(AUTH), any())).thenReturn(ok(Map.of("id", 1)));
        when(pagosClient.getPagoById(AUTH, 1L)).thenReturn(ok(Map.of("id", 1)));
        when(pagosClient.getPagosPorReserva(AUTH, 9L)).thenReturn(ok(List.of()));
        when(pagosClient.solicitarReembolso(eq(AUTH), eq(1L), any())).thenReturn(ok(Map.of()));
        when(pagosClient.crearCheckoutStripe(any())).thenReturn(ok(Map.of("checkoutUrl", "https://stripe")));
        when(pagosClient.confirmarCheckoutStripe(any())).thenReturn(ok(Map.of("estado", "COMPLETADO")));

        mockMvc.perform(post("/bff/pagos").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reservaId\":9}")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/bff/pagos/1").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(get("/bff/pagos/reserva/9").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(post("/bff/pagos/1/reembolso").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/bff/pagos/stripe/checkout").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reservaId\":9}")).andExpect(status().is2xxSuccessful());
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
