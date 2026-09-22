package com.staysync.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pool de hilos dedicado a las llamadas paralelas BFF -> microservicios (ver DashboardBffController).
 * Acotado deliberadamente: nunca debe compartirse con el pool de Tomcat, para que un pico de
 * llamadas de agregación no compita por hilos con el resto de endpoints del BFF.
 */
@Configuration
public class AsyncConfig {

    @Value("${bff.executor.pool-size:20}")
    private int poolSize;

    @Bean(destroyMethod = "shutdown")
    public ExecutorService bffTaskExecutor() {
        return Executors.newFixedThreadPool(poolSize);
    }
}
