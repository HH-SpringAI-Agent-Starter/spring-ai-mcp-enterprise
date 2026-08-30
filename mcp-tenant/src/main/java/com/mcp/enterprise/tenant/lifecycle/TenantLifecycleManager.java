package com.mcp.enterprise.tenant.lifecycle;

import com.mcp.enterprise.tenant.McpTenantProperties;
import com.mcp.enterprise.tenant.instance.TenantInstanceProvisioner;
import com.mcp.enterprise.tenant.instance.TenantInstanceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime tenant lifecycle management (V1.14): provision / resume / suspend /
 * teardown of instance-level tenant datasources <em>without a restart</em>.
 *
 * <p>This manager is the operational counterpart of the V1.13 static
 * {@code mcp.tenant.instance.tenants} configuration. It consumes the same
 * {@link TenantInstanceRegistry} and {@link TenantInstanceProvisioner} beans,
 * so regulated operations behave identically to startup-provisioned tenants
 * (fail-closed routing, per-tenant pool quotas, initialize-DDL, secret
 * placeholder resolution).</p>
 *
 * <p>State model:</p>
 * <ul>
 *   <li><b>ACTIVE</b> - a dedicated {@link DataSource} is registered in the
 *       registry; tenant traffic is served.</li>
 *   <li><b>SUSPENDED</b> - the pool is released and the tenant is fail-closed
 *       (traffic denied), but the datasource spec is retained so
 *       {@link #resume(String)} can restore the tenant without re-supplying
 *       credentials.</li>
 * </ul>
 *
 * <p>Thread safety: the manager is backed by a {@link ConcurrentHashMap};
 * per-tenant transitions are synchronized on the tenant's monitor, so
 * concurrent admin calls cannot corrupt a tenant's state or leak pools.</p>
 */
public class TenantLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(TenantLifecycleManager.class);

    private final TenantInstanceRegistry registry;
    private final TenantInstanceProvisioner provisioner;
    private final Map<String, ManagedTenant> tenants = new ConcurrentHashMap<>();

    /** Internal holder combining spec, live datasource and lifecycle metadata. */
    private static final class ManagedTenant {
        volatile McpTenantProperties.TenantDatasource spec;
        volatile TenantLifecycleState state;
        volatile DataSource dataSource;
        final Instant createdAt;
        volatile Instant updatedAt;

        ManagedTenant(McpTenantProperties.TenantDatasource spec,
                      TenantLifecycleState state,
                      DataSource dataSource) {
            this.spec = spec;
            this.state = state;
            this.dataSource = dataSource;
            this.createdAt = Instant.now();
            this.updatedAt = this.createdAt;
        }

        TenantLifecycleInfo snapshot(String tenantId) {
            return TenantLifecycleInfo.of(tenantId, state, spec, createdAt, updatedAt);
        }
    }

    public TenantLifecycleManager(TenantInstanceRegistry registry,
                                  TenantInstanceProvisioner provisioner) {
        this.registry = registry;
        this.provisioner = provisioner;
    }

    /**
     * Provisions (or re-provisions) a tenant instance at runtime. If the
     * tenant is already ACTIVE, the old pool is replaced (previous pool is
     * closed). If the tenant is SUSPENDED, it is reactivated with the new spec.
     *
     * @return snapshot of the tenant after provisioning
     */
    public TenantLifecycleInfo provision(String tenantId,
                                         McpTenantProperties.TenantDatasource spec) {
        validateTenantId(tenantId);
        if (spec == null) {
            throw new IllegalArgumentException("Datasource spec must not be null for tenant '" + tenantId + "'");
        }
        ManagedTenant holder = tenants.compute(tenantId, (id, existing) -> {
            DataSource pool = provisioner.provision(id, spec);
            if (existing != null) {
                // Replace previous pool (registry handles closing the old one).
                registry.register(id, pool);
                existing.dataSource = pool;
                existing.spec = spec;
                existing.state = TenantLifecycleState.ACTIVE;
                existing.updatedAt = Instant.now();
                return existing;
            }
            registry.register(id, pool);
            return new ManagedTenant(spec, TenantLifecycleState.ACTIVE, pool);
        });
        log.info("Provisioned tenant [{}] at runtime (state=ACTIVE)", tenantId);
        return holder.snapshot(tenantId);
    }

    /**
     * Suspends a tenant: releases its connection pool and blocks data access
     * (fail-closed). The datasource spec is retained for later resume.
     *
     * @return snapshot of the suspended tenant
     * @throws IllegalStateException when the tenant is not managed
     */
    public TenantLifecycleInfo suspend(String tenantId) {
        validateTenantId(tenantId);
        ManagedTenant holder = tenants.get(tenantId);
        if (holder == null) {
            throw new IllegalStateException("Tenant '" + tenantId + "' is not managed. "
                    + "Provision it first (POST /api/admin/tenants).");
        }
        synchronized (holder) {
            if (holder.state == TenantLifecycleState.SUSPENDED) {
                return holder.snapshot(tenantId); // idempotent
            }
            registry.unregister(tenantId);
            holder.dataSource = null;
            holder.state = TenantLifecycleState.SUSPENDED;
            holder.updatedAt = Instant.now();
        }
        log.info("Suspended tenant [{}] - pool released, access fail-closed", tenantId);
        return holder.snapshot(tenantId);
    }

    /**
     * Resumes a suspended tenant using its retained datasource spec.
     *
     * @return snapshot of the reactivated tenant
     * @throws IllegalStateException when the tenant is not managed
     */
    public TenantLifecycleInfo resume(String tenantId) {
        validateTenantId(tenantId);
        ManagedTenant holder = tenants.get(tenantId);
        if (holder == null) {
            throw new IllegalStateException("Tenant '" + tenantId + "' is not managed. "
                    + "Provision it first (POST /api/admin/tenants).");
        }
        synchronized (holder) {
            if (holder.state == TenantLifecycleState.ACTIVE) {
                return holder.snapshot(tenantId); // idempotent
            }
            DataSource pool = provisioner.provision(tenantId, holder.spec);
            registry.register(tenantId, pool);
            holder.dataSource = pool;
            holder.state = TenantLifecycleState.ACTIVE;
            holder.updatedAt = Instant.now();
        }
        log.info("Resumed tenant [{}] - instance datasource re-registered", tenantId);
        return holder.snapshot(tenantId);
    }

    /**
     * Tears a tenant down: releases the pool and forgets the tenant entirely.
     * A subsequent request must provision the tenant again from scratch.
     *
     * @return {@code true} when the tenant existed and was removed
     */
    public boolean remove(String tenantId) {
        validateTenantId(tenantId);
        ManagedTenant removed = tenants.remove(tenantId);
        if (removed != null) {
            registry.unregister(tenantId);
            log.info("Removed tenant [{}] - instance datasource torn down", tenantId);
            return true;
        }
        return false;
    }

    /**
     * Snapshot of one managed tenant, if present.
     */
    public Optional<TenantLifecycleInfo> get(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        ManagedTenant holder = tenants.get(tenantId);
        return holder == null ? Optional.empty() : Optional.of(holder.snapshot(tenantId));
    }

    /**
     * All managed tenants, ordered by created-at for stable admin listings.
     */
    public List<TenantLifecycleInfo> list() {
        return tenants.entrySet().stream()
                .map(e -> e.getValue().snapshot(e.getKey()))
                .sorted(Comparator.comparing(TenantLifecycleInfo::createdAt))
                .toList();
    }

    /** Number of managed tenants (including suspended ones). */
    public int count() {
        return tenants.size();
    }

    /** True when the tenant is managed and currently ACTIVE. */
    public boolean isActive(String tenantId) {
        ManagedTenant holder = tenants.get(tenantId);
        return holder != null && holder.state == TenantLifecycleState.ACTIVE;
    }

    private static void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant id must not be blank");
        }
    }
}