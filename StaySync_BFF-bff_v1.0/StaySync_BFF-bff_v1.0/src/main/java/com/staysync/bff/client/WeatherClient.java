package com.staysync.bff.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Clima actual de la ciudad del hotel, vía WeatherAPI.com (plan free).
 * Es un widget decorativo del dashboard: si falta la API key o la API externa
 * falla, el fallback devuelve null y el resto del dashboard sigue funcionando.
 */
@Slf4j
@Component
public class WeatherClient {

    private final RestTemplate restTemplate;

    @Value("${weather.api-key:}")
    private String apiKey;

    @Value("${weather.city:Santiago}")
    private String city;

    public WeatherClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "climaCB", fallbackMethod = "obtenerClimaFallback")
    public Map<String, Object> obtenerClima() {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String url = UriComponentsBuilder
                .fromHttpUrl("https://api.weatherapi.com/v1/current.json")
                .queryParam("key", apiKey)
                .queryParam("q", city)
                .queryParam("aqi", "no")
                .toUriString();

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null) return null;

        Map<String, Object> location  = (Map<String, Object>) response.get("location");
        Map<String, Object> current   = (Map<String, Object>) response.get("current");
        Map<String, Object> condition = (Map<String, Object>) current.get("condition");

        return Map.of(
                "ciudad", location.get("name"),
                "temperaturaC", current.get("temp_c"),
                "condicion", condition.get("text"),
                "icono", "https:" + condition.get("icon")
        );
    }

    public Map<String, Object> obtenerClimaFallback(Exception ex) {
        log.warn("No se pudo obtener el clima de {}: {}", city, ex.getMessage());
        return null;
    }
}
