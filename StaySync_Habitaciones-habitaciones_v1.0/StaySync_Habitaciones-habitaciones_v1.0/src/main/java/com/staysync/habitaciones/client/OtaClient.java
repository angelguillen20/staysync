package com.staysync.habitaciones.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Notifica a ota-service los cambios de estado de habitación vía RestTemplate.
 * Reemplaza al evento "habitacion.estado.cambiado" que antes se publicaba a una
 * cola de RabbitMQ nunca enlazada al exchange — nadie lo recibía realmente.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtaClient {

    private final RestTemplate restTemplate;

    @Value("${services.ota.url}")
    private String otaUrl;

    public void sincronizarEstado(Long habitacionId, String estadoAnterior, String estadoNuevo) {
        try {
            restTemplate.postForEntity(
                    otaUrl + "/api/v1/sincronizacion/estado",
                    Map.of(
                            "habitacionId", habitacionId,
                            "estadoAnterior", estadoAnterior,
                            "estadoNuevo", estadoNuevo
                    ),
                    Void.class);
        } catch (Exception e) {
            log.warn("No se pudo notificar a ota-service el cambio de estado de habitación {}: {}",
                    habitacionId, e.getMessage());
        }
    }
}
