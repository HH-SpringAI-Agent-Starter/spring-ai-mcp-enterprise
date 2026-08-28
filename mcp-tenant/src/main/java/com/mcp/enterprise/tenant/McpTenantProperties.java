package com.mcp.enterprise.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for multi-tenant isolation.
 *
 * <p>Prefix: {@code mcp.tenant}</p>
 *
 * <p>Two isolation modes are supported (see {@link Mode}):</p>
 * <ul>
 *   <li>{@code row} (default): row-level isolation - every JDBC statement is
 *       fail-closed guarded by {@link TenantAwareJdbcTemplate}; business SQL
 *       filters by a tenant column.</li>
 *   <li>{@code schema}: schema-level isolation - the DataSource automatically
 *       switches the database schema per tenant (PostgreSQL {@code search_path},
 *       MySQL {@code USE}, H2 {@code SET SCHEMA}); schemas are provisioned on
 *       first use. Ideal for strong isolation at the platform level.</li>
 * </ul>
 *
 * <pre>{@code
 * mcp:
 *   tenant:
 *     enabled: true            # master switch (default true)
 *     header-name: X-Tenant-Id # header carrying the tenant id (default X-Tenant-Id)
 *     fail-closed: true        # reject data access without a tenant (default true)
 *     default-tenant: ""       # optional fallback when failClosed=false
 *     mode: row                # row | schema (default row)
 *     schema:
 *       prefix: tenant_        # schema name prefix, e.g. tenant_acme
 *       dialect: auto          # auto | postgresql | mysql | h2 | generic
 *       provision-on-first-use: true  # CREATE SCHEMA IF NOT EXISTS on first access
 *       template-ddl: ""       # optional DDL executed after schema creation (e.g.
 *                              #   "CREATE TABLE IF NOT EXISTS app_data(id BIGINT AUTO_INCREMENT PRIMARY KEY, payload VARCHAR(500));")
 * }</pre>
 */
@ConfigurationProperties(prefix = "mcp.tenant")
public class McpTenantProperties {

    public enum Mode {
        /** Row-level isolation (V1.11, default). */
        ROW,
        /** Schema-level isolation (V1.12). */
        SCHEMA
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

    /** Isolation mode: {@code row} (default) or {@code schema}. */
    private Mode mode = Mode.ROW;

    /** Schema-level isolation settings (used when {@code mode=SCHEMA}). */
    private Schema schema = new Schema();

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
}