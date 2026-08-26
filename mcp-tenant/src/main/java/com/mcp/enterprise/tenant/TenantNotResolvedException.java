package com.mcp.enterprise.tenant;

/**
 * Thrown when a tenant-scoped data operation is attempted while no tenant id is
 * resolved on the current thread (fail-closed multi-tenancy).
 */
public class TenantNotResolvedException extends IllegalStateException {

    public TenantNotResolvedException(String message) {
        super(message);
    }

    public TenantNotResolvedException(String message, Throwable cause) {
        super(message, cause);
    }
}