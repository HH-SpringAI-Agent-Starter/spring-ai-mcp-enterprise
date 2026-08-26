package com.mcp.enterprise.tenant;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auto-configuration for multi-tenant row-level isolation.
 *
 * <ul>
 *   <li>Registers {@link McpTenantFilter} (web applications only) to resolve the
 *       tenant id from the {@code X-Tenant-Id} header.</li>
 *   <li>Registers {@link TenantAwareJdbcTemplate} when a {@link JdbcTemplate} bean
 *       exists, making every JDBC operation fail-closed without a tenant.</li>
 * </ul>
 *
 * <p>Disabled by setting {@code mcp.tenant.enabled=false}.</p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "mcp.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpTenantProperties.class)
public class McpTenantAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<McpTenantFilter> mcpTenantFilterRegistration(McpTenantProperties properties) {
        FilterRegistrationBean<McpTenantFilter> registration = new FilterRegistrationBean<>(new McpTenantFilter(properties));
        registration.addUrlPatterns("/*");
        // After the security chain (which is at HIGHEST_PRECEDENCE), before business filters.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(TenantAwareJdbcTemplate.class)
    public TenantAwareJdbcTemplate tenantAwareJdbcTemplate(JdbcTemplate jdbcTemplate, McpTenantProperties properties) {
        return new TenantAwareJdbcTemplate(jdbcTemplate, properties);
    }
}