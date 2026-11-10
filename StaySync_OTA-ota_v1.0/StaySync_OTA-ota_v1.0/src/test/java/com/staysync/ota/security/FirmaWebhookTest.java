package com.staysync.ota.security;

import com.staysync.ota.exception.FirmaWebhookInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FirmaWebhook - verificación HMAC de webhooks OTA")
class FirmaWebhookTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T12:00:00Z");
    private static final String SECRETO = "secreto-booking";
    private static final byte[] CUERPO = "{\"id\":\"BK-1\"}".getBytes(StandardCharsets.UTF_8);

    private FirmaWebhook firma;
    private String ts;

    @BeforeEach
    void setUp() {
        firma = new FirmaWebhook(Clock.fixed(AHORA, ZoneOffset.UTC));
        firma.setSecretos(Map.of("booking", SECRETO));
        ts = String.valueOf(AHORA.getEpochSecond());
    }

    @Test
    @DisplayName("Acepta una firma correcta, sin importar mayúsculas en el código del canal")
    void aceptaFirmaCorrecta() {
        String valida = FirmaWebhook.firmar(SECRETO, ts, CUERPO);

        assertThatCode(() -> firma.verificar("BOOKING", ts, valida, CUERPO)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Rechaza si el cuerpo fue alterado")
    void rechazaCuerpoAlterado() {
        String valida = FirmaWebhook.firmar(SECRETO, ts, CUERPO);
        byte[] alterado = "{\"id\":\"BK-2\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> firma.verificar("BOOKING", ts, valida, alterado))
                .isInstanceOf(FirmaWebhookInvalidaException.class);
    }

    @Test
    @DisplayName("Rechaza una firma hecha con otro secreto")
    void rechazaOtroSecreto() {
        String otra = FirmaWebhook.firmar("otro-secreto", ts, CUERPO);

        assertThatThrownBy(() -> firma.verificar("BOOKING", ts, otra, CUERPO))
                .isInstanceOf(FirmaWebhookInvalidaException.class);
    }

    @Test
    @DisplayName("Rechaza un webhook viejo aunque la firma sea correcta (reenvío de uno capturado)")
    void rechazaTimestampViejo() {
        String viejo = String.valueOf(AHORA.minusSeconds(301).getEpochSecond());
        String valida = FirmaWebhook.firmar(SECRETO, viejo, CUERPO);

        assertThatThrownBy(() -> firma.verificar("BOOKING", viejo, valida, CUERPO))
                .isInstanceOf(FirmaWebhookInvalidaException.class);
    }

    @Test
    @DisplayName("Rechaza los canales sin secreto configurado")
    void rechazaCanalSinSecreto() {
        String valida = FirmaWebhook.firmar(SECRETO, ts, CUERPO);

        assertThatThrownBy(() -> firma.verificar("EXPEDIA", ts, valida, CUERPO))
                .isInstanceOf(FirmaWebhookInvalidaException.class);
    }

    @Test
    @DisplayName("Rechaza si faltan los headers o el timestamp no es numérico")
    void rechazaHeadersIncompletos() {
        String valida = FirmaWebhook.firmar(SECRETO, ts, CUERPO);

        assertThatThrownBy(() -> firma.verificar("BOOKING", null, valida, CUERPO))
                .isInstanceOf(FirmaWebhookInvalidaException.class);
        assertThatThrownBy(() -> firma.verificar("BOOKING", ts, null, CUERPO))
                .isInstanceOf(FirmaWebhookInvalidaException.class);
        assertThatThrownBy(() -> firma.verificar("BOOKING", "ayer", valida, CUERPO))
                .isInstanceOf(FirmaWebhookInvalidaException.class);
    }
}
