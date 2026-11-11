package com.staysync.ota.controller;

import com.staysync.ota.model.CanalOta;
import com.staysync.ota.repository.CanalOtaRepository;
import com.staysync.ota.repository.ReservaOtaRepository;
import com.staysync.ota.security.FirmaWebhook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ota_webhooks;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "ota.webhooks.secretos.BOOKING=secreto-booking"
})
@AutoConfigureMockMvc
@DisplayName("Webhook OTA - solo se aceptan envíos firmados por el canal (H2)")
class WebhookOtaIntegrationTest {

    private static final String SECRETO = "secreto-booking";
    private static final String CUERPO = "{\"id\":\"BK-1001\",\"huesped\":\"Ana Rojas\"}";

    @Autowired MockMvc mockMvc;
    @Autowired CanalOtaRepository canales;
    @Autowired ReservaOtaRepository reservas;

    @BeforeEach
    void canales() {
        reservas.deleteAll();
        canales.deleteAll();
        canales.save(CanalOta.builder().nombre("Booking.com").codigo("BOOKING").comision(BigDecimal.TEN).activo(true).build());
        canales.save(CanalOta.builder().nombre("Expedia").codigo("EXPEDIA").comision(BigDecimal.TEN).activo(true).build());
    }

    private ResultActions enviar(String canal, String cuerpo, String timestamp, String firma) throws Exception {
        var peticion = post("/api/v1/webhooks/" + canal).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (timestamp != null) peticion.header(FirmaWebhook.HEADER_TIMESTAMP, timestamp);
        if (firma != null) peticion.header(FirmaWebhook.HEADER_FIRMA, firma);
        return mockMvc.perform(peticion);
    }

    private static String ahora() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    private static String firmar(String timestamp, String cuerpo) {
        return FirmaWebhook.firmar(SECRETO, timestamp, cuerpo.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Con la firma correcta registra la reserva externa")
    void aceptaWebhookFirmado() throws Exception {
        String ts = ahora();

        enviar("BOOKING", CUERPO, ts, firmar(ts, CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaIdExt").value("BK-1001"));

        assertThat(reservas.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sin firma, con firma falsa o caducada responde 401 y no registra nada")
    void rechazaSinFirmaValida() throws Exception {
        String ts = ahora();
        String viejo = String.valueOf(Instant.now().minusSeconds(600).getEpochSecond());

        enviar("BOOKING", CUERPO, null, null).andExpect(status().isUnauthorized());
        enviar("BOOKING", CUERPO, ts, "sha256=" + "0".repeat(64)).andExpect(status().isUnauthorized());
        enviar("BOOKING", CUERPO, viejo, firmar(viejo, CUERPO)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Firma del webhook inválida"));

        assertThat(reservas.count()).isZero();
    }

    @Test
    @DisplayName("Un canal sin secreto configurado no puede enviar webhooks")
    void rechazaCanalSinSecreto() throws Exception {
        String ts = ahora();

        enviar("EXPEDIA", CUERPO, ts, firmar(ts, CUERPO)).andExpect(status().isUnauthorized());
        assertThat(reservas.count()).isZero();
    }

    @Test
    @DisplayName("Firmado pero con un cuerpo que no es JSON responde 400")
    void rechazaJsonInvalido() throws Exception {
        String ts = ahora();
        String roto = "esto no es json";

        enviar("BOOKING", roto, ts, firmar(ts, roto)).andExpect(status().isBadRequest());
    }
}
