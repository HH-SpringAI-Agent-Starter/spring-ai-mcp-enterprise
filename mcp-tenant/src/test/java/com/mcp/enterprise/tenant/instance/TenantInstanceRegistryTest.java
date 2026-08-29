package com.mcp.enterprise.tenant.instance;

import com.mcp.enterprise.tenant.TenantNotResolvedException;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the V1.13 instance registry lifecycle: register / resolve /
 * unregister (pool released) / fail-closed on unknown tenants.
 */
class TenantInstanceRegistryTest {

    private static final AtomicInteger DB_SEQ = new AtomicInteger();

    private final TenantInstanceRegistry registry = new DefaultTenantInstanceRegistry();

    @AfterEach
    void tearDown() {
        registry.close();
    }

    private HikariDataSource newPool(String tenantId) {
        HikariDataSource pool = new HikariDataSource();
        pool.setPoolName("test-" + tenantId);
        pool.setJdbcUrl("jdbc:h2:mem:reg_" + tenantId + "_" + DB_SEQ.incrementAndGet() + ";DB_CLOSE_DELAY=-1");
        pool.setUsername("sa");
        pool.setMaximumPoolSize(3);
        return pool;
    }

    @Test
    void registersAndResolves() {
        HikariDataSource acme = newPool("acme");
        registry.register("acme", acme);
        assertTrue(registry.isRegistered("acme"));
        assertEquals(Set.of("acme"), registry.tenants());
        assertSamePool(acme, registry.get("acme"));
    }

    @Test
    void unknownTenantFailsClosed() {
        assertThrows(TenantNotResolvedException.class, () -> registry.get("nobody"));
        assertFalse(registry.isRegistered("nobody"));
    }

    @Test
    void rejectsBlankTenantOrNullDataSource() {
        assertThrows(IllegalArgumentException.class, () -> registry.register("", newPool("x")));
        assertThrows(IllegalArgumentException.class, () -> registry.register("acme", null));
    }

    @Test
    void unregisterClosesThePoolAndRemovesTenant() {
        HikariDataSource acme = newPool("acme");
        registry.register("acme", acme);
        DataSource removed = registry.unregister("acme");
        assertSamePool(acme, removed);
        assertTrue(acme.isClosed(), "unregistered pool must be closed");
        assertFalse(registry.isRegistered("acme"));
        assertThrows(TenantNotResolvedException.class, () -> registry.get("acme"));
    }

    @Test
    void unregisterIsIdempotent() {
        registry.register("acme", newPool("acme"));
        assertNotNull(registry.unregister("acme"));
        assertNull(registry.unregister("acme"));
    }

    @Test
    void replacingATenantClosesThePreviousPool() {
        HikariDataSource first = newPool("acme");
        registry.register("acme", first);
        HikariDataSource second = newPool("acme");
        registry.register("acme", second);
        assertTrue(first.isClosed(), "replaced pool must be closed");
        assertSamePool(second, registry.get("acme"));
    }

    @Test
    void closeReleasesEveryPool() {
        HikariDataSource acme = newPool("acme");
        HikariDataSource globex = newPool("globex");
        registry.register("acme", acme);
        registry.register("globex", globex);
        registry.close();
        assertTrue(acme.isClosed());
        assertTrue(globex.isClosed());
        assertEquals(Set.of(), registry.tenants());
    }

    private static void assertSamePool(HikariDataSource expected, DataSource actual) {
        assertTrue(expected == actual, "expected the exact same pool instance");
    }
}