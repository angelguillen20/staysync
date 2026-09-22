package com.staysync.bff.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService - Tests Unitarios")
class JwtServiceTest {

    private static final String SECRET =
            "staysync-test-secret-key-must-be-at-least-64-characters-for-hs256-hmac-sha256";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
    }

    private String buildToken(String email, String rol, long expiresInMs) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(email)
                .claim("rol", rol)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("extractEmail() - debe extraer el email del token")
    void debeExtraerEmail() {
        String token = buildToken("admin@hotel.com", "ADMIN", 60_000);

        assertThat(jwtService.extractEmail(token)).isEqualTo("admin@hotel.com");
    }

    @Test
    @DisplayName("extractRol() - debe extraer el rol del token")
    void debeExtraerRol() {
        String token = buildToken("recep@hotel.com", "RECEPCIONISTA", 60_000);

        assertThat(jwtService.extractRol(token)).isEqualTo("RECEPCIONISTA");
    }

    @Test
    @DisplayName("isTokenValid() - token vigente debe ser válido")
    void tokenVigenteEsValido() {
        String token = buildToken("user@hotel.com", "HUESPED", 60_000);

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid() - token expirado debe ser inválido")
    void tokenExpiradoEsInvalido() {
        String token = buildToken("user@hotel.com", "HUESPED", -1_000);

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid() - token malformado debe ser inválido")
    void tokenMalformadoEsInvalido() {
        assertThat(jwtService.isTokenValid("esto.no.es.un.jwt")).isFalse();
    }

    @Test
    @DisplayName("isTokenValid() - string vacío debe ser inválido")
    void stringVacioEsInvalido() {
        assertThat(jwtService.isTokenValid("")).isFalse();
    }
}
