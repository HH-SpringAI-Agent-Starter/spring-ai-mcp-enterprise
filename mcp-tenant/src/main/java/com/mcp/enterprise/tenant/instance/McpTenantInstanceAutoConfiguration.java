package com.mcp.enterprise.tenant.instance;

import com.mcp.enterprise.tenant.McpTenantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Auto-configuration for V1.13 instance-level tenant isolation
 * ({@code mcp.tenant.mode=instance}).
 *
 * <p>Wires:</p>
 * <ul>
 *   <li>{@link TenantInstanceRegistry} - populated from the static
 *       {@code mcp.tenant.instance.tenants} map; runtime register/unregister
 *       stays possible afterwards via the same bean.</li>
 *   <li>{@link TenantInstanceDataSource} - the routing {@link DataSource}
 *       consumed by JdbcTemplate/MyBatis/etc. in instance mode.</li>
 * </ul>
 *
 * <p>The registry bean is declared with {@code destroyMethod="close"} so every
 * tenant pool is released on application shutdown.</p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "mcp.tenant", name = "mode", havingValue = "instance")
@EnableConfigurationProperties(McpTenantProperties.class)
public class McpTenantInstanceAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpTenantInstanceAutoConfiguration.class);

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(TenantInstanceRegistry.class)
    @ConditionalOnProperty(prefix = "mcp.tenant.instance", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TenantInstanceRegistry tenantInstanceRegistry(McpTenantProperties properties) {
        McpTenantProperties.Instance instanceProps = properties.getInstance();
        TenantInstanceProvisioner provisioner = new TenantInstanceProvisioner(instanceProps.getInitializeDdl());
        DefaultTenantInstanceRegistry registry = new DefaultTenantInstanceRegistry();
        if (instanceProps.getTenants() != null) {
            instanceProps.getTenants().forEach((tenantId, spec) -> {
                try {
                    registry.register(tenantId, provisioner.provision(tenantId, spec));
                } catch (RuntimeException e) {
                    // Fail-fast on misconfigured static tenants: a silently
                    // missing tenant pool would break fail-closed guarantees.
                    throw new IllegalStateException(
                            "Failed to provision instance datasource for tenant '" + tenantId
                                    + "': " + e.getMessage(), e);
                }
            });
        }
        log.info("Instance-level multi-tenancy active: {} static tenant pool(s) provisioned",
                registry.tenants().size());
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean(TenantInstanceDataSource.class)
    @ConditionalOnProperty(prefix = "mcp.tenant.instance", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TenantInstanceDataSource tenantInstanceDataSource(TenantInstanceRegistry registry) {
        return new TenantInstanceDataSource(registry);
    }
}