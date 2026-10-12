package com.staysync.tenant;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Basta con agregar la dependencia: el filtro se registra solo en cualquier servicio web.
 * Se puede apagar con staysync.tenant.filter.enabled=false.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "staysync.tenant.filter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TenantAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "tenantFilterRegistration")
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration() {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(new TenantFilter());
        registration.addUrlPatterns("/*");
        // Antes de Spring Security (-100) para que los logs de autorización ya lleven el hotel
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("tenantFilter");
        return registration;
    }
}
