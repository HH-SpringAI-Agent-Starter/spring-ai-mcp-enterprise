package com.mcp.enterprise.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates row-level tenant isolation on a real in-memory H2 database:
 * fail-closed writes, per-tenant data visibility and clean context handling.
 */
class TenantAwareJdbcTemplateTest {

    private JdbcTemplate rawJdbc;
    private TenantAwareJdbcTemplate tenantJdbc;
    private McpTenantProperties failClosedProps;

    @BeforeEach
    void setUp() {
        DataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:tenant_test;DB_CLOSE_DELAY=-1", "sa", "");
        rawJdbc = new JdbcTemplate(ds);
        rawJdbc.execute("CREATE TABLE IF NOT EXISTS app_data ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "tenant_id VARCHAR(32) NOT NULL, "
                + "payload VARCHAR(64))");

        failClosedProps = new McpTenantProperties();
        failClosedProps.setEnabled(true);
        failClosedProps.setFailClosed(true);

        tenantJdbc = new TenantAwareJdbcTemplate(rawJdbc, failClosedProps);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        rawJdbc.execute("DROP TABLE IF EXISTS app_data");
    }

    @Test
    void updateWithoutTenantFailsClosed() {
        assertThrows(TenantNotResolvedException.class,
                () -> tenantJdbc.update("INSERT INTO app_data(tenant_id, payload) VALUES (?, ?)",
                        "acme", "x"));
        assertEquals(0, rawJdbc.queryForObject("SELECT COUNT(*) FROM app_data", Integer.class));
    }

    @Test
    void queryWithoutTenantFailsClosed() {
        assertThrows(TenantNotResolvedException.class,
                () -> tenantJdbc.queryForList("SELECT * FROM app_data"));
    }

    @Test
    void tenantBoundWritesAndReadsAreIsolated() {
        TenantContext.set("acme");
        tenantJdbc.update("INSERT INTO app_data(tenant_id, payload) VALUES (?, ?)", "acme", "p1");
        tenantJdbc.update("INSERT INTO app_data(tenant_id, payload) VALUES (?, ?)", "acme", "p2");

        TenantContext.set("globex");
        tenantJdbc.update("INSERT INTO app_data(tenant_id, payload) VALUES (?, ?)", "globex", "g1");

        // acme sees only its own rows
        TenantContext.set("acme");
        List<Map<String, Object>> acmeRows =
                tenantJdbc.queryForList("SELECT payload FROM app_data WHERE tenant_id = ?", "acme");
        assertEquals(2, acmeRows.size());

        // globex sees only one
        TenantContext.set("globex");
        List<Map<String, Object>> globexRows =
                tenantJdbc.queryForList("SELECT payload FROM app_data WHERE tenant_id = ?", "globex");
        assertEquals(1, globexRows.size());

        assertEquals(3, rawJdbc.queryForObject("SELECT COUNT(*) FROM app_data", Integer.class));
    }

    @Test
    void withTenantBindsAndCleansUp() {
        TenantContext.withTenant("short-lived", () -> {
            tenantJdbc.update("INSERT INTO app_data(tenant_id, payload) VALUES (?, ?)",
                    "short-lived", "s1");
            return null;
        });
        assertTrue(TenantContext.get().isEmpty());
        assertEquals(1, rawJdbc.queryForObject(
                "SELECT COUNT(*) FROM app_data WHERE tenant_id = 'short-lived'", Integer.class));
    }

    @Test
    void disabledTenantEnforcementAllowsLegacyAccess() {
        McpTenantProperties disabled = new McpTenantProperties();
        disabled.setEnabled(false);
        TenantAwareJdbcTemplate openJdbc = new TenantAwareJdbcTemplate(rawJdbc, disabled);

        openJdbc.update("INSERT INTO app_data(tenant_id, payload) VALUES (?, ?)", "legacy", "ok");
        assertEquals(1, openJdbc.queryForObject("SELECT COUNT(*) FROM app_data", Integer.class));
    }

    @Test
    void defaultTenantFallbackWhenNotFailClosed() {
        McpTenantProperties lenient = new McpTenantProperties();
        lenient.setEnabled(true);
        lenient.setFailClosed(false);
        lenient.setDefaultTenant("default-tenant");
        TenantAwareJdbcTemplate lenientJdbc = new TenantAwareJdbcTemplate(rawJdbc, lenient);

        // no tenant bound -> falls back to default-tenant, no exception
        lenientJdbc.update("INSERT INTO app_data(tenant_id, payload) VALUES (?, ?)", "default-tenant", "d1");
        assertEquals(1, lenientJdbc.queryForObject("SELECT COUNT(*) FROM app_data", Integer.class));
        // MDC/default bound
        assertEquals("default-tenant", TenantContext.getOrNull());
    }
}