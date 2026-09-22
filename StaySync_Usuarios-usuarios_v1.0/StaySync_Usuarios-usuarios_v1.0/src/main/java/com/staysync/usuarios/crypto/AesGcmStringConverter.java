package com.staysync.usuarios.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado de atributos String a nivel de persistencia con AES-256-GCM (AEAD: aporta
 * confidencialidad e integridad, detecta manipulación del valor cifrado).
 *
 * Reutilizable en cualquier entidad JPA de cualquier microservicio:
 *   {@code @Convert(converter = AesGcmStringConverter.class)}
 *   {@code private String telefono;}
 *
 * Es un bean de Spring (@Component) para que Hibernate lo resuelva vía el
 * SpringBeanContainer (auto-configurado por spring-boot-starter-data-jpa) y así
 * pueda inyectar la clave por configuración en vez de hardcodearla.
 *
 * IMPORTANTE — no aplicar sobre columnas usadas en WHERE = o índices únicos (p.ej. email):
 * GCM usa un IV aleatorio por valor, así que el mismo texto plano produce un ciphertext
 * distinto en cada escritura, lo cual rompe búsquedas por igualdad y unicidad a nivel de BD.
 *
 * La clave (security.encryption.aes-key, 256 bits en Base64) debe provenir de un secreto
 * gestionado (AWS Secrets Manager / KMS) en producción, nunca de un valor versionado en git.
 */
@Converter
@Component
public class AesGcmStringConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;

    public AesGcmStringConverter(@Value("${security.encryption.aes-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "security.encryption.aes-key debe decodificar a 32 bytes (AES-256), tiene " + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] ivAndCipherText = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, ivAndCipherText, 0, iv.length);
            System.arraycopy(cipherText, 0, ivAndCipherText, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(ivAndCipherText);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el atributo", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(dbValue);

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] cipherText = new byte[decoded.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(decoded, 0, iv, 0, iv.length);
            System.arraycopy(decoded, iv.length, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar el atributo", e);
        }
    }
}
