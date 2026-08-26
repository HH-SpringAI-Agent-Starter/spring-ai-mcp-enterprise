package com.mcp.enterprise.tenant;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thread-local tenant context for multi-tenant row-level isolation.
 *
 * <p>Design principles:</p>
 * <ul>
 *   <li><b>Fail-closed</b> - every data operation must resolve a tenant id before touching
 *       shared tables ({@link #currentTenantOrThrow()}).</li>
 *   <li><b>Implicit propagation</b> - uses {@link InheritableThreadLocal} so async tasks
 *       spawned from a tenant-bound thread inherit the tenant automatically.</li>
 *   <li><b>Observability</b> - the tenant id is mirrored to SLF4J MDC key
 *       {@value #MDC_KEY} so every audit/metrics line is tenant-taggable.</li>
 *   <li><b>Always cleans up</b> - {@link #withTenant(String, Supplier)} restores the
 *       previous binding (or clears) in a finally block.</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // In a servlet filter (see McpTenantFilter) or a tool executor:
 * TenantContext.set("acme-corp");
 * try { jdbcTemplate.update("INSERT INTO app_data ..."); }
 * finally { TenantContext.clear(); }
 * }</pre>
 */
public final class TenantContext {

    /** SLF4J MDC key used to tag logs with the current tenant id. */
    public static final String MDC_KEY = "mcp.tenantId";

    private static final ThreadLocal<String> HOLDER = new InheritableThreadLocal<>();

    private TenantContext() {
    }

    /**
     * Binds a tenant id to the current thread and MDC.
     * Blank/null values clear the context (no tenant).
     */
    public static void set(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            clear();
            return;
        }
        String normalized = tenantId.trim();
        HOLDER.set(normalized);
        MDC.put(MDC_KEY, normalized);
    }

    /** Returns the resolved tenant id, if any. */
    public static Optional<String> get() {
        return Optional.ofNullable(HOLDER.get());
    }

    /**
     * Fail-closed accessor: returns the current tenant or throws
     * {@link TenantNotResolvedException} when none is bound.
     */
    public static String currentTenantOrThrow() {
        return get().orElseThrow(() -> new TenantNotResolvedException(
                "No tenant resolved for current thread. Bind the tenant header (default X-Tenant-Id) "
                        + "or call TenantContext.set(tenantId) before accessing tenant-scoped data."));
    }

    /** True when a tenant id is currently bound. */
    public static boolean isResolved() {
        return HOLDER.get() != null;
    }

    /** Returns the bound tenant id, or {@code null} when absent. */
    public static String getOrNull() {
        return HOLDER.get();
    }

    /**
     * Runs {@code action} with {@code tenantId} bound, then restores the previous
     * binding (or clears the context). Safe for nested calls.
     */
    public static <T> T withTenant(String tenantId, Supplier<T> action) {
        String previous = HOLDER.get();
        set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                clear();
            } else {
                set(previous);
            }
        }
    }

    /** Removes the tenant binding from both the thread and MDC. */
    public static void clear() {
        HOLDER.remove();
        MDC.remove(MDC_KEY);
    }
}