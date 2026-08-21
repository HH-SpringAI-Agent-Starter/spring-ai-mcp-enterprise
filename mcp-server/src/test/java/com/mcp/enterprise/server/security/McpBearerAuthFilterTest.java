package com.mcp.enterprise.server.security;

import com.mcp.enterprise.autoconfigure.McpEnterpriseProperties;
import com.mcp.enterprise.core.security.McpOAuth2Manager;
import com.mcp.enterprise.core.security.McpSecurityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpBearerAuthFilter 单元测试 —— V1.9 网关 Bearer 自动校验（Fail-Closed）。
 *
 * 覆盖：开关关闭放行 / 有效 Bearer 放行并写 tokenInfo / 无效 Bearer 401 / 缺失凭证 401 /
 * 公开路径放行（/oauth2、/health）/ 旧 X-API-Key 平滑迁移。
 */
class McpBearerAuthFilterTest {

    private McpOAuth2Manager oauth2;
    private McpSecurityManager securityManager;
    private McpEnterpriseProperties properties;
    private McpBearerAuthFilter filter;
    private String clientSecret;

    @BeforeEach
    void setUp() {
        oauth2 = new McpOAuth2Manager("filter-test-key-" + System.nanoTime());
        var reg = oauth2.registerClient("agent-1", "data-team", Set.of("user"), Set.of("tools:read", "tools:call"));
        clientSecret = reg.clientSecret();
        securityManager = new McpSecurityManager();
        properties = new McpEnterpriseProperties();
        filter = new McpBearerAuthFilter(oauth2, securityManager, properties);
    }

    private String freshToken() {
        return oauth2.issueClientCredentialsToken("agent-1", clientSecret, null).accessToken();
    }

    private MockHttpServletResponse doFilter(String authHeader, String apiKey, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (authHeader != null) request.addHeader("Authorization", authHeader);
        if (apiKey != null) request.addHeader("X-API-Key", apiKey);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void disabledByDefaultPassesThrough() throws Exception {
        // 默认 enforce-bearer=false：不拦截，直接放行
        MockHttpServletResponse resp = doFilter(null, null, "/api/mcp/tools");
        assertEquals(200, resp.getStatus());
        assertNull(resp.getHeader("WWW-Authenticate"));
    }

    @Test
    void validBearerPassesAndExposesTokenInfo() throws Exception {
        properties.getOauth2().setEnforceBearer(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mcp/tools");
        request.addHeader("Authorization", "Bearer " + freshToken());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertNotNull(request.getAttribute(McpBearerAuthFilter.ATTR_TOKEN_INFO));
        McpOAuth2Manager.TokenInfo info =
                (McpOAuth2Manager.TokenInfo) request.getAttribute(McpBearerAuthFilter.ATTR_TOKEN_INFO);
        assertEquals("agent-1", info.clientId());
        assertTrue(info.scopes().contains("tools:call"));
    }

    @Test
    void invalidBearerRejectedWith401AndWwwAuthenticate() throws Exception {
        properties.getOauth2().setEnforceBearer(true);
        MockHttpServletResponse resp = doFilter("Bearer bad.token.here", null, "/api/mcp/tools");
        assertEquals(401, resp.getStatus());
        assertTrue(resp.getHeader("WWW-Authenticate").startsWith("Bearer "));
    }

    @Test
    void noCredentialsRejectedWith401() throws Exception {
        properties.getOauth2().setEnforceBearer(true);
        MockHttpServletResponse resp = doFilter(null, null, "/api/mcp/tools");
        assertEquals(401, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("missing_token"));
    }

    @Test
    void expiredTokenRejected() throws Exception {
        properties.getOauth2().setEnforceBearer(true);
        String token = freshToken();
        oauth2.revokeToken(token);
        MockHttpServletResponse resp = doFilter("Bearer " + token, null, "/api/mcp/tools");
        assertEquals(401, resp.getStatus());
    }

    @Test
    void legacyApiKeyStillAllowedForMigration() throws Exception {
        properties.getOauth2().setEnforceBearer(true);
        String legacyKey = securityManager.createApiKey("legacy-client", Set.of("user"));
        MockHttpServletResponse resp = doFilter(null, legacyKey, "/api/mcp/tools");
        assertEquals(200, resp.getStatus());
    }

    @Test
    void publicPathsSkipped() throws Exception {
        properties.getOauth2().setEnforceBearer(true);
        // 公开路径：/oauth2、/health、/actuator、OPTIONS 预检
        assertEquals(200, doFilter(null, null, "/oauth2/token").getStatus());
        assertEquals(200, doFilter(null, null, "/api/mcp/health").getStatus());
        MockHttpServletRequest options = new MockHttpServletRequest("OPTIONS", "/api/mcp/tools");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(options, resp, new MockFilterChain());
        assertEquals(200, resp.getStatus());
    }
}