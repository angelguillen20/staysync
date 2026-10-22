package com.staysync.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/** Registra {@link TenantMigracion} una vez que existen el EntityManagerFactory y el JdbcTemplate. */
@AutoConfiguration(after = {HibernateJpaAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@ConditionalOnClass(name = "org.hibernate.context.spi.CurrentTenantIdentifierResolver")
@ConditionalOnBean({EntityManagerFactory.class, JdbcTemplate.class})
@ConditionalOnProperty(prefix = "staysync.tenant.migracion", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TenantMigracionAutoConfiguration {

    @Bean
    public TenantMigracion tenantMigracion(EntityManagerFactory emf, JdbcTemplate jdbcTemplate,
                                           @Value("${staysync.tenant.default-id:staysync-demo}") String porDefecto,
                                           @Value("${staysync.tenant.unicos-por-hotel:}") List<String> unicos) {
        return new TenantMigracion(emf, jdbcTemplate, porDefecto, unicos);
    }
}
