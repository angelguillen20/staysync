package com.staysync.bff.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Quita de las respuestas de los microservicios las cabeceras de transporte. Los controladores
 * devuelven el ResponseEntity del RestTemplate tal cual; sin este filtro esas cabeceras pasan a la
 * respuesta del BFF y el ALB la rechaza como malformada (502).
 */
public class HopByHopHeadersInterceptor implements ClientHttpRequestInterceptor {

    private static final Set<String> QUITAR =
            Set.of("transfer-encoding", "content-length", "connection", "keep-alive");

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        HttpHeaders limpias = new HttpHeaders();
        response.getHeaders().forEach((nombre, valores) -> {
            if (!QUITAR.contains(nombre.toLowerCase())) {
                limpias.addAll(nombre, valores);
            }
        });
        return new ClientHttpResponse() {
            @Override public HttpStatusCode getStatusCode() throws IOException { return response.getStatusCode(); }
            @Override public String getStatusText() throws IOException { return response.getStatusText(); }
            @Override public HttpHeaders getHeaders() { return limpias; }
            @Override public InputStream getBody() throws IOException { return response.getBody(); }
            @Override public void close() { response.close(); }
        };
    }
}
