package com.mcp.enterprise.tenant;

/**
 * Thrown when a tenant id cannot be safely mapped to a database schema name
 * (e.g. contains characters outside the allow-list, or is too long).
 *
 * <p>Schema names are derived from untrusted input (HTTP headers / JWT claims),
 * so this guard prevents SQL injection through schema identifiers.</p>
 */
public class InvalidTenantSchemaException extends RuntimeException {

    public InvalidTenantSchemaException(String message) {
        super(message);
    }

    public InvalidTenantSchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}