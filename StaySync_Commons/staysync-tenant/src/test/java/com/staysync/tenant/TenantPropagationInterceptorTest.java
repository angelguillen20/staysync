package com.staysync.tenant;

import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("TenantPropagationInterceptor")
class TenantPropagationInterceptorTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new TenantPropagationInterceptor());
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Reenvía el hotel actual en X-Tenant-Id")
    void reenviaHotel() {
        TenantContext.set("hotel-andes");
        server.expect(requestTo("http://ota:8087/api/v1/canales"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(TenantHeaders.HEADER, "hotel-andes"))
                .andRespond(withSuccess());

        restTemplate.getForEntity("http://ota:8087/api/v1/canales", String.class);

        server.verify();
    }

    @Test
    @DisplayName("Sin hotel actual no manda el header")
    void sinHotelNoMandaHeader() {
        server.expect(requestTo("http://ota:8087/api/v1/canales"))
                .andExpect(headerDoesNotExist(TenantHeaders.HEADER))
                .andRespond(withSuccess());

        restTemplate.getForEntity("http://ota:8087/api/v1/canales", String.class);

        server.verify();
    }
}
