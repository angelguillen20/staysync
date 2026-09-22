package com.staysync.bff.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Enrutamiento interno BFF -> microservicios, externalizado desde application.yml
 * (services.*.url / variables de entorno). Distinto del enrutamiento público/perimetral,
 * que vive en AWS API Gateway y nunca en el código del BFF.
 */
@Validated
@ConfigurationProperties(prefix = "services")
public record ServicesProperties(
        @Valid Service usuarios,
        @Valid Service habitaciones,
        @Valid Service reservas,
        @Valid Service servicios,
        @Valid Service pagos,
        @Valid Service notificaciones) {

    public record Service(@NotBlank String url) {
    }
}
