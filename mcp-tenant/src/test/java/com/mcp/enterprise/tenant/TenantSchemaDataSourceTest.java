package com.mcp.enterprise.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validates V1.12 schema-level tenant isolation end-to-end on real in-memory H2:
 * automatic per-tenant schema switching, cross-tenant data isolation and
 * fail-closed behaviour without a resolved tenant.
 */
class TenantSchemaDataSourceTest {

    private static final AtomicInteger DB_SEQ = new AtomicInteger();

    private TenantSchemaManager manager;
    private TenantSchemaDataSource tenantDs;
    private McpTenantProperties props;
    private String dbUrl;

    @BeforeEach
    void setUp() {
        // Unique in-memory DB per test to keep test isolation deterministic.
        dbUrl = "jdbc:h2:mem:ds_test_" + DB_SEQ.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        DataSource rawDs = new DriverManagerDataSource(dbUrl, "sa", "");
        props = new McpTenantProperties();
        props.setEnabled(true);
        props.setFailClosed(true);
        props.setMode(McpTenantProperties.Mode.SCHEMA);
        props.getSchema().setProvisionOnFirstUse(true);
        props.getSchema().setTemplateDdl(
                "CREATE TABLE IF NOT EXISTS app_data(id INT AUTO_INCREMENT PRIMARY KEY, payload VARCHAR(64));");

        manager = new TenantSchemaManager(rawDs, props);
        tenantDs = new TenantSchemaDataSource(rawDs, manager, props);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void writingWithoutTenantFailsClosed() {
        TenantContext.clear();
        JdbcTemplate jdbc = new JdbcTemplate(tenantDs);
        assertThrows(TenantNotResolvedException.class,
                () -> jdbc.execute("INSERT INTO app_data(payload) VALUES ('x')"));
    }

    @Test
    void tenantsAreDataIsolatedInTheirOwnSchemas() {
        // Tenant acme writes into its auto-provisioned schema.
        TenantContext.withTenant("acme", () -> {
            JdbcTemplate jdbc = new JdbcTemplate(tenantDs);
            jdbc.update("INSERT INTO app_data(payload) VALUES ('acme-secret')");
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_data", Integer.class);
            assertEquals(1, count, "acme must see its own row");
            return null;
        });

        // Tenant globex gets the same table layout (template DDL) but an EMPTY
        // schema - it must not see acme's rows.
        TenantContext.withTenant("globex", () -> {
            JdbcTemplate jdbc = new JdbcTemplate(tenantDs);
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_data", Integer.class);
            assertEquals(0, count, "globex must not see acme's rows");
            return null;
        });

        // acme's data is still intact afterwards.
        TenantContext.withTenant("acme", () -> {
            JdbcTemplate jdbc = new JdbcTemplate(tenantDs);
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_data", Integer.class);
            assertEquals(1, count, "acme's data must remain intact");
            return null;
        });
    }

    @Test
    void schemasArePhysicallyDistinct() {
        TenantContext.withTenant("acme", () -> {
            JdbcTemplate jdbc = new JdbcTemplate(tenantDs);
            jdbc.update("INSERT INTO app_data(payload) VALUES ('A')");
            return null;
        });
        TenantContext.withTenant("globex", () -> {
            JdbcTemplate jdbc = new JdbcTemplate(tenantDs);
            jdbc.update("INSERT INTO app_data(payload) VALUES ('G')");
            return null;
        });

        // Both tenants have their own physical schema with their own row.
        TenantContext.withTenant("acme", () -> {
            JdbcTemplate jdbc = new JdbcTemplate(tenantDs);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_data", Integer.class));
            assertEquals("A", jdbc.queryForObject("SELECT payload FROM app_data", String.class));
            return null;
        });
        TenantContext.withTenant("globex", () -> {
            JdbcTemplate jdbc = new JdbcTemplate(tenantDs);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_data", Integer.class));
            assertEquals("G", jdbc.queryForObject("SELECT payload FROM app_data", String.class));
            return null;
        });
    }

    @Test
    void provisionCanBeDisabled() {
        props.getSchema().setProvisionOnFirstUse(false);
        TenantContext.set("acme");
        JdbcTemplate jdbc = new JdbcTemplate(tenantDs);
        // Without provisioning, the schema switch itself fails (schema missing in H2).
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.execute("CREATE TABLE app_data(id INT)"));
    }
}