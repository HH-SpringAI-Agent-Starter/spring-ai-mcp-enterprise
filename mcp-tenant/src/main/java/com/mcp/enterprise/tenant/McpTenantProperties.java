package com.mcp.enterprise.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for multi-tenant row-level isolation.
 *
 * <p>Prefix: {@code mcp.tenant}</p>
 *
 * <pre>{@code
 * mcp:
 *   tenant:
 *     enabled: true            # master switch (default true)
 *     header-name: X-Tenant-Id # header carrying the tenant id (default X-Tenant-Id)
 *     fail-closed: true        # reject data access without a tenant (default true)
 *     default-tenant: ""       # optional fallback when failClosed=false
 * }</pre>
 */
@ConfigurationProperties(prefix = "mcp.tenant")
public class McpTenantProperties {

    /** Master switch for tenant enforcement. */
    private boolean enabled = true;

    /** HTTP header name that carries the tenant id. */
    private String headerName = "X-Tenant-Id";

    /**
     * Fail closed: reject tenant-scoped data access when no tenant is resolved.
     * Safe default for multi-tenant deployments - prevents cross-tenant leaks.
     */
    private boolean failClosed = true;

    /**
     * Optional fallback tenant id applied when {@code failClosed=false} and no
     * tenant header is present. Leave empty to keep denying tenant-scoped access.
     */
    private String defaultTenant = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public boolean isFailClosed() {
        return failClosed;
    }

    public void setFailClosed(boolean failClosed) {
        this.failClosed = failClosed;
    }

    public String getDefaultTenant() {
        return defaultTenant;
    }

    public void setDefaultTenant(String defaultTenant) {
        this.defaultTenant = defaultTenant;
    }
}