package com.staysync.eventos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Outbox y procesador siempre disponibles en servicios con base de datos. La publicación a
 * SNS (relay) solo con staysync.eventos.publicar=true: en tests y entornos sin mensajería
 * los eventos quedan guardados en el outbox y se publican cuando se active.
 */
@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, TransactionAutoConfiguration.class,
        JacksonAutoConfiguration.class})
@ConditionalOnBean({JdbcTemplate.class, PlatformTransactionManager.class})
public class EventosAutoConfiguration {

    @Bean
    public EsquemaEventos esquemaEventos(JdbcTemplate jdbcTemplate) {
        return new EsquemaEventos(jdbcTemplate);
    }

    @Bean
    public Outbox outbox(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, EsquemaEventos esquema) {
        return new Outbox(jdbcTemplate, objectMapper);
    }

    @Bean
    public ProcesadorEventos procesadorEventos(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
                                               PlatformTransactionManager transactionManager,
                                               EsquemaEventos esquema) {
        return new ProcesadorEventos(objectMapper, jdbcTemplate, new TransactionTemplate(transactionManager));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "staysync.eventos", name = "publicar", havingValue = "true")
    static class RelayConfiguration {

        @Bean
        public OutboxRelay outboxRelay(JdbcTemplate jdbcTemplate, SnsClient snsClient,
                                       @Value("${staysync.eventos.topic-arn}") String topicArn,
                                       EsquemaEventos esquema) {
            return new OutboxRelay(jdbcTemplate, snsClient, topicArn);
        }
    }
}
