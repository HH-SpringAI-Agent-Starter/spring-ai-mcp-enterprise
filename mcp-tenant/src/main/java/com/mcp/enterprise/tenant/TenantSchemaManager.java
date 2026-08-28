package com.mcp.enterprise.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Schema-level tenant manager (V1.12).
 *
 * <p>Maps a tenant id to a safe database schema name and provisions the schema
 * on first use. All schema identifiers are derived from untrusted input, so the
 * mapping is strictly validated and normalized:</p>
 *
 * <ul>
 *   <li>Only {@code [A-Za-z0-9_-]} characters (1-64) are accepted for tenant ids
 *       - anything else throws {@link InvalidTenantSchemaException}.</li>
 *   <li>The schema name is {@code prefix + sanitized(tenantId)}, lower-cased,
 *       hyphens folded to underscores - safe for unquoted identifier usage in
 *       PostgreSQL/MySQL/H2 without SQL injection risk.</li>
 * </ul>
 *
 * <p>Provisioning ({@code mcp.tenant.schema.provision-on-first-use=true}) runs
 * {@code CREATE SCHEMA IF NOT EXISTS} on a single connection, then - optionally -
 * switches to the tenant schema and applies the template DDL so every tenant
 * schema starts with the same object layout.</p>
 *
 * <p>Dialects: {@code auto} detection from the JDBC driver product name (cached
 * after first resolution), or explicit {@code postgresql | mysql | h2 | generic}.</p>
 */
