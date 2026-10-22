package com.staysync.usuarios.service;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.*;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService - Tests Unitarios")
class JwtServiceTest {

    private static final long EXPIRATION_MS = 3_600_000L;

    private JwtService jwtService;
    private UserDetails usuario;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "TestJWTSecretKeyForUnitTestingPurposes256BitsLong!!");
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", EXPIRATION_MS);
        usuario = new User("huesped@staysync.com", "x", List.of());
    }

    @Test
    @DisplayName("generateToken incluye subject, claims extra, iat y exp correctos")
    void generateToken_claimsCorrectos() {
        Instant antes = Instant.now().minusSeconds(1);
        String token = jwtService.generateToken(usuario, Map.of("rol", "HUESPED"));

        assertThat(jwtService.extractUsername(token)).isEqualTo("huesped@staysync.com");
        String rol = jwtService.extractClaim(token, c -> c.get("rol", String.class));
        assertThat(rol).isEqualTo("HUESPED");

        Instant iat = jwtService.extractClaim(token, Claims::getIssuedAt).toInstant();
        Instant exp = jwtService.extractClaim(token, Claims::getExpiration).toInstant();
        assertThat(iat).isAfterOrEqualTo(antes.truncatedTo(ChronoUnit.SECONDS));
        assertThat(exp.getEpochSecond() - iat.getEpochSecond()).isEqualTo(EXPIRATION_MS / 1000);
    }

    @Test
    @DisplayName("isTokenValid true para el mismo usuario y token vigente")
    void isTokenValid_tokenVigente() {
        String token = jwtService.generateToken(usuario);

        assertThat(jwtService.isTokenExpired(token)).isFalse();
        assertThat(jwtService.isTokenValid(token, usuario)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid false para otro usuario")
    void isTokenValid_otroUsuario() {
        String token = jwtService.generateToken(usuario);
        UserDetails otro = new User("otro@staysync.com", "x", List.of());

        assertThat(jwtService.isTokenValid(token, otro)).isFalse();
    }
}
