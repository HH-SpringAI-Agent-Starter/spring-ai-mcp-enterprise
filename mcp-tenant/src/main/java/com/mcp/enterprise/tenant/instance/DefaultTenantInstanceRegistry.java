package com.mcp.enterprise.tenant.instance;

import com.mcp.enterprise.tenant.TenantNotResolvedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default thread-safe {@link TenantInstanceRegistry} backed by a
 * {@link ConcurrentHashMap}.
 *
 * <p>Fail-closed semantics: {@link #get(String)} on an unknown tenant throws
 * {@link TenantNotResolvedException} - an unregistered tenant is treated as
 * "no access", never as "fall back to a shared datasource".</p>
 *
 * <p>Lifecycle safety:</p>
 * <ul>
 *   <li>{@link #unregister(String)} closes the removed pool (idempotent - a
 *       second unregister is a no-op).</li>
 *   <li>{@link #register(String, DataSource)} on an existing tenant closes the
 *       previous pool before installing the replacement (no pool leak).</li>
 *   <li>{@link #close()} closes every pool and clears the map.</li>
 * </ul>
 */
public class DefaultTenantInstanceRegistry implements TenantInstanceRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultTenantInstanceRegistry.class);

    private final ConcurrentHashMap<String, DataSource> tenants = new ConcurrentHashMap<>();

    @Override
    public DataSource get(String tenantId) {
        DataSource ds = tenants.get(tenantId);
        if (ds == null) {
            throw new TenantNotResolvedException(
                    "Tenant '" + tenantId + "' is not registered in the instance registry. "
                            + "Provision the tenant first (mcp.tenant.instance.tenants or "
                            + "TenantInstanceRegistry.register). Fail-closed: unregistered tenant = denied.");
        }
        return ds;
    }

    @Override
    public boolean isRegistered(String tenantId) {
        return tenantId != null && tenants.containsKey(tenantId);
    }

    @Override
    public void register(String tenantId, DataSource dataSource) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant id must not be blank");
        }
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource must not be null for tenant '" + tenantId + "'");
        }
        DataSource previous = tenants.put(tenantId, dataSource);
        if (previous != null && previous != dataSource) {
            log.info("Replacing datasource for tenant [{}] - closing previous pool", tenantId);
            TenantInstanceProvisioner.closeQuietly(previous);
        }
        log.info("Registered instance datasource for tenant [{}] ({})", tenantId, dataSource.getClass().getSimpleName());
    }

    @Override
    public DataSource unregister(String tenantId) {
        DataSource removed = tenants.remove(tenantId);
        if (removed != null) {
            log.info("Unregistering tenant [{}] - closing its connection pool", tenantId);
            TenantInstanceProvisioner.closeQuietly(removed);
        }
        return removed;
    }

    @Override
    public Set<String> tenants() {
        return Set.copyOf(tenants.keySet());
    }

    @Override
    public void close() {
        tenants.keySet().forEach(this::unregister);
        log.info("Instance registry closed - all tenant pools released");
    }
}