public class TenantSchemaManager {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaManager.class);

    /** Allow-list for raw tenant ids - conservative, injection-safe. */
    private static final Pattern SAFE_TENANT_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final DataSource dataSource;
    private final McpTenantProperties properties;

    /** Cached dialect (auto-detected once, then reused). */
    private volatile McpTenantProperties.SchemaDialect resolvedDialect;

    public TenantSchemaManager(DataSource dataSource, McpTenantProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    /**
     * Maps a tenant id to the physical schema name, e.g.
     * {@code acme-corp} + prefix {@code tenant_} -> {@code tenant_acme_corp}.
     *
     * @throws InvalidTenantSchemaException when the tenant id is unsafe.
     */
    public String resolveSchemaName(String tenantId) {
        if (tenantId == null || !SAFE_TENANT_PATTERN.matcher(tenantId).matches()) {
            throw new InvalidTenantSchemaException(
                    "Unsafe tenant id for schema resolution: '" + tenantId
                            + "'. Allowed: 1-64 chars of [A-Za-z0-9_-].");
        }
        String sanitized = tenantId.toLowerCase(Locale.ROOT).replace('-', '_');
        String prefix = properties.getSchema().getPrefix();
        String schemaName = (prefix == null ? "" : prefix) + sanitized;
        if (!SAFE_TENANT_PATTERN.matcher(schemaName).matches()) {
            throw new InvalidTenantSchemaException(
                    "Resolved schema name too long or unsafe: '" + schemaName + "'");
        }
        return schemaName;
    }

    /**
     * Resolves the schema name for the current thread tenant
     * (fail-closed when no tenant is bound).
     */
    public String resolveCurrentSchema() {
        String tenantId = TenantContext.getOrNull();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantNotResolvedException(
                    "No tenant resolved for schema-level access. Bind the tenant header (default X-Tenant-Id) "
                            + "or call TenantContext.set(tenantId) before accessing tenant-scoped data.");
        }
        return resolveSchemaName(tenantId);
    }

    /**
     * Creates the tenant schema when missing and applies the optional template
     * DDL inside that schema. Idempotent and safe to call on every first
     * connection per tenant. Runs on a single connection so the create + switch
     * + template DDL sequence is consistent.
     */
    public void provision(String tenantId) {
        String schemaName = resolveSchemaName(tenantId);
        if (schemaExists(schemaName)) {
            return;
        }
        McpTenantProperties.SchemaDialect dialect = dialect();
        try (Connection connection = dataSource.getConnection()) {
            String createSql = createSchemaSql(dialect, schemaName);
            log.info("Provisioning tenant schema [{}] via {}", schemaName, createSql);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createSql);
            }
            String templateDdl = properties.getSchema().getTemplateDdl();
            if (templateDdl != null && !templateDdl.isBlank()) {
                // Switch this connection into the tenant schema first, so the
                // template DDL (e.g. CREATE TABLE ...) lands in the right place.
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(switchStatement(dialect, schemaName));
                }
                for (String ddl : templateDdl.split(";")) {
                    String trimmed = ddl.trim();
                    if (!trimmed.isEmpty()) {
                        log.debug("Applying template DDL to [{}]: {}", schemaName, trimmed);
                        try (Statement stmt = connection.createStatement()) {
                            stmt.execute(trimmed);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataAccessResourceFailureException(
                    "Failed to provision tenant schema '" + schemaName + "': " + e.getMessage(), e);
        }
    }

    /** True when the given schema already exists in the catalogue. */
    public boolean schemaExists(String schemaName) {
        String sql = "SELECT COUNT(*) FROM information_schema.schemata WHERE UPPER(schema_name) = UPPER(?)";
        try (Connection connection = dataSource.getConnection();
             java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            // Some engines expose a different catalogue layout - be conservative:
            // log and continue; provisioning itself is idempotent.
            log.warn("Schema existence check failed for [{}]: {}", schemaName, e.getMessage());
            return false;
        }
    }

    /**
     * Returns the SQL statement that switches the current session to the schema,
     * e.g. {@code SET search_path TO tenant_acme} (PostgreSQL),
     * {@code USE tenant_acme} (MySQL), {@code SET SCHEMA tenant_acme} (H2/generic).
     */
    public String switchStatement(String schemaName) {
        return switchStatement(dialect(), schemaName);
    }

    /** Resolves the dialect (configured value, or cached auto-detection). */
    public McpTenantProperties.SchemaDialect dialect() {
        McpTenantProperties.SchemaDialect configured = properties.getSchema().getDialect();
        if (configured != null && configured != McpTenantProperties.SchemaDialect.AUTO) {
            return configured;
        }
        McpTenantProperties.SchemaDialect cached = resolvedDialect;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (resolvedDialect == null) {
                try (Connection connection = dataSource.getConnection()) {
                    resolvedDialect = detectDialect(connection);
                } catch (SQLException e) {
                    log.warn("Dialect auto-detection failed, falling back to generic: {}", e.getMessage());
                    resolvedDialect = McpTenantProperties.SchemaDialect.GENERIC;
                }
            }
            return resolvedDialect;
        }
    }

    /** Detects the dialect from the JDBC driver's database product name. */
    public McpTenantProperties.SchemaDialect detectDialect(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (product.contains("postgres")) {
            return McpTenantProperties.SchemaDialect.POSTGRESQL;
        }
        if (product.contains("mysql") || product.contains("mariadb")) {
            return McpTenantProperties.SchemaDialect.MYSQL;
        }
        if (product.contains("h2")) {
            return McpTenantProperties.SchemaDialect.H2;
        }
        return McpTenantProperties.SchemaDialect.GENERIC;
    }

    /** CREATE statement for a new tenant schema, per dialect. */
    private String createSchemaSql(McpTenantProperties.SchemaDialect dialect, String schemaName) {
        return switch (dialect) {
            case MYSQL -> "CREATE DATABASE IF NOT EXISTS " + schemaName;
            default -> "CREATE SCHEMA IF NOT EXISTS " + schemaName;
        };
    }

    /** Session switch statement, per dialect. */
    private String switchStatement(McpTenantProperties.SchemaDialect dialect, String schemaName) {
        return switch (dialect) {
            case POSTGRESQL -> "SET search_path TO " + schemaName;
            case MYSQL -> "USE " + schemaName;
            default -> "SET SCHEMA " + schemaName;
        };
    }
}