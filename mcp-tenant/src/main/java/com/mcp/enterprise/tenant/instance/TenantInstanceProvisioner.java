package com.mcp.enterprise.tenant.instance;

import com.mcp.enterprise.tenant.McpTenantProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds and releases per-tenant {@link HikariDataSource} pools (V1.13
 * instance-level isolation).
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Create a dedicated HikariCP pool per tenant from
 *       {@link McpTenantProperties.TenantDatasource} (URL / account / pool quota).</li>
 *   <li>Resolve {@code ${ENV_VAR}} password placeholders (system property first,
 *       then environment variable - the same convention as Spring's relaxed
 *       binding so secrets never leak into git).</li>
 *   <li>Optionally execute per-tenant {@code initialize-ddl} exactly once per
 *       pool right after creation, so every tenant database starts with the
 *       same object layout.</li>
 *   <li>Close pools quietly on unregister/context shutdown.</li>
 * </ul>
 */
public class TenantInstanceProvisioner {

    private static final Logger log = LoggerFactory.getLogger(TenantInstanceProvisioner.class);

    /** Matches {@code ${ENV_VAR}} placeholders used for secrets. */
    private static final Pattern SECRET_PLACEHOLDER = Pattern.compile("^\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}$");

    private final String initializeDdl;

    public TenantInstanceProvisioner() {
        this("");
    }

    public TenantInstanceProvisioner(String initializeDdl) {
        this.initializeDdl = initializeDdl == null ? "" : initializeDdl;
    }

    /**
     * Creates a dedicated HikariCP pool for the tenant from the given spec.
     * The pool is fully configured (quota + timeout + lifetime) and, when
     * {@code initializeDdl} is configured, the DDL is applied once.
     */
    public DataSource provision(String tenantId, McpTenantProperties.TenantDatasource spec) {
        if (spec.getUrl() == null || spec.getUrl().isBlank()) {
            throw new IllegalArgumentException("Tenant '" + tenantId + "' instance config is missing 'url'");
        }
        HikariDataSource pool = new HikariDataSource();
        pool.setPoolName("mcp-tenant-" + sanitizePoolName(tenantId));
        pool.setJdbcUrl(spec.getUrl());
        pool.setUsername(spec.getUsername());
        pool.setPassword(resolveSecret(spec.getPassword()));
        if (spec.getDriverClassName() != null && !spec.getDriverClassName().isBlank()) {
            pool.setDriverClassName(spec.getDriverClassName());
        }
        McpTenantProperties.Pool poolCfg = spec.getPool();
        if (poolCfg != null) {
            pool.setMaximumPoolSize(poolCfg.getMaximumPoolSize());
            pool.setMinimumIdle(poolCfg.getMinimumIdle());
            pool.setConnectionTimeout(poolCfg.getConnectionTimeoutMs());
            pool.setMaxLifetime(poolCfg.getMaxLifetimeMs());
            pool.setIdleTimeout(poolCfg.getIdleTimeoutMs());
        }
        log.info("Provisioned instance pool for tenant [{}] (max={}, min={})",
                tenantId, pool.getMaximumPoolSize(), pool.getMinimumIdle());
        runInitializeDdl(tenantId, pool);
        return pool;
    }

    private void runInitializeDdl(String tenantId, HikariDataSource pool) {
        if (initializeDdl == null || initializeDdl.isBlank()) {
            return;
        }
        try (Connection connection = pool.getConnection()) {
            for (String ddl : initializeDdl.split(";")) {
                String trimmed = ddl.trim();
                if (!trimmed.isEmpty()) {
                    log.debug("Applying initialize DDL to tenant [{}]: {}", tenantId, trimmed);
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute(trimmed);
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("Initialize DDL failed for tenant [{}]: {}", tenantId, e.getMessage());
        }
    }

    /**
     * Resolves a {@code ${NAME}} placeholder: system property first, then the
     * environment variable. Non-placeholder values pass through unchanged.
     */
    public static String resolveSecret(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        Matcher matcher = SECRET_PLACEHOLDER.matcher(raw.trim());
        if (!matcher.matches()) {
            return raw;
        }
        String name = matcher.group(1);
        String fromProperty = System.getProperty(name);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        String fromEnv = System.getenv(name);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        log.warn("Secret placeholder ${{{}}} not found in system properties or environment", name);
        return raw; // let the pool fail at connect time with a clear driver error
    }

    /** Closes a pool/data source quietly (idempotent, never throws). */
    public static void closeQuietly(DataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        try {
            if (dataSource instanceof AutoCloseable closeable) {
                closeable.close();
            }
        } catch (Exception e) {
            log.warn("Failed to close datasource {}: {}", dataSource.getClass().getSimpleName(), e.getMessage());
        }
    }

    private static String sanitizePoolName(String tenantId) {
        return tenantId.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}