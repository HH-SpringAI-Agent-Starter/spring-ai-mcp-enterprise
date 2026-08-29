package com.mcp.enterprise.tenant.instance;

import com.mcp.enterprise.tenant.McpTenantProperties;
import com.mcp.enterprise.tenant.TenantContext;
import com.mcp.enterprise.tenant.TenantNotResolvedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validates V1.13 instance-level isolation end-to-end on two real in-memory
 * H2 databases (two tenant instances):
 * physical data isolation, fail-closed without a tenant, denial of
 * unregistered tenants and runtime register/unregister.
 */
class TenantInstanceDataSourceTest {

    private static final AtomicInteger DB_SEQ = new AtomicInteger();

    private static final String DDL =
            "CREATE TABLE IF NOT EXISTS app_data(id INT AUTO_INCREMENT PRIMARY KEY, payload VARCHAR(64));";

    private DefaultTenantInstanceRegistry registry;
    private TenantInstanceDataSource routing;
    private DataSource acmePool;
    private DataSource globexPool;
    private String acmeUrl;
    private String globexUrl;

    @BeforeEach
    void setUp() {
        acmeUrl = "jdbc:h2:mem:inst_acme_" + DB_SEQ.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        globexUrl = "jdbc:h2:mem:inst_globex_" + DB_SEQ.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        TenantInstanceProvisioner provisioner = new TenantInstanceProvisioner(DDL);

        registry = new DefaultTenantInstanceRegistry();
        acmePool = provisioner.provision("acme", spec(acmeUrl));
        globexPool = provisioner.provision("globex", spec(globexUrl));
        registry.register("acme", acmePool);
        registry.register("globex", globexPool);
        routing = new TenantInstanceDataSource(registry);
    }

    @AfterEach
    void tearDown() {
        registry.close();
        TenantContext.clear();
    }

    private static McpTenantProperties.TenantDatasource spec(String url) {
        McpTenantProperties.TenantDatasource spec = new McpTenantProperties.TenantDatasource();
        spec.setUrl(url);
        spec.setUsername("sa");
        spec.setPassword("");
        return spec;
    }

    @Test
    void writingWithoutTenantFailsClosed() {
        TenantContext.clear();
        assertThrows(TenantNotResolvedException.class, routing::getConnection);
    }

    @Test
    void unknownTenantIsDeniedFailClosed() {
        TenantContext.withTenant("nobody", () -> {
            assertThrows(TenantNotResolvedException.class, routing::getConnection);
            return null;
        });
    }

    @Test
    void tenantsArePhysicallyIsolatedAcrossDatabases() {
        // acme writes into ITS OWN database instance.
        TenantContext.withTenant("acme", () -> {
            JdbcTemplate jdbc = new JdbcTemplate(routing);
            jdbc.update("INSERT INTO app_data(payload) VALUES ('acme-secret')");
            return null;
        });
        // globex writes into ITS OWN database instance.
        TenantContext.withTenant("globex", () -> {
            JdbcTemplate jdbc = new JdbcTemplate(routing);
            jdbc.update("INSERT INTO app_data(payload) VALUES ('globex-secret')");
            return null;
        });

        // Each tenant sees only its own row - physical (database-level) isolation.
        TenantContext.withTenant("acme", () -> {
            JdbcTemplate jdbc = new JdbcTemplate(routing);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_data", Integer.class));
            assertEquals("acme-secret", jdbc.queryForObject("SELECT payload FROM app_data", String.class));
            return null;
        });
        TenantContext.withTenant("globex", () -> {
            JdbcTemplate jdbc = new JdbcTemplate(routing);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_data", Integer.class));
            assertEquals("globex-secret", jdbc.queryForObject("SELECT payload FROM app_data", String.class));
            return null;
        });
    }

    @Test
    void runtimeRegisterTakesEffectImmediately() {
        // Provision a third tenant database at runtime - no restart.
        String lateUrl = "jdbc:h2:mem:inst_late_" + DB_SEQ.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        DataSource latePool = new TenantInstanceProvisioner(DDL).provision("late", spec(lateUrl));
        registry.register("late", latePool);

        TenantContext.withTenant("late", () -> {
            JdbcTemplate jdbc = new JdbcTemplate(routing);
            jdbc.update("INSERT INTO app_data(payload) VALUES ('late-data')");
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_data", Integer.class));
            return null;
        });
    }

    @Test
    void runtimeUnregisterDeniesAccessImmediately() {
        registry.unregister("globex");
        TenantContext.withTenant("globex", () -> {
            assertThrows(TenantNotResolvedException.class, routing::getConnection);
            return null;
        });
        // The other tenant is unaffected.
        TenantContext.withTenant("acme", () -> {
            JdbcTemplate jdbc = new JdbcTemplate(routing);
            jdbc.update("INSERT INTO app_data(payload) VALUES ('still-alive')");
            return null;
        });
    }
}