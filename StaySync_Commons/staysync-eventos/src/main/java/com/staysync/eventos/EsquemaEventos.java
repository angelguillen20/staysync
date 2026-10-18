package com.staysync.eventos;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Crea las tablas de la librería al arrancar (sintaxis válida en MySQL y en H2).
 * <ul>
 *   <li>outbox_eventos: eventos escritos en la misma transacción que el cambio de negocio,
 *       pendientes de publicar en SNS.</li>
 *   <li>eventos_procesados: eventId ya consumidos, para ignorar entregas duplicadas.</li>
 * </ul>
 */
public class EsquemaEventos implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    public EsquemaEventos(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS outbox_eventos (
                    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                    event_id      VARCHAR(36)  NOT NULL,
                    tipo          VARCHAR(60)  NOT NULL,
                    tenant_id     VARCHAR(64)  NOT NULL,
                    contenido     TEXT         NOT NULL,
                    creado_en     TIMESTAMP    NOT NULL,
                    publicado_en  TIMESTAMP    NULL,
                    intentos      INT          NOT NULL DEFAULT 0,
                    ultimo_error  VARCHAR(500) NULL,
                    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
                )""");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS eventos_procesados (
                    event_id      VARCHAR(36) NOT NULL PRIMARY KEY,
                    tipo          VARCHAR(60) NOT NULL,
                    procesado_en  TIMESTAMP   NOT NULL
                )""");
    }
}
