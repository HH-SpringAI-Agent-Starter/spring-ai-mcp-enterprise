package com.mcp.enterprise.tenant.instance;

import com.mcp.enterprise.tenant.McpTenantProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates {@link TenantInstanceProvisioner}: pool quota configuration,
 * ${ENV} secret resolution and optional initialize-DDL on pool creation.
 */
class TenantInstanceProvisionerTest {

    private static final AtomicInteger DB_SEQ = new AtomicInteger();

    @AfterEach
    void tearDown() {
        System.clearProperty("MCP_TEST_PW");
    }

    private McpTenantProperties.TenantDatasource spec(String url) {
        McpTenantProperties.TenantDatasource spec = new McpTenantProperties.TenantDatasource();
        spec.setUrl(url);
        spec.setUsername("sa");
        spec.setPassword("");
        return spec;
    }

    private String uniqueUrl(String label) {
        return "jdbc:h2:mem:prov_" + label + "_" + DB_SEQ.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
    }

    @Test
    void appliesPoolQuotaPerTenant() {
        McpTenantProperties.TenantDatasource spec = spec(uniqueUrl("quota"));
        spec.getPool().setMaximumPoolSize(5);
        spec.getPool().setMinimumIdle(1);

        TenantInstanceProvisioner provisioner = new TenantInstanceProvisioner();
        DataSource ds = provisioner.provision("acme", spec);
        try {
            assertTrue(ds instanceof HikariDataSource);
            HikariDataSource pool = (HikariDataSource) ds;
            assertEquals(5, pool.getMaximumPoolSize());
            assertEquals(1, pool.getMinimumIdle());
        } finally {
            TenantInstanceProvisioner.closeQuietly(ds);
        }
    }

    @Test
    void resolvesEnvPlaceholderFromSystemProperty() {
        System.setProperty("MCP_TEST_PW", "s3cret!");
        assertEquals("s3cret!", TenantInstanceProvisioner.resolveSecret("${MCP_TEST_PW}"));
    }

    @Test
    void passesThroughPlainAndUnknownPlaceholders() {
        assertEquals("plain-pass", TenantInstanceProvisioner.resolveSecret("plain-pass"));
        assertEquals("", TenantInstanceProvisioner.resolveSecret(""));
        assertEquals("${NO_SUCH_VAR_12345}", TenantInstanceProvisioner.resolveSecret("${NO_SUCH_VAR_12345}"));
    }

    @Test
    void refusesMissingUrl() {
        McpTenantProperties.TenantDatasource spec = spec(uniqueUrl("x"));
        spec.setUrl("  ");
        TenantInstanceProvisioner provisioner = new TenantInstanceProvisioner();
        assertThrows(IllegalArgumentException.class, () -> provisioner.provision("acme", spec));
    }

    @Test
    void appliesInitializeDdlOncePerPool() {
        McpTenantProperties.TenantDatasource spec = spec(uniqueUrl("ddl"));
        TenantInstanceProvisioner provisioner = new TenantInstanceProvisioner(
                "CREATE TABLE IF NOT EXISTS app_data(id INT AUTO_INCREMENT PRIMARY KEY, payload VARCHAR(64));");

        DataSource ds = provisioner.provision("acme", spec);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.update("INSERT INTO app_data(payload) VALUES ('ready')");
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_data", Integer.class);
            assertEquals(1, count, "initialize DDL must have created the table");
        } finally {
            TenantInstanceProvisioner.closeQuietly(ds);
        }
    }
}