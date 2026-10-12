package com.staysync.bff.security;

import com.staysync.bff.config.ServicesProperties;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Agrega X-Tenant-Id (tomado del JWT) a cada llamada BFF -> microservicio interno.
 * Las llamadas a APIs externas que comparten el RestTemplate (Cognito, WeatherAPI)
 * no lo reciben: el hotel no tiene por qué salir de nuestra red.
 */
public class TenantHeaderInterceptor implements ClientHttpRequestInterceptor {

    private final List<String> serviciosInternos;

    public TenantHeaderInterceptor(ServicesProperties services) {
        this.serviciosInternos = Stream.of(services.usuarios(), services.habitaciones(), services.reservas(),
                        services.servicios(), services.pagos(), services.notificaciones())
                .filter(Objects::nonNull)
                .map(ServicesProperties.Service::url)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().remove(TenantDetails.HEADER);
        if (esServicioInterno(request.getURI().toString())) {
            TenantDetails.actual().ifPresent(t -> request.getHeaders().set(TenantDetails.HEADER, t));
        }
        return execution.execute(request, body);
    }

    private boolean esServicioInterno(String url) {
        return serviciosInternos.stream().anyMatch(url::startsWith);
    }
}
