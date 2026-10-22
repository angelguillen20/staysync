package com.staysync.bff.client;

import com.staysync.bff.config.ServicesProperties;
import com.staysync.bff.config.ServicesProperties.Service;
import com.staysync.bff.exception.DownstreamServiceException;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Clientes HTTP del BFF: cada método llama a la URL y con el verbo correctos del
 * microservicio, reenvía el token y los fallbacks responden como se espera.
 * (Los @CircuitBreaker no se activan aquí: se prueban los fallbacks llamándolos directo.)
 */
@DisplayName("Clientes HTTP del BFF")
class ClientesHttpTest {

    private static final String TOKEN = "Bearer t";
    private static final Map<String, Object> BODY = Map.of("x", 1);
    private static final RuntimeException CAIDA = new RuntimeException("connection refused");
    private static final HttpClientErrorException NO_ENCONTRADO =
            HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null);

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private ServicesProperties services;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        services = new ServicesProperties(
                new Service("http://usuarios"), new Service("http://habitaciones"), new Service("http://reservas"),
                new Service("http://servicios"), new Service("http://pagos"), new Service("http://notificaciones"));
    }

    private void esperar(HttpMethod metodo, String url) {
        server.expect(ExpectedCount.once(), requestTo(url))
                .andExpect(method(metodo))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    }

    private void esperarConToken(HttpMethod metodo, String url) {
        server.expect(ExpectedCount.once(), requestTo(url))
                .andExpect(method(metodo))
                .andExpect(header("Authorization", TOKEN))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("UsuariosClient llama a los endpoints de usuarios-service")
    void usuarios() {
        UsuariosClient c = new UsuariosClient(restTemplate, services);
        esperar(HttpMethod.POST, "http://usuarios/api/v1/auth/login");
        esperar(HttpMethod.POST, "http://usuarios/api/v1/auth/registro");
        esperar(HttpMethod.POST, "http://usuarios/api/v1/auth/refresh");
        esperar(HttpMethod.POST, "http://usuarios/api/v1/auth/google");
        esperarConToken(HttpMethod.POST, "http://usuarios/api/v1/auth/logout");
        esperarConToken(HttpMethod.GET, "http://usuarios/api/v1/usuarios/perfil");
        esperarConToken(HttpMethod.PUT, "http://usuarios/api/v1/usuarios/perfil");
        esperarConToken(HttpMethod.GET, "http://usuarios/api/v1/usuarios/5");
        esperarConToken(HttpMethod.PUT, "http://usuarios/api/v1/usuarios/5");
        esperarConToken(HttpMethod.PATCH, "http://usuarios/api/v1/usuarios/5/rol");
        esperarConToken(HttpMethod.PATCH, "http://usuarios/api/v1/usuarios/5/hotel");
        esperarConToken(HttpMethod.PUT, "http://usuarios/api/v1/hoteles/hotel-andes/marca");
        esperarConToken(HttpMethod.GET, "http://usuarios/api/v1/hoteles");
        esperarConToken(HttpMethod.GET, "http://usuarios/api/v1/usuarios?page=0&size=10&sort=id&tenantId=hotel-andes");
        esperarConToken(HttpMethod.GET, "http://usuarios/api/v1/usuarios/buscar?q=ana&rol=HUESPED");
        esperarConToken(HttpMethod.GET, "http://usuarios/api/v1/usuarios/huespedes");
        esperarConToken(HttpMethod.DELETE, "http://usuarios/api/v1/usuarios/5");
        esperar(HttpMethod.GET, "http://usuarios/api/v1/public/hoteles/hotel-andes/theme");
        esperar(HttpMethod.GET, "http://usuarios/api/v1/public/hoteles");

        c.login(BODY);
        c.registro(BODY);
        c.refresh(BODY);
        c.loginConGoogle(BODY);
        c.logout(TOKEN, BODY);
        c.getPerfilPropio(TOKEN);
        c.updatePerfilPropio(TOKEN, BODY);
        c.getPerfil(TOKEN, 5L);
        c.updatePerfil(TOKEN, 5L, BODY);
        c.cambiarRol(TOKEN, 5L, BODY);
        c.cambiarHotel(TOKEN, 5L, BODY);
        c.actualizarMarca(TOKEN, "hotel-andes", BODY);
        c.listarHoteles(TOKEN);
        c.listarHotelesPublicos();
        c.listarUsuarios(TOKEN, 0, 10, "id", "hotel-andes");
        c.buscarUsuarios(TOKEN, "ana", "HUESPED", null);
        c.listarHuespedes(TOKEN);
        c.desactivar(TOKEN, 5L);
        assertThat(c.obtenerThemeHotel("hotel-andes").getStatusCode().is2xxSuccessful()).isTrue();

        server.verify();
    }

    @Test
    @DisplayName("UsuariosClient: los 4xx se reenvían tal cual y las caídas dan 'servicio no disponible'")
    void usuariosFallbacks() {
        UsuariosClient c = new UsuariosClient(restTemplate, services);

        assertThatThrownBy(() -> c.authFallback(TOKEN, NO_ENCONTRADO)).isSameAs(NO_ENCONTRADO);
        assertThatThrownBy(() -> c.authFallback(TOKEN, BODY, CAIDA)).isInstanceOf(DownstreamServiceException.class);
        assertThatThrownBy(() -> c.authFallback(TOKEN, 5L, CAIDA)).isInstanceOf(DownstreamServiceException.class);
        assertThatThrownBy(() -> c.authFallback(TOKEN, 5L, BODY, NO_ENCONTRADO)).isSameAs(NO_ENCONTRADO);
        assertThatThrownBy(() -> c.themeFallback("x", NO_ENCONTRADO)).isSameAs(NO_ENCONTRADO);
        assertThatThrownBy(() -> c.themeFallback("x", CAIDA)).isInstanceOf(DownstreamServiceException.class);
        assertThatThrownBy(() -> c.marcaFallback(TOKEN, "x", BODY, CAIDA)).isInstanceOf(DownstreamServiceException.class);
        assertThatThrownBy(() -> c.loginFallback(BODY, CAIDA)).isInstanceOf(DownstreamServiceException.class);
        assertThatThrownBy(() -> c.passthruFallback(BODY, CAIDA)).isInstanceOf(DownstreamServiceException.class);
        assertThatThrownBy(() -> c.desactivarFallback(TOKEN, 5L, CAIDA)).isInstanceOf(DownstreamServiceException.class);
        assertThat(c.buscarFallback(TOKEN, "q", "rol", null, CAIDA).getBody()).isEqualTo(List.of());
        assertThatThrownBy(() -> c.listarFallback(TOKEN, 0, 10, "id", null, CAIDA)).isInstanceOf(DownstreamServiceException.class);
        assertThat(c.listarHuespedesFallback(TOKEN, CAIDA).getBody()).isEqualTo(List.of());
    }

    @Test
    @DisplayName("HabitacionesClient llama a los endpoints de habitaciones-service")
    void habitaciones() {
        HabitacionesClient c = new HabitacionesClient(restTemplate, services);
        server.expect(ExpectedCount.manyTimes(), requestTo(startsWith("http://habitaciones/api/v1/")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        c.listarDisponibles(TOKEN);
        c.buscarDisponibles(TOKEN, 2, "WiFi", "precio_desc");
        c.listarTodas(TOKEN);
        c.getById(TOKEN, 3L);
        c.crear(TOKEN, BODY);
        c.cambiarEstado(TOKEN, 3L, BODY);
        c.listarTipos(TOKEN);
        c.listarAmenidades(TOKEN);
        c.desactivar(TOKEN, 3L);

        server.verify();
    }

    @Test
    @DisplayName("HabitacionesClient: fallbacks")
    void habitacionesFallbacks() {
        HabitacionesClient c = new HabitacionesClient(restTemplate, services);
        assertThat(c.listarFallback(TOKEN, CAIDA).getBody()).isEqualTo(List.of());
        assertThat(c.buscarFallback(TOKEN, 2, "WiFi", "s", CAIDA).getBody()).isEqualTo(List.of());
        assertThat(c.getOneFallback(TOKEN, 3L, CAIDA).getBody()).isNull();
        assertThatThrownBy(() -> c.passthruFallback(TOKEN, BODY, CAIDA)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> c.passthruFallback(TOKEN, 3L, BODY, CAIDA)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> c.desactivarFallback(TOKEN, 3L, CAIDA)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("ReservasClient llama a los endpoints de reservas-service")
    void reservas() {
        ReservasClient c = new ReservasClient(restTemplate, services);
        esperarConToken(HttpMethod.GET, "http://reservas/api/v1/reservas");
        esperarConToken(HttpMethod.GET, "http://reservas/api/v1/reservas/hoy");
        esperarConToken(HttpMethod.GET, "http://reservas/api/v1/reservas/9");
        server.expect(requestTo(startsWith("http://reservas/api/v1/reservas/usuario/7")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        esperarConToken(HttpMethod.POST, "http://reservas/api/v1/reservas");
        esperarConToken(HttpMethod.PATCH, "http://reservas/api/v1/reservas/9/estado");
        esperarConToken(HttpMethod.PATCH, "http://reservas/api/v1/reservas/9/cancelar");

        c.listar(TOKEN);
        c.getReservasHoy(TOKEN);
        c.getById(TOKEN, 9L);
        c.listarPorUsuario(TOKEN, 7L, 0, 20);
        c.crear(TOKEN, BODY);
        c.cambiarEstado(TOKEN, 9L, BODY);
        c.cancelar(TOKEN, 9L);

        server.verify();
    }

    @Test
    @DisplayName("ReservasClient: fallbacks")
    void reservasFallbacks() {
        ReservasClient c = new ReservasClient(restTemplate, services);
        assertThatThrownBy(() -> c.listarFallback(TOKEN, CAIDA)).isInstanceOf(DownstreamServiceException.class);
        assertThatThrownBy(() -> c.listarFallback(TOKEN, 7L, 0, 20, CAIDA)).isInstanceOf(DownstreamServiceException.class);
        assertThatThrownBy(() -> c.getOneFallback(TOKEN, 9L, CAIDA)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> c.mutarFallback(TOKEN, BODY, CAIDA)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> c.mutarFallback(TOKEN, 9L, BODY, CAIDA)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> c.mutarFallback(TOKEN, 9L, CAIDA)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("ServiciosClient llama a los endpoints de servicios-service, incluido el aseo")
    void servicios() {
        ServiciosClient c = new ServiciosClient(restTemplate, services);
        esperarConToken(HttpMethod.GET, "http://servicios/api/v1/servicios");
        esperarConToken(HttpMethod.GET, "http://servicios/api/v1/servicios/categoria/2");
        esperarConToken(HttpMethod.POST, "http://servicios/api/v1/solicitudes");
        esperarConToken(HttpMethod.GET, "http://servicios/api/v1/solicitudes/usuario/7");
        esperarConToken(HttpMethod.GET, "http://servicios/api/v1/solicitudes");
        esperarConToken(HttpMethod.PATCH, "http://servicios/api/v1/solicitudes/4/estado");
        esperarConToken(HttpMethod.GET, "http://servicios/api/v1/aseo");
        esperarConToken(HttpMethod.PATCH, "http://servicios/api/v1/aseo/4/completar");

        c.listarDisponibles(TOKEN);
        c.listarPorCategoria(TOKEN, 2L);
        c.solicitarServicio(TOKEN, BODY);
        c.listarSolicitudes(TOKEN, 7L);
        c.listarTodasSolicitudes(TOKEN);
        c.actualizarEstadoSolicitud(TOKEN, 4L, BODY);
        c.listarAseo(TOKEN);
        c.completarAseo(TOKEN, 4L);

        server.verify();
    }

    @Test
    @DisplayName("ServiciosClient: fallbacks")
    void serviciosFallbacks() {
        ServiciosClient c = new ServiciosClient(restTemplate, services);
        assertThat(c.listarFallback(TOKEN, CAIDA).getBody()).isEqualTo(List.of());
        assertThat(c.listarFallback(TOKEN, 7L, CAIDA).getBody()).isEqualTo(List.of());
        assertThatThrownBy(() -> c.mutarFallback(TOKEN, BODY, CAIDA)).isInstanceOf(DownstreamServiceException.class);
        assertThatThrownBy(() -> c.mutarFallback(TOKEN, 4L, CAIDA)).isInstanceOf(DownstreamServiceException.class);
        assertThatThrownBy(() -> c.mutarFallback(TOKEN, 4L, BODY, CAIDA)).isInstanceOf(DownstreamServiceException.class);
    }

    @Test
    @DisplayName("PagosClient llama a los endpoints de pagos-service y Stripe")
    void pagos() {
        PagosClient c = new PagosClient(restTemplate, services);
        server.expect(ExpectedCount.manyTimes(), requestTo(startsWith("http://pagos/api/")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        c.procesarPago(TOKEN, BODY);
        c.getPagoById(TOKEN, 1L);
        c.getPagosPorReserva(TOKEN, 9L);
        c.solicitarReembolso(TOKEN, 1L, BODY);
        c.crearCheckoutStripe(BODY);
        c.confirmarCheckoutStripe(BODY);

        server.verify();
    }

    @Test
    @DisplayName("PagosClient: fallbacks")
    void pagosFallbacks() {
        PagosClient c = new PagosClient(restTemplate, services);
        assertThatThrownBy(() -> c.stripeFallback(BODY, CAIDA)).isInstanceOf(DownstreamServiceException.class);
        assertThatThrownBy(() -> c.mutarFallback(TOKEN, BODY, CAIDA)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> c.mutarFallback(TOKEN, 1L, BODY, CAIDA)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> c.getOneFallback(TOKEN, 1L, CAIDA)).isInstanceOf(RuntimeException.class);
        assertThat(c.listarFallback(TOKEN, 9L, CAIDA).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @DisplayName("Un 404 del microservicio llega como HttpClientErrorException")
    void errorDelServicio() {
        ReservasClient c = new ReservasClient(restTemplate, services);
        server.expect(requestTo("http://reservas/api/v1/reservas/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> c.getById(TOKEN, 99L)).isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    @DisplayName("NotificacionEventPublisher manda el hotel del usuario registrado")
    void publicadorRegistro() {
        var publicador = new com.staysync.bff.messaging.NotificacionEventPublisher(restTemplate, services);
        org.springframework.test.util.ReflectionTestUtils.setField(publicador, "enabled", true);
        server.expect(requestTo("http://notificaciones/api/v1/notificaciones/usuario-registrado"))
                .andExpect(header("X-Tenant-Id", "hotel-andes"))
                .andRespond(withSuccess());

        publicador.publishUsuarioRegistrado(5L, "Ana", "ana@correo.cl", "hotel-andes");

        server.verify();
    }

    @Test
    @DisplayName("NotificacionEventPublisher no rompe el registro si Notificaciones está caído")
    void publicadorTolerante() {
        var publicador = new com.staysync.bff.messaging.NotificacionEventPublisher(restTemplate, services);
        org.springframework.test.util.ReflectionTestUtils.setField(publicador, "enabled", true);
        server.expect(requestTo(startsWith("http://notificaciones"))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatNoException().isThrownBy(() -> publicador.publishUsuarioRegistrado(5L, "Ana", "a@b.cl", null));
    }

    @Test
    @DisplayName("WeatherClient devuelve null (sin widget) si WeatherAPI rechaza la API key")
    void climaConApiKeyInvalida() {
        var clima = new WeatherClient(restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(clima, "apiKey", "invalida");
        org.springframework.test.util.ReflectionTestUtils.setField(clima, "city", "Santiago");
        server.expect(requestTo(startsWith("https://api.weatherapi.com/v1/current.json")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(clima.obtenerClima()).isNull();
    }

    @Test
    @DisplayName("Los clientes devuelven el cuerpo del microservicio")
    void devuelveCuerpo() {
        server.expect(requestTo("http://reservas/api/v1/reservas/hoy"))
                .andRespond(withSuccess("{\"fecha\":\"2026-10-18\"}", MediaType.APPLICATION_JSON));

        ResponseEntity<Object> r = new ReservasClient(restTemplate, services).getReservasHoy(TOKEN);

        assertThat(r.getBody()).isEqualTo(Map.of("fecha", "2026-10-18"));
    }
}
