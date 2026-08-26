package com.mcp.enterprise.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that resolves the tenant id from a request header
 * (default {@code X-Tenant-Id}) and binds it to {@link TenantContext} for the
 * duration of the request, guaranteeing cleanup on exit.
 *
 * <p>In future iterations the tenant can also be resolved from JWT claims
 * ({@code tenant_id}) or OAuth2 client registrations - see the V1.12 roadmap.</p>
 */
public class McpTenantFilter extends OncePerRequestFilter {

    private final McpTenantProperties properties;

    public McpTenantFilter(McpTenantProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (properties.isEnabled()) {
            String tenantId = request.getHeader(properties.getHeaderName());
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.set(tenantId);
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}