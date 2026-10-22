package com.staysync.bff.security;

import com.staysync.bff.config.ServicesProperties;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Agrega X-Tenant-Id a cada llamada BFF -> microservicio interno. Prioridad:
 * <ol>
 *   <li>Usuario autenticado: el hotel de su JWT, siempre (reemplaza cualquier otro valor).</li>
 *   <li>Sin sesión: el que haya puesto explícitamente el código del BFF (p. ej. el hotel
 *       del usuario recién registrado, tomado de la respuesta de usuarios-service).</li>
 *   <li>Sin sesión: el hotel del portal resuelto por {@link HotelPublicoFilter} (X-Hotel).</li>
 * </ol>
 * Las llamadas a APIs externas que comparten el RestTemplate (Cognito, WeatherAPI) no lo
 * reciben: el hotel no tiene por qué salir de nuestra red.
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
        if (!esServicioInterno(request.getURI().toString())) {
            request.getHeaders().remove(TenantDetails.HEADER);
            return execution.execute(request, body);
        }
        Optional<String> delJwt = TenantDetails.actual();
        if (delJwt.isPresent()) {
            request.getHeaders().set(TenantDetails.HEADER, delJwt.get());
        } else if (!request.getHeaders().containsKey(TenantDetails.HEADER)) {
            tenantPublico().ifPresent(t -> request.getHeaders().set(TenantDetails.HEADER, t));
        }
        return execution.execute(request, body);
    }

    private boolean esServicioInterno(String url) {
        return serviciosInternos.stream().anyMatch(url::startsWith);
    }

    private static Optional<String> tenantPublico() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) return Optional.empty();
        Object valor = attrs.getAttribute(HotelPublicoFilter.ATRIBUTO, RequestAttributes.SCOPE_REQUEST);
        return Optional.ofNullable(valor).map(Object::toString);
    }
}
