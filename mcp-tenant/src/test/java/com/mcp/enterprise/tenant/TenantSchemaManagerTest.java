package com.mcp.enterprise.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates schema-name mapping, validation, dialect detection and provisioning
 * for V1.12 schema-level tenant isolation (real in-memory H2).
 */
class TenantSchemaManagerTest {

    private static final AtomicInteger DB_SEQ = new AtomicInteger();

    private DataSource ds;
    private McpTenantProperties props;
    private TenantSchemaManager manager;
    private String dbUrl;

    @BeforeEach
    void setUp() {
        // Unique in-memory DB per test to keep test isolation deterministic.
        dbUrl = "jdbc:h2:mem:schema_test_" + DB_SEQ.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        ds = new DriverManagerDataSource(dbUrl, "sa", "");
        props = new McpTenantProperties();
        props.setEnabled(true);
        props.setFailClosed(true);
        props.setMode(McpTenantProperties.Mode.SCHEMA);
        manager = new TenantSchemaManager(ds, props);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void mapsTenantIdToSafeSchemaName() {
        assertEquals("tenant_acme_corp", manager.resolveSchemaName("acme-corp"));
        assertEquals("tenant_globex", manager.resolveSchemaName("Globex"));
        assertEquals("tenant_a1", manager.resolveSchemaName("A1"));
    }

    @Test
    void rejectsUnsafeTenantIds() {
        assertThrows(InvalidTenantSchemaException.class, () -> manager.resolveSchemaName(null));
        assertThrows(InvalidTenantSchemaException.class, () -> manager.resolveSchemaName(""));
        assertThrows(InvalidTenantSchemaException.class, () -> manager.resolveSchemaName("ac me"));
        assertThrows(InvalidTenantSchemaException.class, () -> manager.resolveSchemaName("ac;me"));
        assertThrows(InvalidTenantSchemaException.class, () -> manager.resolveSchemaName("ac'me"));
        assertThrows(InvalidTenantSchemaException.class, () -> manager.resolveSchemaName("a".repeat(65)));
    }

    @Test
    void resolvesCurrentSchemaFailClosed() {
        assertThrows(TenantNotResolvedException.class, manager::resolveCurrentSchema);
        TenantContext.set("acme");
        assertEquals("tenant_acme", manager.resolveCurrentSchema());
    }

    @Test
    void detectsH2Dialect() {
        assertEquals(McpTenantProperties.SchemaDialect.H2, manager.dialect());
        assertEquals("SET SCHEMA tenant_acme", manager.switchStatement("tenant_acme"));
    }

    @Test
    void provisionsSchemaOnFirstUse() {
        assertFalse(manager.schemaExists("tenant_acme"));
        manager.provision("acme");
        assertTrue(manager.schemaExists("tenant_acme"));
        // Idempotent - second provision must not fail.
        manager.provision("acme");
    }

    @Test
    void appliesTemplateDdlInsideTenantSchema() {
        props.getSchema().setTemplateDdl(
                "CREATE TABLE IF NOT EXISTS app_data("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, payload VARCHAR(64));"
                        + "CREATE INDEX IF NOT EXISTS idx_payload ON app_data(payload);");
        manager.provision("acme");

        // The tenant schema exists and the template DDL landed inside it.
        org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);
        Integer tablesInTenantSchema = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = UPPER('tenant_acme') AND table_name = UPPER('app_data')",
                Integer.class);
        assertEquals(1, tablesInTenantSchema, "template DDL must create app_data inside the tenant schema");

        // ... and the table must NOT exist in the default PUBLIC schema.
        Integer tablesInPublic = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = UPPER('PUBLIC') AND table_name = UPPER('app_data')",
                Integer.class);
        assertEquals(0, tablesInPublic, "template DDL must not leak into the PUBLIC schema");
    }

    @Test
    void noTemplateDdlMeansEmptyTenantSchema() {
        manager.provision("acme");
        org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);
        Integer tablesInTenantSchema = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = UPPER('tenant_acme')",
                Integer.class);
        assertEquals(0, tablesInTenantSchema, "no template DDL configured - schema must stay empty");
    }
}