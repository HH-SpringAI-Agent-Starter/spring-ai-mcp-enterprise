package com.mcp.enterprise.tenant.lifecycle;

/**
 * Lifecycle state of a managed tenant instance (V1.14).
 *
 * <ul>
 *   <li>{@link #ACTIVE} - a dedicated {@code DataSource} is registered in the
 *       instance registry and the tenant can access data.</li>
 *   <li>{@link #SUSPENDED} - the tenant's pool has been released and the
 *       tenant is blocked from data access (fail-closed), but the datasource
 *       specification is retained so the tenant can be resumed without
 *       re-supplying credentials.</li>
 * </ul>
 */
public enum TenantLifecycleState {

    /** A dedicated datasource is registered and usable. */
    ACTIVE,

    /** Pool released, access blocked; spec retained for fast resume. */
    SUSPENDED
}