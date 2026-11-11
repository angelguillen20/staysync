package com.staysync.tenant;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Para llamadas HTTP entre microservicios (p. ej. Habitaciones -> OTA): reenvía el hotel
 * de la petición actual. Regístralo solo en el RestTemplate que apunta a servicios internos,
 * nunca en uno que llame a APIs externas.
 */
public class TenantPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().remove(TenantHeaders.HEADER);
        TenantContext.get().ifPresent(t -> request.getHeaders().set(TenantHeaders.HEADER, t));
        return execution.execute(request, body);
    }
}
