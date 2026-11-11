package com.staysync.ota.security;

import com.staysync.ota.exception.FirmaWebhookInvalidaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Verifica que un webhook venga del canal OTA y no de cualquiera que conozca la URL.
 * El canal firma cada envío con un secreto compartido (uno por canal):
 * <pre>
 *   X-Webhook-Timestamp: segundos Unix del envío
 *   X-Webhook-Signature: sha256=hex(HMAC-SHA256(secreto, timestamp + "." + cuerpo))
 * </pre>
 * Se rechaza si el canal no tiene secreto configurado, si la firma no coincide o si el
 * timestamp se aleja más de la tolerancia (un webhook capturado no se puede reenviar después).
 */
@Component
@ConfigurationProperties(prefix = "ota.webhooks")
@Slf4j
public class FirmaWebhook {

    public static final String HEADER_TIMESTAMP = "X-Webhook-Timestamp";
    public static final String HEADER_FIRMA = "X-Webhook-Signature";
    private static final String PREFIJO = "sha256=";

    /** Secreto por código de canal (OTA_WEBHOOKS_SECRETOS_BOOKING → "booking"). */
    private Map<String, String> secretos = new HashMap<>();
    private Duration tolerancia = Duration.ofMinutes(5);
    private final Clock clock;

    @Autowired
    public FirmaWebhook() {
        this(Clock.systemUTC());
    }

    FirmaWebhook(Clock clock) {
        this.clock = clock;
    }

    public void verificar(String codigoCanal, String timestamp, String firma, byte[] cuerpo) {
        String secreto = secretoDe(codigoCanal);
        if (secreto == null) {
            rechazar(codigoCanal, "el canal no tiene secreto de webhook configurado");
        }
        if (timestamp == null || firma == null) {
            rechazar(codigoCanal, "faltan los headers " + HEADER_TIMESTAMP + " / " + HEADER_FIRMA);
        }
        long enviado;
        try {
            enviado = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            rechazar(codigoCanal, "timestamp no numérico");
            return;
        }
        long desfase = Math.abs(clock.instant().getEpochSecond() - enviado);
        if (desfase > tolerancia.toSeconds()) {
            rechazar(codigoCanal, "timestamp fuera de la tolerancia (" + desfase + " s)");
        }
        byte[] esperada = firmar(secreto, timestamp.trim(), cuerpo).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(esperada, firma.trim().getBytes(StandardCharsets.UTF_8))) {
            rechazar(codigoCanal, "firma no coincide");
        }
    }

    /** Firma que debe enviar el canal; pública para documentación y pruebas de integración. */
    public static String firmar(String secreto, String timestamp, byte[] cuerpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return PREFIJO + HexFormat.of().formatHex(mac.doFinal(cuerpo));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", e);
        }
    }

    private String secretoDe(String codigoCanal) {
        if (codigoCanal == null) {
            return null;
        }
        String buscado = codigoCanal.toLowerCase(Locale.ROOT);
        return secretos.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase(Locale.ROOT).equals(buscado))
                .map(Map.Entry::getValue)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** Al canal solo se le dice que la firma es inválida; el motivo queda en el log. */
    private static void rechazar(String codigoCanal, String motivo) {
        log.warn("Webhook OTA rechazado (canal {}): {}", codigoCanal, motivo);
        throw new FirmaWebhookInvalidaException();
    }

    public Map<String, String> getSecretos() {
        return secretos;
    }

    public void setSecretos(Map<String, String> secretos) {
        this.secretos = secretos;
    }

    public Duration getTolerancia() {
        return tolerancia;
    }

    public void setTolerancia(Duration tolerancia) {
        this.tolerancia = tolerancia;
    }
}
