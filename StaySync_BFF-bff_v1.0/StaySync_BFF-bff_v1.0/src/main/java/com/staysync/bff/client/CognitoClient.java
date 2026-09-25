package com.staysync.bff.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Intercambia el authorization code de Cognito Hosted UI por tokens, y decodifica el id_token.
 * El App Client de Cognito es público (sin client secret) → el intercambio usa PKCE
 * (code_verifier) en vez de un secreto.
 *
 * El id_token se decodifica sin verificar la firma: llega directo de Cognito por HTTPS
 * server-to-server (nunca lo toca el navegador del cliente), así que confiar en su contenido
 * en este punto es razonable — no es un token que nos envíe un tercero no confiable.
 */
@Component
@Slf4j
public class CognitoClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${cognito.domain}")
    private String cognitoDomain;

    @Value("${cognito.client-id}")
    private String clientId;

    public CognitoClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Intercambia el code por tokens y devuelve los claims del id_token (email, given_name, family_name...).
     * El redirectUri lo manda el frontend (debe ser exactamente el mismo que usó en /oauth2/authorize,
     * según exige OAuth2) en vez de estar fijo en el backend — así funciona sin importar si el
     * frontend corre en S3, CloudFront o localhost (Cognito acepta http://localhost como excepción a HTTPS).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> obtenerClaimsUsuario(String code, String codeVerifier, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("code_verifier", codeVerifier);

        Map<String, Object> tokens = restTemplate.postForObject(
                cognitoDomain + "/oauth2/token",
                new HttpEntity<>(body, headers),
                Map.class);

        if (tokens == null || tokens.get("id_token") == null) {
            throw new IllegalStateException("Cognito no devolvió id_token");
        }
        return decodificarPayload((String) tokens.get("id_token"));
    }

    private Map<String, Object> decodificarPayload(String jwt) {
        try {
            String[] partes = jwt.split("\\.");
            byte[] payloadBytes = Base64.getUrlDecoder().decode(partes[1]);
            return objectMapper.readValue(new String(payloadBytes, StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            log.error("No se pudo decodificar el id_token de Cognito: {}", e.getMessage());
            throw new IllegalStateException("id_token de Cognito inválido", e);
        }
    }
}
