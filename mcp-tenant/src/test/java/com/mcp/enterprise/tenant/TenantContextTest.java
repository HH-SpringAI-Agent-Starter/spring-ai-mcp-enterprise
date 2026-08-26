package com.mcp.enterprise.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void setAndGetRoundtrip() {
        TenantContext.set("acme-corp");
        assertEquals(Optional.of("acme-corp"), TenantContext.get());
        assertEquals("acme-corp", TenantContext.currentTenantOrThrow());
    }

    @Test
    void setMirrorsToMdc() {
        TenantContext.set("tenant-42");
        assertEquals("tenant-42", MDC.get(TenantContext.MDC_KEY));
    }

    @Test
    void clearRemovesThreadLocalAndMdc() {
        TenantContext.set("acme-corp");
        TenantContext.clear();
        assertFalse(TenantContext.isResolved());
        assertTrue(TenantContext.get().isEmpty());
        assertNull(MDC.get(TenantContext.MDC_KEY));
    }

    @Test
    void blankOrNullClears() {
        TenantContext.set("acme-corp");
        TenantContext.set(null);
        assertFalse(TenantContext.isResolved());

        TenantContext.set("acme-corp");
        TenantContext.set("   ");
        assertFalse(TenantContext.isResolved());
    }

    @Test
    void currentTenantOrThrowFailsClosedWithoutTenant() {
        assertThrows(TenantNotResolvedException.class, TenantContext::currentTenantOrThrow);
    }

    @Test
    void withTenantRestoresPreviousBinding() {
        TenantContext.set("outer-tenant");
        String result = TenantContext.withTenant("inner-tenant", () -> {
            assertEquals("inner-tenant", TenantContext.currentTenantOrThrow());
            return "ok";
        });
        assertEquals("ok", result);
        // previous binding restored
        assertEquals("outer-tenant", TenantContext.currentTenantOrThrow());
    }

    @Test
    void withTenantClearsWhenNoPreviousBinding() {
        TenantContext.withTenant("inner-tenant", () -> {
            assertEquals("inner-tenant", TenantContext.currentTenantOrThrow());
            return null;
        });
        assertFalse(TenantContext.isResolved());
    }

    @Test
    void withTenantCleansUpOnException() {
        assertThrows(IllegalStateException.class, () -> TenantContext.withTenant("boom-tenant", () -> {
            throw new IllegalStateException("boom");
        }));
        assertFalse(TenantContext.isResolved());
    }

    @Test
    void inheritableThreadLocalPropagatesToChildThread() throws InterruptedException {
        TenantContext.set("parent-tenant");
        AtomicReference<String> child = new AtomicReference<>();
        Thread t = new Thread(() -> child.set(TenantContext.currentTenantOrThrow()));
        t.start();
        t.join();
        assertEquals("parent-tenant", child.get());
    }
}