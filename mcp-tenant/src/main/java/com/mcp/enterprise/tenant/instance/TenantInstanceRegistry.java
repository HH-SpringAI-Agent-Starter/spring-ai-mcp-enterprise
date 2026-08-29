package com.mcp.enterprise.tenant.instance;

import javax.sql.DataSource;
import java.util.Set;

/**
 * Registry mapping tenant ids to their dedicated physical
 * {@link DataSource} (V1.13 instance-level isolation).
 *
 * <p>Each entry owns its connection pool. {@link #unregister(String)} must
 * release the pool (fail-safe close), and {@link #close()} releases every
 * pool (invoked by Spring on context shutdown via the auto-configuration's
 * {@code destroyMethod}).</p>
 *
 * <p>This registry is the extension point for the upcoming tenant lifecycle
 * management REST API (V1.14): provisioning, suspension and teardown of
 * tenant instances without a restart.</p>
 */
public interface TenantInstanceRegistry extends AutoCloseable {

    /**
     * Resolves the tenant's dedicated {@link DataSource}.
     *
     * @throws com.mcp.enterprise.tenant.TenantNotResolvedException when the
     *         tenant is not registered (fail-closed: unknown tenant equals
     *         denied access).
     */
    DataSource get(String tenantId);

    /** True when a datasource is currently registered for the tenant. */
    boolean isRegistered(String tenantId);

    /**
     * Registers (or replaces) the tenant's datasource at runtime.
     * A replaced datasource is closed automatically to avoid pool leaks.
     */
    void register(String tenantId, DataSource dataSource);

    /**
     * Unregisters the tenant and closes its connection pool.
     *
     * @return the previously registered datasource, or {@code null}.
     */
    DataSource unregister(String tenantId);

    /** All currently registered tenant ids. */
    Set<String> tenants();

    /** Releases every registered pool. Idempotent. */
    @Override
    void close();
}