package com.staysync.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.annotations.TenantId;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Migración al arrancar, para bases que ya tenían datos antes del multi-tenant
 * (ddl-auto=update agrega la columna tenant_id, pero vacía):
 * <ol>
 *   <li>Asigna el hotel por defecto a las filas sin tenant_id en cada tabla con {@code @TenantId}.</li>
 *   <li>Elimina los índices únicos de una sola columna listados en
 *       staysync.tenant.unicos-por-hotel (p. ej. habitaciones.numero), que pasan a ser
 *       únicos por hotel. Solo en MySQL.</li>
 * </ol>
 * Idempotente: en arranques posteriores no encuentra nada que cambiar.
 */
public class TenantMigracion implements ApplicationRunner, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TenantMigracion.class);
    static final String COLUMNA = "tenant_id";

    private final EntityManagerFactory entityManagerFactory;
    private final JdbcTemplate jdbcTemplate;
    private final String tenantPorDefecto;
    private final List<String> unicosPorHotel;

    public TenantMigracion(EntityManagerFactory entityManagerFactory, JdbcTemplate jdbcTemplate,
                           String tenantPorDefecto, List<String> unicosPorHotel) {
        this.entityManagerFactory = entityManagerFactory;
        this.jdbcTemplate = jdbcTemplate;
        this.tenantPorDefecto = tenantPorDefecto;
        this.unicosPorHotel = unicosPorHotel;
    }

    /** Antes que los seeders de cada servicio, para que no mezclen filas con y sin hotel. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String tabla : tablasConTenant()) {
            asignarHotelPorDefecto(tabla);
        }
        for (String tablaColumna : unicosPorHotel) {
            String[] partes = tablaColumna.trim().split("\\.");
            if (partes.length == 2) {
                eliminarUnicoSimple(partes[0], partes[1]);
            }
        }
    }

    /** Tablas de las entidades que tienen un campo anotado con @TenantId. */
    List<String> tablasConTenant() {
        List<String> tablas = new ArrayList<>();
        SessionFactoryImplementor sf = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        sf.getMappingMetamodel().forEachEntityDescriptor(persister -> {
            if (persister instanceof AbstractEntityPersister aep && tieneTenantId(persister.getMappedClass())) {
                tablas.add(aep.getTableName());
            }
        });
        return tablas;
    }

    private static boolean tieneTenantId(Class<?> clase) {
        for (Class<?> c = clase; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(TenantId.class)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void asignarHotelPorDefecto(String tabla) {
        try {
            int filas = jdbcTemplate.update(
                    "UPDATE " + tabla + " SET " + COLUMNA + " = ? WHERE " + COLUMNA + " IS NULL OR " + COLUMNA + " = ''",
                    tenantPorDefecto);
            if (filas > 0) {
                log.info("{} fila(s) de '{}' asignadas al hotel '{}'", filas, tabla, tenantPorDefecto);
            }
        } catch (DataAccessException e) {
            log.warn("No se pudo asignar hotel por defecto en '{}': {}", tabla, e.getMessage());
        }
    }

    private void eliminarUnicoSimple(String tabla, String columna) {
        try {
            List<String> indices = jdbcTemplate.queryForList("""
                    SELECT s.INDEX_NAME FROM information_schema.STATISTICS s
                    WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = ? AND s.NON_UNIQUE = 0
                      AND s.INDEX_NAME <> 'PRIMARY' AND s.COLUMN_NAME = ?
                      AND (SELECT COUNT(*) FROM information_schema.STATISTICS x
                           WHERE x.TABLE_SCHEMA = s.TABLE_SCHEMA AND x.TABLE_NAME = s.TABLE_NAME
                             AND x.INDEX_NAME = s.INDEX_NAME) = 1
                    """, String.class, tabla, columna);
            for (String indice : indices) {
                jdbcTemplate.execute("ALTER TABLE " + tabla + " DROP INDEX `" + indice + "`");
                log.info("Índice único '{}' en {}.{} eliminado: ahora es único por hotel", indice, tabla, columna);
            }
        } catch (DataAccessException e) {
            // H2 (tests) no tiene esta vista con la misma forma; la tabla se crea ya con el único por hotel
            log.debug("No se revisaron los únicos de {}.{}: {}", tabla, columna, e.getMessage());
        }
    }
}
