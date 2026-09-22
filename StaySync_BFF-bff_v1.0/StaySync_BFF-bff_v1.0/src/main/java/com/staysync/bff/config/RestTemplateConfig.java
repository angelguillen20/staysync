package com.staysync.bff.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate de alto rendimiento para las llamadas BFF -> microservicios internos:
 * pool de conexiones HTTP reutilizables (evita el handshake TCP/TLS en cada llamada)
 * y timeouts estrictos en las tres fases (obtener conexión del pool, conectar, leer)
 * para que un downstream lento no agote los hilos del servlet container.
 */
@Configuration
public class RestTemplateConfig {

    @Value("${http.client.connect-timeout:3000}")
    private int connectTimeout;

    @Value("${http.client.read-timeout:5000}")
    private int readTimeout;

    @Value("${http.client.connection-request-timeout:1000}")
    private int connectionRequestTimeout;

    @Value("${http.client.max-total-connections:200}")
    private int maxTotalConnections;

    @Value("${http.client.max-connections-per-route:50}")
    private int maxConnectionsPerRoute;

    @Bean
    public PoolingHttpClientConnectionManager poolingConnectionManager() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxTotalConnections);
        connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);
        connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))
                .setSocketTimeout(Timeout.ofMilliseconds(readTimeout))
                .build());
        return connectionManager;
    }

    @Bean(destroyMethod = "close")
    public CloseableHttpClient httpClient(PoolingHttpClientConnectionManager connectionManager) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeout))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictIdleConnections(TimeValue.ofSeconds(30))
                .build();
    }

    @Bean
    public RestTemplate restTemplate(CloseableHttpClient httpClient) {
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }
}
