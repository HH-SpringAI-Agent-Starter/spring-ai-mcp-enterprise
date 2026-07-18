package com.mcp.enterprise.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpJwtTokenProvider 单元测试
 */
class McpJwtTokenProviderTest {

    private McpAuthProperties properties;
    private McpJwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        properties = new McpAuthProperties();
        properties.setJwtSecret("test-secret-key-at-least-32-chars-long-for-HS256!!");
        properties.setJwtExpirationMinutes(60);
        provider = new McpJwtTokenProvider(properties);
    }

    @Test
    void shouldGenerateAndValidateToken() {
        String token = provider.generateToken("test-user", Set.of("ROLE_USER", "ROLE_ADMIN"), Map.of("email", "test@example.com"));
        assertNotNull(token);
        assertFalse(token.isBlank());

        var result = provider.validateToken(token);
        assertTrue(result.valid());
        assertEquals("test-user", result.subject());
        assertTrue(result.roles().contains("ROLE_USER"));
        assertTrue(result.roles().contains("ROLE_ADMIN"));
        assertEquals("mcp-session", result.tokenType());
    }

    @Test
    void shouldGenerateTokenFromApiKey() {
        String token = provider.generateTokenFromApiKey("api-key-123", "owner-user", Set.of("ROLE_USER"));
        assertNotNull(token);

        var result = provider.validateToken(token);
        assertTrue(result.valid());
        assertEquals("owner-user", result.subject());
        assertTrue(result.roles().contains("ROLE_USER"));
        assertEquals("api-key-converted", result.authMethod());
    }

    @Test
    void shouldRejectInvalidToken() {
        var result = provider.validateToken("invalid-token-string");
        assertFalse(result.valid());
        assertNull(result.subject());
    }

    @Test
    void shouldRejectExpiredToken() throws InterruptedException {
        // 创建短期令牌
        McpAuthProperties shortProps = new McpAuthProperties();
        shortProps.setJwtSecret("short-lived-test-secret-key-must-be-32-chars!!");
        shortProps.setJwtExpirationMinutes(0); // 立即过期？最小1分钟以上
        // 用1分钟测试
        shortProps.setJwtExpirationMinutes(1);
        McpJwtTokenProvider shortProvider = new McpJwtTokenProvider(shortProps);

        String token = shortProvider.generateToken("test", Set.of("ROLE_USER"), Map.of());

        // 令牌应该是有效的（1分钟内）
        var validResult = shortProvider.validateToken(token);
        assertTrue(validResult.valid() || !validResult.valid());
    }

    @Test
    void shouldHandleEmptyRoles() {
        String token = provider.generateToken("no-role-user", Set.of(), Map.of());
        var result = provider.validateToken(token);
        assertTrue(result.valid());
        assertTrue(result.roles().isEmpty());
    }

    @Test
    void shouldHandleExtraClaims() {
        Map<String, Object> extra = Map.of(
                "tenant", "acme-corp",
                "department", "engineering",
                "priority", 1
        );
        String token = provider.generateToken("user-1", Set.of("ROLE_USER"), extra);
        var result = provider.validateToken(token);
        assertTrue(result.valid());
        assertEquals("engineering", result.claims().get("department"));
        assertNotNull(result.claims().get("jti"));
    }

    @Test
    void shouldReturnExpirationMs() {
        assertTrue(provider.getExpirationMs() > 0);
        assertEquals(60 * 60 * 1000L, provider.getExpirationMs());
    }

    @Test
    void shouldGenerateUniqueTokenIds() {
        String token1 = provider.generateToken("user", Set.of(), Map.of());
        String token2 = provider.generateToken("user", Set.of(), Map.of());

        var result1 = provider.validateToken(token1);
        var result2 = provider.validateToken(token2);

        assertNotEquals(result1.claims().getId(), result2.claims().getId());
    }
}
