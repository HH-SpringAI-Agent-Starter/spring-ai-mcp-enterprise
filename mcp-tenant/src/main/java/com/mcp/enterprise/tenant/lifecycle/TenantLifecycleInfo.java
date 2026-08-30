package com.mcp.enterprise.tenant.lifecycle;

import com.mcp.enterprise.tenant.McpTenantProperties;

import java.time.Instant;

/**
 * Runtime view of one managed tenant instance (V1.14).
 *
 * <p>Combines the tenant's datasource specification (credentials aside) with
 * its lifecycle state and pool metadata. Exposed through the admin REST API
 * so operators can observe and manage tenant instances at runtime.</p>
 *
 * @param tenantId        unique tenant id
 * @param state           {@link TenantLifecycleState}
 * @param jdbcUrl         tenant database URL (spec, never the password)
 * @param username        database account
 * @param maximumPoolSize configured pool quota
 * @param minimumIdle     configured idle connections
 * @param createdAt       when the tenant was first managed (ISO-8601)
 * @param updatedAt       last state transition (ISO-8601)
 */
public record TenantLifecycleInfo(
        String tenantId,
        TenantLifecycleState state,
        String jdbcUrl,
        String username,
        int maximumPoolSize,
        int minimumIdle,
        Instant createdAt,
        Instant updatedAt) {

    /** Builds an immutable snapshot from a managed tenant + spec. */
    public static TenantLifecycleInfo of(String tenantId,
                                         TenantLifecycleState state,
                                         McpTenantProperties.TenantDatasource spec,
                                         Instant createdAt,
                                         Instant updatedAt) {
        return new TenantLifecycleInfo(
                tenantId,
                state,
                spec == null ? "" : spec.getUrl(),
                spec == null ? "" : spec.getUsername(),
                spec == null || spec.getPool() == null ? 0 : spec.getPool().getMaximumPoolSize(),
                spec == null || spec.getPool() == null ? 0 : spec.getPool().getMinimumIdle(),
                createdAt,
                updatedAt);
    }
}