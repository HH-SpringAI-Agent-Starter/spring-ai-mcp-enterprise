package com.mcp.enterprise.tenant;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Statement;

/**
 * A {@link JdbcTemplate} that enforces tenant row-level isolation before every
 * database operation.
 *
 * <p><b>How it works:</b> Spring JDBC 6.x funnels every statement
 * (plain, prepared and callable, including the {@code update(...)}/{@code query(...)}
 * families) through the protected {@link #applyStatementSettings(Statement)} hook
 * right before execution. This class overrides that hook as the single
 * fail-closed choke point, so no code path can slip a tenant-scoped operation
 * through without a resolved tenant id.</p>
 *
 * <p>Behaviour (driven by {@link McpTenantProperties}):</p>
 * <ul>
 *   <li>{@code failClosed=true}: throws {@link TenantNotResolvedException} when no
 *       tenant is bound - guards against accidental cross-tenant writes/reads.</li>
 *   <li>{@code failClosed=false} + {@code defaultTenant}: binds the default tenant
 *       so legacy single-tenant code keeps working.</li>
 *   <li>The tenant id is tagged into SLF4J MDC ({@code mcp.tenantId}) for
 *       logs/metrics/audit correlation.</li>
 * </ul>
 *
 * <p>Business SQL should filter by the tenant column explicitly, e.g.:</p>
 * <pre>{@code
 * String tenantId = TenantContext.currentTenantOrThrow();
 * tenantAwareJdbcTemplate.update(
 *     "INSERT INTO app_data(tenant_id, payload) VALUES (?, ?)", tenantId, payload);
 * }</pre>
 *
 * <p>Note: raw {@code ConnectionCallback} usage bypasses statement creation and
 * therefore the guard - avoid it in tenant-scoped code.</p>
 */
public class TenantAwareJdbcTemplate extends JdbcTemplate {

    private final McpTenantProperties properties;

    /**
     * Wraps an existing {@link JdbcTemplate}, reusing its DataSource.
     */
    public TenantAwareJdbcTemplate(JdbcTemplate delegate, McpTenantProperties properties) {
        super(delegate.getDataSource());
        this.properties = properties;
    }

    /**
     * Convenience constructor for direct DataSource wiring.
     */
    public TenantAwareJdbcTemplate(DataSource dataSource, McpTenantProperties properties) {
        super(dataSource);
        this.properties = properties;
    }

    /**
     * Fail-closed guard, invoked once per statement creation regardless of the
     * calling API ({@code update}/{@code query}/{@code execute}/{@code batchUpdate}/
     * stored procedures).
     */
    @Override
    protected void applyStatementSettings(Statement stmt) throws java.sql.SQLException {
        enforceTenant();
        super.applyStatementSettings(stmt);
    }

    /**
     * Resolves (or optionally defaults) the tenant id before any data operation
     * reaches the driver.
     */
    private void enforceTenant() {
        if (!properties.isEnabled()) {
            return;
        }
        if (properties.isFailClosed()) {
            TenantContext.currentTenantOrThrow();
        } else if (!TenantContext.isResolved()
                && properties.getDefaultTenant() != null
                && !properties.getDefaultTenant().isBlank()) {
            TenantContext.set(properties.getDefaultTenant());
        }
    }
}