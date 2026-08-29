package com.mcp.enterprise.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for multi-tenant isolation.
 *
 * <p>Prefix: {@code mcp.tenant}</p>
 *
 * <p>Three isolation modes are supported (see {@link Mode}):</p>
 * <ul>
 *   <li>{@code row} (default): row-level isolation - every JDBC statement is
 *       fail-closed guarded by {@link TenantAwareJdbcTemplate}; business SQL
 *       filters by a tenant column.</li>
 *   <li>{@code schema}: schema-level isolation - the DataSource automatically
 *       switches the database schema per tenant (PostgreSQL {@code search_path},
 *       MySQL {@code USE}, H2 {@code SET SCHEMA}); schemas are provisioned on
 *       first use. Ideal for strong isolation at the platform level.</li>
 *   <li>{@code instance}: instance-level isolation (V1.13) - every tenant gets
 *       its own physical {@link javax.sql.DataSource} with an independent
 *       connection pool (and optionally an independent database/instance).
 *       The highest isolation level: physical fault domains, per-tenant
 *       resource quotas and data-residency compliance (finance / government /
 *       healthcare). See {@code com.mcp.enterprise.tenant.instance}.</li>
 * </ul>
 *
 * <pre>{@code
 * mcp:
 *   tenant:
 *     enabled: true            # master switch (default true)
 *     header-name: X-Tenant-Id # header carrying the tenant id (default X-Tenant-Id)
 *     fail-closed: true        # reject data access without a tenant (default true)
 *     default-tenant: ""       # optional fallback when failClosed=false
 *     mode: row                # row | schema | instance (default row)
 *     schema:
 *       prefix: tenant_        # schema name prefix, e.g. tenant_acme
 *       dialect: auto          # auto | postgresql | mysql | h2 | generic
 *       provision-on-first-use: true  # CREATE SCHEMA IF NOT EXISTS on first access
 *       template-ddl: ""       # optional DDL executed after schema creation (e.g.
 *                              #   "CREATE TABLE IF NOT EXISTS app_data(id BIGINT AUTO_INCREMENT PRIMARY KEY, payload VARCHAR(500));")
 *     instance:                # used when mode=instance (V1.13)
 *       enabled: true          # master switch for instance-mode wiring
 *       initialize-ddl: ""     # optional DDL executed once per pool on registration
 *       tenants:
 *         acme-corp:
 *           url: jdbc:mysql://db-acme:3306/mcp
 *           username: acme_app
 *           password: ${ACME_DB_PASSWORD}   # ${ENV} placeholders are resolved
 *           pool:
 *             maximum-pool-size: 10
 *             minimum-idle: 2
 *             connection-timeout-ms: 30000
 *             max-lifetime-ms: 1800000
 * }</pre>
 */
@ConfigurationProperties(prefix = "mcp.tenant")
public class McpTenantProperties {

    public enum Mode {
        /** Row-level isolation (V1.11, default). */
        ROW,
        /** Schema-level isolation (V1.12). */
        SCHEMA,
        /** Instance-level isolation (V1.13): per-tenant DataSource + connection pool. */
        INSTANCE
    }

    public enum SchemaDialect {
        /** Detect from JDBC driver metadata. */
        AUTO,
        POSTGRESQL,
        MYSQL,
        H2,
        GENERIC
    }

    /** Master switch for tenant enforcement. */
    private boolean enabled = true;

    /** HTTP header name that carries the tenant id. */
    private String headerName = "X-Tenant-Id";

    /**
     * Fail closed: reject tenant-scoped data access when no tenant is resolved.
     * Safe default for multi-tenant deployments - prevents cross-tenant leaks.
     */
    private boolean failClosed = true;

    /**
     * Optional fallback tenant id applied when {@code failClosed=false} and no
     * tenant header is present. Leave empty to keep denying tenant-scoped access.
     */
    private String defaultTenant = "";

    /** Isolation mode: {@code row} (default), {@code schema} or {@code instance}. */
    private Mode mode = Mode.ROW;

    /** Schema-level isolation settings (used when {@code mode=SCHEMA}). */
    private Schema schema = new Schema();

    /** Instance-level isolation settings (used when {@code mode=INSTANCE}). */
    private Instance instance = new Instance();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public boolean isFailClosed() {
        return failClosed;
    }

    public void setFailClosed(boolean failClosed) {
        this.failClosed = failClosed;
    }

    public String getDefaultTenant() {
        return defaultTenant;
    }

