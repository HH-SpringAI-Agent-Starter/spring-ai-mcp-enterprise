package com.mcp.enterprise.tenant;

/**
 * Fail-fast validation for {@link McpTenantProperties} mode combinations.
 *
 * <p>The three isolation modes (row / schema / instance) are mutually exclusive
 * by construction - every bean set is guarded by its own {@code mode} condition
 * in auto-configuration. What the conditions cannot express is a
 * <em>semantic</em> contradiction, e.g. {@code mode=instance} together with
 * {@code mcp.tenant.instance.enabled=false}: no instance beans would be wired,
 * the app would boot fine and silently run <em>without tenant isolation</em>.
 * This guard turns that silent degradation into an immediate startup failure.</p>
 */
public class TenantModeGuard {

    public TenantModeGuard(McpTenantProperties properties) {
        McpTenantProperties.Mode mode = properties.getMode();
        if (mode == McpTenantProperties.Mode.INSTANCE && !properties.getInstance().isEnabled()) {
            throw new IllegalStateException(
                    "Tenant mode is 'instance' but mcp.tenant.instance.enabled=false. "
                            + "Enable instance wiring or switch mode (row/schema). Failing fast instead of "
                            + "running without tenant isolation.");
        }
    }
}