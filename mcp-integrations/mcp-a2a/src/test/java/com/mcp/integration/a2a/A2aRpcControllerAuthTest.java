package com.mcp.integration.a2a;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * V1.17: A2A 控制器鉴权模式测试
 *
 * 覆盖：none / api-key / oauth2 (Bearer JWT) 三种模式的 authorized 判定，
 * 以及 authErrorMessage 按模式输出的提示。
 */
@ExtendWith(MockitoExtension.class)
class A2aRpcControllerAuthTest {

    private static final String JWT_SECRET = "shared-secret-for-mcp-auth-and-a2a-gateway";
    private static final String API_KEY = "a2a-static-key";

    @Mock
    private A2aBridgeService bridgeService;

    private McpA2aProperties properties;
    private A2aJwtTokenValidator validator;

    @BeforeEach
    void setUp() {
        properties = new McpA2aProperties();
        validator = new A2aJwtTokenValidator(JWT_SECRET);
    }

    private A2aRpcController controller() {
        return new A2aRpcController(bridgeService, properties, validator);
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest();
    }

    private String issueToken(String subject, long ttlMs) {
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .claim("tokenType", "mcp-session")
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    // ===== oauth2 模式 =====

    @Test
    @DisplayName("oauth2 模式(jwt-secret)：无 Authorization 头 → 拒绝")
    void oauth2MissingHeaderRejected() {
        properties.setJwtSecret(JWT_SECRET);
        assertEquals("oauth2", properties.resolvedAuthMode());
        // 通过 health 端点行为间接断言（health 返回非 null 表示放行，否则走 unauthorized）
        MockHttpServletRequest req = request();
        // 直接借 controller.unauthorized 不可见；用 rpc 端点 JSON-RPC 错误码验证
        var resp = controller().rpc(null, req);
        assertEquals(401, resp.getStatusCode().value());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = (java.util.Map<String, Object>) resp.getBody();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> err = (java.util.Map<String, Object>) body.get("error");
        assertEquals(-32009, err.get("code"));
    }

    @Test
    @DisplayName("oauth2 模式：有效 Bearer 令牌 → 放行")
    void oauth2ValidBearerPasses() {
        properties.setJwtSecret(JWT_SECRET);
        when(bridgeService.listSkills()).thenReturn(List.of());
        MockHttpServletRequest req = request();
        req.addHeader("Authorization", "Bearer " + issueToken("svc-agent-1", 3600_000L));
        assertNotNull(controller().health(req)); // 非 null = 通过鉴权返回 health
    }

    @Test
    @DisplayName("oauth2 模式：无效/伪造 Bearer → 拒绝")
    void oauth2InvalidBearerRejected() {
        properties.setJwtSecret(JWT_SECRET);
        MockHttpServletRequest req = request();
        req.addHeader("Authorization", "Bearer invalid.token.value");
        var resp = controller().rpc(null, req);
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("oauth2 模式：过期 Bearer → 拒绝")
    void oauth2ExpiredBearerRejected() {
        properties.setJwtSecret(JWT_SECRET);
        MockHttpServletRequest req = request();
        req.addHeader("Authorization", "Bearer " + issueToken("svc-agent-1", -1000L));
        var resp = controller().rpc(null, req);
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("oauth2 模式：错误提示包含 RFC 6750 Bearer 语义")
    void oauth2ErrorMessage() {
        properties.setJwtSecret(JWT_SECRET);
        MockHttpServletRequest req = request();
        var resp = controller().rpc(null, req);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = (java.util.Map<String, Object>) resp.getBody();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> err = (java.util.Map<String, Object>) body.get("error");
        assertTrue(String.valueOf(err.get("message")).contains("Bearer"));
        assertTrue(String.valueOf(err.get("message")).contains("RFC 6750"));
    }

    // ===== api-key 模式（保留 V1.15 行为） =====

    @Test
    @DisplayName("api-key 模式：X-A2A-Key 匹配 → 放行")
    void apiKeyMatchPasses() {
        properties.setApiKey(API_KEY);
        assertEquals("api-key", properties.resolvedAuthMode());
        when(bridgeService.listSkills()).thenReturn(List.of());
        MockHttpServletRequest req = request();
        req.addHeader("X-A2A-Key", API_KEY);
        assertNotNull(controller().health(req));
    }

    @Test
    @DisplayName("api-key 模式：缺失/错误 key → 拒绝")
    void apiKeyMismatchRejected() {
        properties.setApiKey(API_KEY);
        MockHttpServletRequest req = request();
        var resp = controller().rpc(null, req);
        assertEquals(401, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("api-key 模式：错误提示仍为 X-A2A-Key")
    void apiKeyErrorMessage() {
        properties.setApiKey(API_KEY);
        MockHttpServletRequest req = request();
        var resp = controller().rpc(null, req);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = (java.util.Map<String, Object>) resp.getBody();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> err = (java.util.Map<String, Object>) body.get("error");
        assertTrue(String.valueOf(err.get("message")).contains("X-A2A-Key"));
    }

    // ===== none 模式 =====

    @Test
    @DisplayName("none 模式：无凭据也放行")
    void noneModePasses() {
        assertEquals("none", properties.resolvedAuthMode());
        when(bridgeService.listSkills()).thenReturn(List.of());
        MockHttpServletRequest req = request();
        assertNotNull(controller().health(req));
    }

    // ===== 模式推导优先级 =====

    @Test
    @DisplayName("模式推导：jwt-secret 优先于 api-key；显式 securityScheme 最高")
    void authModeResolution() {
        properties.setApiKey(API_KEY);
        properties.setJwtSecret(JWT_SECRET);
        assertEquals("oauth2", properties.resolvedAuthMode());

        properties.setSecurityScheme("api-key");
        properties.setJwtSecret(JWT_SECRET);
        // 显式声明 api-key 但未显式声明 oauth2：resolvedAuthMode 仍应尊重显式声明？——规范：声明与校验一致
        assertEquals("api-key", properties.resolvedAuthMode());
        properties.setSecurityScheme("oauth2");
        assertEquals("oauth2", properties.resolvedAuthMode());
    }

    @Test
    @DisplayName("securityScheme 推导与 authMode 联动：jwt-secret 非空 → 声明 oauth2")
    void securitySchemeFollowsJwt() {
        properties.setJwtSecret(JWT_SECRET);
        assertEquals("oauth2", properties.resolvedSecurityScheme());
        assertTrue(properties.isOAuth2Enabled());
    }

    @Test
    @DisplayName("Bearer 提取器：大小写不敏感、空白容忍")
    void bearerExtractionCases() {
        assertFalse(A2aJwtTokenValidator.extractBearerToken(null) != null);
        assertEquals("abc", A2aJwtTokenValidator.extractBearerToken("Bearer abc"));
        assertEquals("abc", A2aJwtTokenValidator.extractBearerToken("bearer abc"));
        assertEquals("abc", A2aJwtTokenValidator.extractBearerToken("  Bearer   abc "));
    }
}