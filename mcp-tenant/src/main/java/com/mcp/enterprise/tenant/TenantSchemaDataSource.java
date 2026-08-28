package com.mcp.enterprise.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link DataSource} that transparently switches the database schema per
 * tenant before any statement is created on a connection (V1.12 schema-level
 * isolation).
 *
 * <p>Behaviour per {@code getConnection()}:</p>
 * <ul>
 *   <li>The returned connection is a dynamic proxy. Before
 *       {@code createStatement}/{@code prepareStatement}/{@code prepareCall}
 *       (and their overloads) execute, the tenant is resolved from
 *       {@link TenantContext} and the session is switched with the dialect's
 *       {@code SET SCHEMA}/{@code SET search_path}/{@code USE} statement.</li>
 *   <li><b>Fail-closed</b>: with no tenant bound, {@link TenantNotResolvedException}
 *       is thrown - no statement can leak across tenants.</li>
 *   <li><b>Provisioning</b>: when {@code mcp.tenant.schema.provision-on-first-use}
 *       is enabled, a missing schema is created on first access (idempotent).</li>
 *   <li><b>Caching</b>: a connection that already switched to the same schema
 *       skips the switch statement - zero overhead on subsequent statements.</li>
 * </ul>
 *
 * <p>Plain {@code Connection} calls such as {@code getMetaData()},
 * {@code setAutoCommit()}, {@code close()} are delegated untouched.</p>
 */
public class TenantSchemaDataSource extends DelegatingDataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaDataSource.class);

    private static final Set<String> SWITCH_HOOKS = Set.of(
            "createStatement", "prepareStatement", "prepareCall");

    private final TenantSchemaManager schemaManager;
    private final McpTenantProperties properties;

    public TenantSchemaDataSource(DataSource delegate, TenantSchemaManager schemaManager,
                                  McpTenantProperties properties) {
        super(delegate);
        this.schemaManager = schemaManager;
        this.properties = properties;
    }

    @Override
    public Connection getConnection() throws java.sql.SQLException {
        return proxy(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws java.sql.SQLException {
        return proxy(super.getConnection(username, password));
    }

    private Connection proxy(Connection target) {
        AtomicReference<String> switchedSchema = new AtomicReference<>(null);
        InvocationHandler handler = (proxyObj, method, args) -> {
            String methodName = method.getName();
            if (SWITCH_HOOKS.contains(methodName)) {
                ensureTenantSchema(target, switchedSchema);
            }
            try {
                return method.invoke(target, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                // Unwrap the real exception thrown by the target connection.
                Throwable cause = e.getCause();
                if (cause instanceof Exception ex) {
                    throw ex;
                }
                throw new java.sql.SQLException(cause);
            }
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler);
    }

    /**
     * Fail-closed tenant resolution + schema switch + optional provisioning,
     * executed once per connection per tenant.
     */
    private void ensureTenantSchema(Connection target, AtomicReference<String> switchedSchema)
            throws java.sql.SQLException {
        String tenantId = TenantContext.getOrNull();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantNotResolvedException(
                    "No tenant resolved for schema-level access. Bind the tenant header (default X-Tenant-Id) "
                            + "or call TenantContext.set(tenantId) before accessing tenant-scoped data.");
        }
        String schemaName = schemaManager.resolveSchemaName(tenantId);
        if (schemaName.equalsIgnoreCase(switchedSchema.get())) {
            return; // this physical connection is already on the right schema
        }
        if (properties.getSchema().isProvisionOnFirstUse()
                && !schemaManager.schemaExists(schemaName)) {
            schemaManager.provision(tenantId);
        }
        String switchSql = schemaManager.switchStatement(schemaName);
        log.debug("Switching connection to tenant schema [{}] via {}", schemaName, switchSql);
        try (java.sql.Statement stmt = target.createStatement()) {
            stmt.execute(switchSql);
        } catch (java.sql.SQLException e) {
            log.warn("Schema switch failed for [{}]: {}", schemaName, e.getMessage());
            throw e;
        }
        switchedSchema.set(schemaName.toLowerCase(Locale.ROOT));
    }
}