    public void setDefaultTenant(String defaultTenant) {
        this.defaultTenant = defaultTenant;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Schema getSchema() {
        return schema;
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    public Instance getInstance() {
        return instance;
    }

    public void setInstance(Instance instance) {
        this.instance = instance;
    }

    /**
     * Schema-level isolation settings.
     */
    public static class Schema {

        /** Schema name prefix, e.g. {@code tenant_} -&gt; {@code tenant_acme}. */
        private String prefix = "tenant_";

        /** Dialect for the schema switch statement. {@code auto} resolves from the driver. */
        private SchemaDialect dialect = SchemaDialect.AUTO;

        /** Creates the schema on first access when missing. */
        private boolean provisionOnFirstUse = true;

        /**
         * Optional DDL (semicolon-separated) executed right after schema creation,
         * so each tenant schema starts with the same object layout. Example:
         * {@code "CREATE TABLE IF NOT EXISTS app_data(id BIGINT AUTO_INCREMENT PRIMARY KEY, payload VARCHAR(500));"}
         */
        private String templateDdl = "";

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public SchemaDialect getDialect() {
            return dialect;
        }

        public void setDialect(SchemaDialect dialect) {
            this.dialect = dialect;
        }

        public boolean isProvisionOnFirstUse() {
            return provisionOnFirstUse;
        }

        public void setProvisionOnFirstUse(boolean provisionOnFirstUse) {
            this.provisionOnFirstUse = provisionOnFirstUse;
        }

        public String getTemplateDdl() {
            return templateDdl;
        }

        public void setTemplateDdl(String templateDdl) {
            this.templateDdl = templateDdl;
        }
    }

    /**
     * Instance-level isolation settings (V1.13, used when {@code mode=INSTANCE}).
     *
     * <p>Every configured tenant gets its own physical {@link javax.sql.DataSource}
     * with an independent HikariCP connection pool. Tenants can be added and
     * removed at runtime through
     * {@code com.mcp.enterprise.tenant.instance.TenantInstanceRegistry} (a future
     * admin REST API will consume the same registry).</p>
     */
    public static class Instance {

        /**
         * Master switch for instance-mode wiring. When {@code mode=instance}
         * but this flag is {@code false}, the application context fails fast
         * (see {@code McpTenantAutoConfiguration}) instead of silently running
         * without tenant isolation.
         */
        private boolean enabled = true;

        /**
         * Optional DDL (semicolon-separated) executed exactly once per tenant
         * pool right after the connection pool is created - e.g. to guarantee
         * every tenant database starts with the same object layout:
         * {@code "CREATE TABLE IF NOT EXISTS app_data(id BIGINT AUTO_INCREMENT PRIMARY KEY, payload VARCHAR(500));"}
         */
        private String initializeDdl = "";

        /**
         * Static per-tenant datasource definitions ({@code tenant-id =&gt; spec}).
         * A management API can later add tenants at runtime on top of these.
         */
        private Map<String, TenantDatasource> tenants = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getInitializeDdl() {
            return initializeDdl;
        }

        public void setInitializeDdl(String initializeDdl) {
            this.initializeDdl = initializeDdl;
        }

        public Map<String, TenantDatasource> getTenants() {
            return tenants;
        }

        public void setTenants(Map<String, TenantDatasource> tenants) {
            this.tenants = tenants;
        }
    }

    /**
     * Datasource specification for one tenant instance (V1.13).
     */
    public static class TenantDatasource {

        /** JDBC URL of the tenant's dedicated database/instance. */
        private String url;

        /** Database account for this tenant. */
        private String username;

        /**
         * Password. Supports {@code ${ENV_VAR}} placeholders resolved from
         * system properties / environment variables at provision time.
         */
        private String password = "";

        /** Optional driver class name; auto-detected from the URL when empty. */
        private String driverClassName = "";

        /** Per-tenant connection pool settings. */
        private Pool pool = new Pool();

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public Pool getPool() {
            return pool;
        }

        public void setPool(Pool pool) {
            this.pool = pool;
        }
    }

    /**
     * Per-tenant HikariCP pool settings (V1.13). Defaults follow HikariCP
     * safe defaults but are overridable per tenant for quota control.
     */
    public static class Pool {

        /** Maximum connections allowed in the tenant pool (quota). */
        private int maximumPoolSize = 10;

        /** Minimum idle connections kept warm. {@code 0} allows pool shrink. */
        private int minimumIdle = 2;

        /** Maximum time (ms) to wait for a connection before failing. */
        private long connectionTimeoutMs = 30_000;

        /** Maximum connection lifetime (ms). */
        private long maxLifetimeMs = 1_800_000;

        /** Idle timeout (ms) before an idle connection is evicted. */
        private long idleTimeoutMs = 600_000;

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public long getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public long getMaxLifetimeMs() {
            return maxLifetimeMs;
        }

        public void setMaxLifetimeMs(long maxLifetimeMs) {
            this.maxLifetimeMs = maxLifetimeMs;
        }

        public long getIdleTimeoutMs() {
            return idleTimeoutMs;
        }

        public void setIdleTimeoutMs(long idleTimeoutMs) {
            this.idleTimeoutMs = idleTimeoutMs;
        }
    }
}