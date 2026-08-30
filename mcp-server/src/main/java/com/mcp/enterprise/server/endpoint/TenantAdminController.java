package com.mcp.enterprise.server.endpoint;

import com.mcp.enterprise.tenant.McpTenantProperties;
import com.mcp.enterprise.tenant.lifecycle.TenantLifecycleInfo;
import com.mcp.enterprise.tenant.lifecycle.TenantLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * V1.14 tenant lifecycle management REST API.
 *
 * <p>Runtime operations over instance-level tenant isolation (no restart
 * needed): provision, resume, suspend and teardown of dedicated tenant
 * datasources. Consumes {@link TenantLifecycleManager}, which shares the same
 * {@code TenantInstanceRegistry} as startup-provisioned tenants, so managed
 * tenants are indistinguishable from statically configured ones from the
 * data-access layer's perspective.</p>
 *
 * <pre>
 *   GET    /api/admin/tenants              - list all managed tenants
 *   GET    /api/admin/tenants/{id}         - tenant detail
 *   POST   /api/admin/tenants              - provision (create/replace) tenant
 *   POST   /api/admin/tenants/{id}/suspend - suspend tenant (release pool)
 *   POST   /api/admin/tenants/{id}/resume  - resume tenant (re-provision pool)
 *   DELETE /api/admin/tenants/{id}         - teardown tenant permanently
 * </pre>
 *
 * <p>Security: this controller lives under {@code /api/admin/*} like the other
 * admin endpoints and is expected to be protected by the same admin
 * authentication / network policy (see mcp-auth). It must never be exposed
 * publicly - it grants runtime data-access rights to tenants.</p>
 */
@RestController
@RequestMapping("/api/admin/tenants")
public class TenantAdminController {

    private static final Logger log = LoggerFactory.getLogger(TenantAdminController.class);

    private final TenantLifecycleManager lifecycleManager;

    public TenantAdminController(TenantLifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
    }

    /** Lists every managed tenant (ACTIVE and SUSPENDED), newest last. */
    @GetMapping
    public List<TenantLifecycleInfo> listTenants() {
        return lifecycleManager.list();
    }

    /** Detail of one managed tenant. */
    @GetMapping("/{tenantId}")
    public Map<String, Object> getTenant(@PathVariable String tenantId) {
        Optional<TenantLifecycleInfo> info = lifecycleManager.get(tenantId);
        if (info.isEmpty()) {
            throw new TenantNotFoundException(tenantId);
        }
        return Map.of("tenant", info.get());
    }

    /**
     * Provisions (or re-provisions) a tenant instance at runtime. Re-provision
     * replaces the current pool; a suspended tenant is reactivated with the
     * supplied spec.
     *
     * <p>Request body (application/json):</p>
     * <pre>{@code
     * {
     *   "url": "jdbc:mysql://db-acme:3306/mcp",
     *   "username": "acme_app",
     *   "password": "${ACME_DB_PASSWORD}",
     *   "pool": { "maximum-pool-size": 10, "minimum-idle": 2 }
     * }
     * }</pre>
     */
    @PostMapping
    public Map<String, Object> provisionTenant(@RequestBody TenantProvisionRequest request) {
        McpTenantProperties.TenantDatasource spec = new McpTenantProperties.TenantDatasource();
        spec.setUrl(request.url());
        spec.setUsername(request.username());
        spec.setPassword(request.password() == null ? "" : request.password());
        spec.setDriverClassName(request.driverClassName() == null ? "" : request.driverClassName());
        if (request.pool() != null) {
            McpTenantProperties.Pool pool = new McpTenantProperties.Pool();
            if (request.pool().maximumPoolSize() != null) {
                pool.setMaximumPoolSize(request.pool().maximumPoolSize());
            }
            if (request.pool().minimumIdle() != null) {
                pool.setMinimumIdle(request.pool().minimumIdle());
            }
            if (request.pool().connectionTimeoutMs() != null) {
                pool.setConnectionTimeoutMs(request.pool().connectionTimeoutMs());
            }
            if (request.pool().maxLifetimeMs() != null) {
                pool.setMaxLifetimeMs(request.pool().maxLifetimeMs());
            }
            if (request.pool().idleTimeoutMs() != null) {
                pool.setIdleTimeoutMs(request.pool().idleTimeoutMs());
            }
            spec.setPool(pool);
        }
        TenantLifecycleInfo info = lifecycleManager.provision(request.tenantId(), spec);
        log.info("Tenant [{}] provisioned via admin API", request.tenantId());
        return Map.of("tenant", info);
    }

    /** Suspends a tenant: releases its pool and fail-closes data access. */
    @PostMapping("/{tenantId}/suspend")
    public Map<String, Object> suspendTenant(@PathVariable String tenantId) {
        TenantLifecycleInfo info = lifecycleManager.suspend(tenantId);
        log.info("Tenant [{}] suspended via admin API", tenantId);
        return Map.of("tenant", info);
    }

    /** Resumes a suspended tenant using its retained datasource spec. */
    @PostMapping("/{tenantId}/resume")
    public Map<String, Object> resumeTenant(@PathVariable String tenantId) {
        TenantLifecycleInfo info = lifecycleManager.resume(tenantId);
        log.info("Tenant [{}] resumed via admin API", tenantId);
        return Map.of("tenant", info);
    }

    /** Tears a tenant down permanently (pool released, spec forgotten). */
    @DeleteMapping("/{tenantId}")
    public Map<String, Object> removeTenant(@PathVariable String tenantId) {
        boolean removed = lifecycleManager.remove(tenantId);
        if (!removed) {
            throw new TenantNotFoundException(tenantId);
        }
        log.info("Tenant [{}] removed via admin API", tenantId);
        return Map.of("removed", true, "tenantId", tenantId);
    }

    /** Request body for {@link #provisionTenant}. */
    public record TenantProvisionRequest(
            String tenantId,
            String url,
            String username,
            String password,
            String driverClassName,
            PoolRequest pool) {
    }

    /** Optional per-tenant pool quota overrides. */
    public record PoolRequest(Integer maximumPoolSize,
                              Integer minimumIdle,
                              Long connectionTimeoutMs,
                              Long maxLifetimeMs,
                              Long idleTimeoutMs) {
    }

    /** 404 for unknown tenants. */
    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(TenantNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    /** 400 for invalid provision payloads (blank tenant id, missing url). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    /** 409 for state conflicts (e.g. suspending an unknown tenant). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    /** 404 for unknown tenants. */
    static class TenantNotFoundException extends RuntimeException {
        TenantNotFoundException(String tenantId) {
            super("Tenant '" + tenantId + "' is not managed");
        }
    }
}