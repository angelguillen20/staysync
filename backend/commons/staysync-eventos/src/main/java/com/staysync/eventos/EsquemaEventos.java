package com.staysync.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.dao.DuplicateKeyException;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Crea las tablas de la librería al arrancar (sintaxis válida en MySQL y en H2).
 * <ul>
 *   <li>outbox_eventos: eventos escritos en la misma transacción que el cambio de negocio,
 *       pendientes de publicar en SNS. proximo_intento marca cuándo reintentar tras un fallo.</li>
 *   <li>eventos_procesados: eventId ya consumidos, para ignorar entregas duplicadas.</li>
 *   <li>eventos_migraciones: migraciones de datos de la librería ya aplicadas.</li>
 * </ul>
 */
public class EsquemaEventos implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(EsquemaEventos.class);

    /**
     * proximo_intento de los eventos aparcados: no se publican hasta liberarlos a mano
     * (UPDATE outbox_eventos SET proximo_intento = NULL WHERE id = ...). Cabe en un TIMESTAMP de MySQL.
     */
    static final Timestamp APARCADO = Timestamp.from(Instant.parse("2037-12-31T00:00:00Z"));

    /** Marca en eventos_migraciones: el aparcado se hace una sola vez por base de datos. */
    static final String MIGRACION_APARCADO = "outbox-aparcar-abandonados";

    /** Intentos tras los que la versión anterior del relay abandonaba un evento. */
    private static final int INTENTOS_ABANDONO_ANTERIOR = 10;

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
                    proximo_intento TIMESTAMP  NULL,
                    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
                )""");
        agregarColumnaSiFalta("outbox_eventos", "proximo_intento", "TIMESTAMP NULL");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS eventos_procesados (
                    event_id      VARCHAR(36) NOT NULL PRIMARY KEY,
                    tipo          VARCHAR(60) NOT NULL,
                    procesado_en  TIMESTAMP   NOT NULL
                )""");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS eventos_migraciones (
                    nombre        VARCHAR(100) NOT NULL PRIMARY KEY,
                    aplicada_en   TIMESTAMP    NOT NULL
                )""");
        if (!migracionAplicada(MIGRACION_APARCADO)) {
            aparcarAbandonados();
            registrarMigracion(MIGRACION_APARCADO);
        }
    }

    /** Para tablas creadas por versiones anteriores: CREATE TABLE IF NOT EXISTS no las modifica. */
    private void agregarColumnaSiFalta(String tabla, String columna, String definicion) {
        if (existeColumna(tabla, columna)) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
        } catch (DataAccessException e) {
            // Otra instancia que arrancaba a la vez pudo agregarla primero
            if (!existeColumna(tabla, columna)) {
                throw e;
            }
        }
    }

    private boolean migracionAplicada(String nombre) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM eventos_migraciones WHERE nombre = ?", Integer.class, nombre) > 0;
    }

    private void registrarMigracion(String nombre) {
        try {
            jdbcTemplate.update("INSERT INTO eventos_migraciones (nombre, aplicada_en) VALUES (?, ?)",
                    nombre, Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException e) {
            // Otra instancia que arrancaba a la vez la registró primero
        }
    }

    private boolean existeColumna(String tabla, String columna) {
        try {
            jdbcTemplate.queryForList("SELECT " + columna + " FROM " + tabla + " WHERE 1 = 0");
            return true;
        } catch (BadSqlGrammarException e) {
            return false;
        }
    }

    /**
     * La versión anterior del relay dejaba de intentar un evento tras 10 fallos. Esos eventos
     * pueden tener semanas y publicarlos ahora aplicaría cambios fuera de contexto (p. ej. un
     * check-in viejo dejaría ocupada una habitación libre): se aparcan para revisarlos a mano.
     * Corre antes de que exista el relay (que depende de este bean), así que ningún evento de la
     * versión nueva cae aquí; si se interrumpe, se repite en el siguiente arranque.
     */
    private void aparcarAbandonados() {
        int aparcados = jdbcTemplate.update(
                "UPDATE outbox_eventos SET proximo_intento = ? "
                        + "WHERE publicado_en IS NULL AND intentos >= ? AND proximo_intento IS NULL",
                APARCADO, INTENTOS_ABANDONO_ANTERIOR);
        if (aparcados > 0) {
            log.warn("{} evento(s) abandonados por la versión anterior del relay quedan aparcados en outbox_eventos "
                    + "(proximo_intento = 2037-12-31). Revísalos y libéralos con proximo_intento = NULL o bórralos.",
                    aparcados);
        }
    }
}
