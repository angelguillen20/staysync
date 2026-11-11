package com.staysync.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * En servicios con JPA: registra el resolver de hotel en Hibernate. Las entidades
 * se suman al filtrado con un campo {@code @TenantId}.
 */
@AutoConfiguration(before = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(name = "org.hibernate.context.spi.CurrentTenantIdentifierResolver")
public class TenantJpaAutoConfiguration {

    @Bean
    public TenantIdentifierResolver tenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    @Bean
    public HibernatePropertiesCustomizer tenantHibernateCustomizer(TenantIdentifierResolver resolver) {
        return props -> props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
