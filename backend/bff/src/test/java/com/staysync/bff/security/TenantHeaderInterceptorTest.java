package com.staysync.bff.security;

import com.staysync.bff.config.ServicesProperties;
import com.staysync.bff.config.ServicesProperties.Service;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("TenantHeaderInterceptor - Tests Unitarios")
class TenantHeaderInterceptorTest {

    private MockRestServiceServer server;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        ServicesProperties services = new ServicesProperties(
                new Service("http://usuarios:8081"), new Service("http://habitaciones:8082"),
                new Service("http://reservas:8083"), new Service("http://servicios:8084"),
                new Service("http://pagos:8085"), new Service("http://notificaciones:8086"));
        restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new TenantHeaderInterceptor(services));
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarCon(String tenantId) {
        var auth = new UsernamePasswordAuthenticationToken("admin@hotelandes.com", null, List.of());
        auth.setDetails(new TenantDetails(tenantId));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Vista de plataforma: dentro de HotelVista.con sale el hotel consultado, fuera vuelve el del JWT")
    void vistaDePlataformaReemplazaAlJwt() {
        autenticarCon("staysync-demo");
        server.expect(requestTo("http://reservas:8083/api/v1/reservas"))
                .andExpect(header(TenantDetails.HEADER, "hotel-andes"))
                .andRespond(withSuccess());
        server.expect(requestTo("http://reservas:8083/api/v1/reservas"))
                .andExpect(header(TenantDetails.HEADER, "staysync-demo"))
                .andRespond(withSuccess());

        HotelVista.con("hotel-andes", () -> restTemplate.getForEntity("http://reservas:8083/api/v1/reservas", String.class));
        restTemplate.getForEntity("http://reservas:8083/api/v1/reservas", String.class);

        server.verify();
    }

    @Test
    @DisplayName("Servicio interno recibe X-Tenant-Id del JWT")
    void servicioInternoRecibeHeader() {
        autenticarCon("hotel-andes");
        server.expect(requestTo("http://reservas:8083/api/v1/reservas"))
                .andExpect(header(TenantDetails.HEADER, "hotel-andes"))
                .andRespond(withSuccess());

        restTemplate.getForEntity("http://reservas:8083/api/v1/reservas", String.class);

        server.verify();
    }

    @Test
    @DisplayName("API externa no recibe el hotel")
    void apiExternaNoRecibeHeader() {
        autenticarCon("hotel-andes");
        server.expect(requestTo("https://api.weatherapi.com/v1/current.json"))
                .andExpect(headerDoesNotExist(TenantDetails.HEADER))
                .andRespond(withSuccess());

        restTemplate.getForEntity("https://api.weatherapi.com/v1/current.json", String.class);

        server.verify();
    }

    @Test
    @DisplayName("Un X-Tenant-Id puesto a mano se reemplaza por el del JWT")
    void headerManualSeReemplaza() {
        autenticarCon("hotel-andes");
        HttpHeaders headers = new HttpHeaders();
        headers.set(TenantDetails.HEADER, "otro-hotel");
        server.expect(requestTo("http://pagos:8085/api/v1/pagos"))
                .andExpect(header(TenantDetails.HEADER, "hotel-andes"))
                .andRespond(withSuccess());

        restTemplate.exchange("http://pagos:8085/api/v1/pagos", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        server.verify();
    }

    @Test
    @DisplayName("Sin usuario autenticado ni portal no se manda hotel")
    void sinAutenticacionNoMandaHeader() {
        server.expect(requestTo("http://usuarios:8081/api/v1/auth/login"))
                .andExpect(headerDoesNotExist(TenantDetails.HEADER))
                .andRespond(withSuccess());

        restTemplate.postForEntity("http://usuarios:8081/api/v1/auth/login", null, String.class);

        server.verify();
    }

    @Test
    @DisplayName("Sin sesión se respeta el hotel que puso el propio BFF (p. ej. tras registrar un usuario)")
    void sinAutenticacionRespetaHeaderDelBff() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TenantDetails.HEADER, "hotel-andes");
        server.expect(requestTo("http://notificaciones:8086/api/v1/notificaciones/usuario-registrado"))
                .andExpect(header(TenantDetails.HEADER, "hotel-andes"))
                .andRespond(withSuccess());

        restTemplate.exchange("http://notificaciones:8086/api/v1/notificaciones/usuario-registrado",
                HttpMethod.POST, new HttpEntity<>(headers), String.class);

        server.verify();
    }

    @Test
    @DisplayName("Sin sesión usa el hotel del portal (X-Hotel resuelto por HotelPublicoFilter)")
    void sinAutenticacionUsaHotelDelPortal() {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute(HotelPublicoFilter.ATRIBUTO, "hotel-andes");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            server.expect(requestTo("http://habitaciones:8082/api/v1/habitaciones/disponibles"))
                    .andExpect(header(TenantDetails.HEADER, "hotel-andes"))
                    .andRespond(withSuccess());

            restTemplate.getForEntity("http://habitaciones:8082/api/v1/habitaciones/disponibles", String.class);

            server.verify();
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }
}
