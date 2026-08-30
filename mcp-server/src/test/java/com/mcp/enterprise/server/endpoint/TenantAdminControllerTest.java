package com.mcp.enterprise.server.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.enterprise.tenant.lifecycle.TenantLifecycleInfo;
import com.mcp.enterprise.tenant.lifecycle.TenantLifecycleManager;
import com.mcp.enterprise.tenant.lifecycle.TenantLifecycleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validates the V1.14 tenant lifecycle admin REST API: provisioning, suspend,
 * resume, list/detail and teardown incl. error mapping (404 / 409).
 */
class TenantAdminControllerTest {

    private final TenantLifecycleManager lifecycleManager = mock(TenantLifecycleManager.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    private static final TenantLifecycleInfo ACME_ACTIVE = new TenantLifecycleInfo(
            "acme", TenantLifecycleState.ACTIVE, "jdbc:h2:mem:acme", "sa",
            10, 2, Instant.parse("2026-08-30T12:00:00Z"), Instant.parse("2026-08-30T12:00:00Z"));

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TenantAdminController(lifecycleManager))
                .build();
    }

    @Test
    void listsTenants() throws Exception {
        when(lifecycleManager.list()).thenReturn(List.of(ACME_ACTIVE));
        mockMvc.perform(get("/api/admin/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenantId").value("acme"))
                .andExpect(jsonPath("$[0].state").value("ACTIVE"));
    }

    @Test
    void getsTenantDetail() throws Exception {
        when(lifecycleManager.get("acme")).thenReturn(Optional.of(ACME_ACTIVE));
        mockMvc.perform(get("/api/admin/tenants/acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant.maximumPoolSize").value(10));
    }

    @Test
    void getUnknownTenantReturns404() throws Exception {
        when(lifecycleManager.get("nobody")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/admin/tenants/nobody"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void provisionsTenant() throws Exception {
        when(lifecycleManager.provision(eq("acme"), any())).thenReturn(ACME_ACTIVE);
        String body = """
                {
                  "tenantId": "acme",
                  "url": "jdbc:mysql://db-acme:3306/mcp",
                  "username": "acme_app",
                  "password": "${ACME_DB_PASSWORD}",
                  "pool": { "maximumPoolSize": 10, "minimumIdle": 2 }
                }
                """;
        mockMvc.perform(post("/api/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant.tenantId").value("acme"))
                .andExpect(jsonPath("$.tenant.state").value("ACTIVE"));
    }

    @Test
    void suspendsTenant() throws Exception {
        TenantLifecycleInfo suspended = new TenantLifecycleInfo(
                "acme", TenantLifecycleState.SUSPENDED, ACME_ACTIVE.jdbcUrl(), "sa",
                10, 2, ACME_ACTIVE.createdAt(), Instant.now());
        when(lifecycleManager.suspend("acme")).thenReturn(suspended);
        mockMvc.perform(post("/api/admin/tenants/acme/suspend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant.state").value("SUSPENDED"));
    }

    @Test
    void suspendUnknownTenantReturns409() throws Exception {
        when(lifecycleManager.suspend("nobody"))
                .thenThrow(new IllegalStateException("Tenant 'nobody' is not managed"));
        mockMvc.perform(post("/api/admin/tenants/nobody/suspend"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void resumesTenant() throws Exception {
        when(lifecycleManager.resume("acme")).thenReturn(ACME_ACTIVE);
        mockMvc.perform(post("/api/admin/tenants/acme/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant.state").value("ACTIVE"));
    }

    @Test
    void removesTenant() throws Exception {
        when(lifecycleManager.remove("acme")).thenReturn(true);
        mockMvc.perform(delete("/api/admin/tenants/acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(true));
    }

    @Test
    void removeUnknownTenantReturns404() throws Exception {
        when(lifecycleManager.remove("nobody")).thenReturn(false);
        mockMvc.perform(delete("/api/admin/tenants/nobody"))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankProvisionPayloadReturns400() throws Exception {
        when(lifecycleManager.provision(eq(""), any()))
                .thenThrow(new IllegalArgumentException("Tenant id must not be blank"));
        String body = """
                { "tenantId": "", "url": "", "username": "" }
                """;
        mockMvc.perform(post("/api/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}