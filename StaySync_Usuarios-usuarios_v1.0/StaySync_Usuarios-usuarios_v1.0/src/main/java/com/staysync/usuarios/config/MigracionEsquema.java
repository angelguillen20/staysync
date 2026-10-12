package com.staysync.usuarios.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ajustes de esquema que ddl-auto=update no hace por sí solo. Corre antes de DataInitializer.
 *
 * <p>Hibernate 6 crea los @Enumerated(STRING) como ENUM nativo de MySQL y ddl-auto nunca
 * modifica columnas existentes: sin esto, guardar el rol SUPER_ADMIN fallaría con
 * "Data truncated for column 'rol'". Es idempotente y no hace nada en H2 (tests).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class MigracionEsquema implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> tipo = jdbcTemplate.queryForList(
                    "SELECT DATA_TYPE FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuarios' AND COLUMN_NAME = 'rol'",
                    String.class);
            if (!tipo.isEmpty() && "enum".equalsIgnoreCase(tipo.get(0))) {
                jdbcTemplate.execute("ALTER TABLE usuarios MODIFY rol VARCHAR(20) NOT NULL");
                log.info("Columna usuarios.rol migrada de ENUM a VARCHAR(20)");
            }
        } catch (DataAccessException e) {
            log.warn("No se pudo revisar el tipo de usuarios.rol: {}", e.getMessage());
        }
    }
}
