package com.mcp.enterprise.tenant.lifecycle;

import com.mcp.enterprise.tenant.McpTenantProperties;
import com.mcp.enterprise.tenant.instance.DefaultTenantInstanceRegistry;
import com.mcp.enterprise.tenant.instance.TenantInstanceProvisioner;
import com.mcp.enterprise.tenant.instance.TenantInstanceRegistry;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the V1.14 tenant lifecycle manager: provision / suspend (pool
 * released + fail-closed) / resume (re-provisioned from retained spec) /
 * teardown, plus idempotency and error paths.
 */
class TenantLifecycleManagerTest {

    private TenantInstanceRegistry registry;
    private TenantLifecycleManager manager;

    @BeforeEach
    void setUp() {
        registry = new DefaultTenantInstanceRegistry();
        manager = new TenantLifecycleManager(registry, new TenantInstanceProvisioner());
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    private McpTenantProperties.TenantDatasource spec(String tenantId) {
        McpTenantProperties.TenantDatasource spec = new McpTenantProperties.TenantDatasource();
        spec.setUrl("jdbc:h2:mem:life_" + tenantId + ";DB_CLOSE_DELAY=-1");
        spec.setUsername("sa");
        spec.getPool().setMaximumPoolSize(5);
        return spec;
    }

    @Test
    void provisionRegistersActivePool() {
        TenantLifecycleInfo info = manager.provision("acme", spec("acme"));
        assertEquals(TenantLifecycleState.ACTIVE, info.state());
        assertEquals("jdbc:h2:mem:life_acme;DB_CLOSE_DELAY=-1", info.jdbcUrl());
        assertEquals(5, info.maximumPoolSize());
        assertTrue(registry.isRegistered("acme"));
        assertTrue(manager.isActive("acme"));
        assertEquals(1, manager.count());
    }

    @Test
    void suspendReleasesPoolAndFailsClosed() {
        manager.provision("acme", spec("acme"));
        TenantLifecycleInfo suspended = manager.suspend("acme");
        assertEquals(TenantLifecycleState.SUSPENDED, suspended.state());
        assertFalse(registry.isRegistered("acme"), "pool must be released on suspend");
        assertFalse(manager.isActive("acme"));
        // fail-closed: traffic for a suspended tenant must be denied
        assertThrows(com.mcp.enterprise.tenant.TenantNotResolvedException.class,
                () -> registry.get("acme"));
    }

    @Test
    void resumeRestoresPoolFromRetainedSpec() {
        manager.provision("acme", spec("acme"));
        manager.suspend("acme");
        TenantLifecycleInfo resumed = manager.resume("acme");
        assertEquals(TenantLifecycleState.ACTIVE, resumed.state());
        assertTrue(registry.isRegistered("acme"), "pool must be re-registered on resume");
        assertTrue(manager.isActive("acme"));
        assertEquals(5, resumed.maximumPoolSize(), "spec must be retained across suspend");
    }

    @Test
    void suspendAndResumeAreIdempotent() {
        manager.provision("acme", spec("acme"));
        TenantLifecycleInfo s1 = manager.suspend("acme");
        TenantLifecycleInfo s2 = manager.suspend("acme");
        assertEquals(TenantLifecycleState.SUSPENDED, s1.state());
        assertEquals(TenantLifecycleState.SUSPENDED, s2.state());
        TenantLifecycleInfo r1 = manager.resume("acme");
        TenantLifecycleInfo r2 = manager.resume("acme");
        assertEquals(TenantLifecycleState.ACTIVE, r1.state());
        assertEquals(TenantLifecycleState.ACTIVE, r2.state());
    }

    @Test
    void removeTearsDownCompletely() {
        manager.provision("acme", spec("acme"));
        assertTrue(manager.remove("acme"));
        assertFalse(manager.isActive("acme"));
        assertFalse(registry.isRegistered("acme"));
        assertEquals(0, manager.count());
        assertTrue(manager.get("acme").isEmpty());
        // second remove is a no-op returning false
        assertFalse(manager.remove("acme"));
    }

    @Test
    void reProvisionReplacesPool() {
        manager.provision("acme", spec("acme"));
        McpTenantProperties.TenantDatasource bigger = spec("acme");
        bigger.getPool().setMaximumPoolSize(20);
        TenantLifecycleInfo replaced = manager.provision("acme", bigger);
        assertEquals(20, replaced.maximumPoolSize());
        assertTrue(registry.isRegistered("acme"));
    }

    @Test
    void listReturnsAllManagedTenants() {
        manager.provision("acme", spec("acme"));
        manager.provision("globex", spec("globex"));
        manager.suspend("globex");
        List<TenantLifecycleInfo> all = manager.list();
        assertEquals(2, all.size());
        long active = all.stream().filter(t -> t.state() == TenantLifecycleState.ACTIVE).count();
        long suspended = all.stream().filter(t -> t.state() == TenantLifecycleState.SUSPENDED).count();
        assertEquals(1, active);
        assertEquals(1, suspended);
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> manager.provision("", spec("x")));
        assertThrows(IllegalArgumentException.class, () -> manager.provision(null, spec("x")));
        assertThrows(IllegalArgumentException.class, () -> manager.provision("acme", null));
        assertThrows(IllegalStateException.class, () -> manager.suspend("unknown"));
        assertThrows(IllegalStateException.class, () -> manager.resume("unknown"));
    }

    @Test
    void provisionedPoolIsRealAndUsable() {
        TenantLifecycleInfo info = manager.provision("acme", spec("acme"));
        HikariDataSource pool = (HikariDataSource) registry.get("acme");
        assertFalse(pool.isClosed());
        assertEquals("mcp-tenant-acme", pool.getPoolName());
        assertTrue(info.createdAt().isBefore(info.updatedAt()) || info.createdAt().equals(info.updatedAt()));
    }
}