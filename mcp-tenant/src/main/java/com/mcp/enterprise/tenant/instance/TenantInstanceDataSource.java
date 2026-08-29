package com.mcp.enterprise.tenant.instance;

import com.mcp.enterprise.tenant.TenantContext;
import com.mcp.enterprise.tenant.TenantNotResolvedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * Routing {@link DataSource} for V1.13 instance-level isolation.
 *
 * <p>Every {@code getConnection()} resolves the tenant id from
 * {@link TenantContext} (populated by the shared {@code X-Tenant-Id} filter -
 * business code stays unchanged across row/schema/instance modes), then looks
 * the tenant's dedicated pool up in the {@link TenantInstanceRegistry} and
 * delegates.</p>
 *
 * <p><b>Fail-closed</b>:</p>
 * <ul>
 *   <li>No tenant bound on the current thread → {@link TenantNotResolvedException}.</li>
 *   <li>Tenant not registered → {@link TenantNotResolvedException} (deny by default).</li>
 * </ul>
 *
 * <p>Because routing happens on every {@code getConnection()} call, runtime
 * {@code register}/{@code unregister} operations take effect immediately -
 * no restart required.</p>
 */
public class TenantInstanceDataSource implements DataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantInstanceDataSource.class);

    private final TenantInstanceRegistry registry;

    public TenantInstanceDataSource(TenantInstanceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return resolve().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        // Credentials are managed per tenant pool; the tenant id is the routing key.
        return resolve().getConnection(username, password);
    }

    /**
     * Fail-closed tenant resolution: current thread must have a tenant, and the
     * tenant must be registered.
     */
    private DataSource resolve() {
        String tenantId = TenantContext.getOrNull();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantNotResolvedException(
                    "No tenant resolved for instance-level access. Bind the tenant header "
                            + "(default X-Tenant-Id) or call TenantContext.set(tenantId) before accessing tenant data.");
        }
        DataSource ds = registry.get(tenantId); // throws when not registered
        log.debug("Routing tenant [{}] to dedicated datasource {}", tenantId, ds.getClass().getSimpleName());
        return ds;
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        // no-op: delegated pools manage their own logging
    }

    @Override
    public void setLoginTimeout(int seconds) {
        // no-op: per-tenant pool timeouts are configured per pool
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return java.util.logging.Logger.getLogger("com.mcp.enterprise.tenant");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }
}