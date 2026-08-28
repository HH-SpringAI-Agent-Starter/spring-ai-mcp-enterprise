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

import javax.sql.DataSource;

/**
 * Auto-configuration for multi-tenant isolation.
 *
 * <h3>Row-level mode (default, V1.11)</h3>
 * <ul>
 *   <li>Registers {@link McpTenantFilter} (web applications only) to resolve the
 *       tenant id from the {@code X-Tenant-Id} header.</li>
 *   <li>Registers {@link TenantAwareJdbcTemplate} when a {@link JdbcTemplate} bean
 *       exists, making every JDBC operation fail-closed without a tenant.</li>
 * </ul>
 *
 * <h3>Schema-level mode (V1.12, {@code mcp.tenant.mode=schema})</h3>
 * <ul>
 *   <li>Registers {@link TenantSchemaManager} to map/provision tenant schemas.</li>
 *   <li>Registers {@link TenantSchemaDataSource} wrapping the primary
 *       {@link DataSource} - connections auto-switch schema per tenant.</li>
 * </ul>
 *
 * <p>Disabled by setting {@code mcp.tenant.enabled=false}.</p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "mcp.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpTenantProperties.class)
public class McpTenantAutoConfiguration {

    /**
     * Servlet filter resolving the tenant id from the request header and binding
     * it to {@link TenantContext} for the duration of the request.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<McpTenantFilter> mcpTenantFilterRegistration(McpTenantProperties properties) {
        FilterRegistrationBean<McpTenantFilter> registration = new FilterRegistrationBean<>(new McpTenantFilter(properties));
        registration.addUrlPatterns("/*");
        // After the security chain (HIGHEST_PRECEDENCE), before business filters.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /**
     * Row-level fail-closed JdbcTemplate (V1.11 mode).
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(TenantAwareJdbcTemplate.class)
    @ConditionalOnProperty(prefix = "mcp.tenant", name = "mode", havingValue = "row", matchIfMissing = true)
    public TenantAwareJdbcTemplate tenantAwareJdbcTemplate(JdbcTemplate jdbcTemplate, McpTenantProperties properties) {
        return new TenantAwareJdbcTemplate(jdbcTemplate, properties);
    }

    /**
     * Schema mapper/provisioner for schema-level mode (V1.12).
     */
    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(TenantSchemaManager.class)
    @ConditionalOnProperty(prefix = "mcp.tenant", name = "mode", havingValue = "schema")
    public TenantSchemaManager tenantSchemaManager(DataSource dataSource, McpTenantProperties properties) {
        return new TenantSchemaManager(dataSource, properties);
    }

    /**
     * Schema-switching DataSource for schema-level mode (V1.12). Wraps the
     * primary DataSource so every connection auto-switches the session schema
     * to the tenant's schema before any statement executes.
     */
    @Bean
    @ConditionalOnBean({DataSource.class, TenantSchemaManager.class})
    @ConditionalOnMissingBean(TenantSchemaDataSource.class)
    @ConditionalOnProperty(prefix = "mcp.tenant", name = "mode", havingValue = "schema")
    public TenantSchemaDataSource tenantSchemaDataSource(DataSource dataSource,
                                                         TenantSchemaManager tenantSchemaManager,
                                                         McpTenantProperties properties) {
        return new TenantSchemaDataSource(dataSource, tenantSchemaManager, properties);
    }
}