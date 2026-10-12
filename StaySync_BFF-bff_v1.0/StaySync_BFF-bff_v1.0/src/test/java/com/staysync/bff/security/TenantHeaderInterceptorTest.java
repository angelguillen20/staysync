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
    @DisplayName("Sin usuario autenticado no se manda hotel")
    void sinAutenticacionNoMandaHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TenantDetails.HEADER, "otro-hotel");
        server.expect(requestTo("http://usuarios:8081/api/v1/auth/login"))
                .andExpect(headerDoesNotExist(TenantDetails.HEADER))
                .andRespond(withSuccess());

        restTemplate.exchange("http://usuarios:8081/api/v1/auth/login", HttpMethod.POST, new HttpEntity<>(headers), String.class);

        server.verify();
    }
}
