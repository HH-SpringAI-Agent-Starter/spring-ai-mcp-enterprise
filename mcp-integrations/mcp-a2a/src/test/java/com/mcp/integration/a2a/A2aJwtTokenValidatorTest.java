package com.mcp.integration.a2a;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * V1.17: A2A 网关 OAuth2 Bearer (JWT) 校验器测试
 *
 * 重点验证与 mcp-auth 令牌的互通性：用与 mcp-auth 完全相同的 HS256 密钥派生规则
 * （secret 不足 32 字节时补足到 32 字节）签发令牌，A2A 网关应能校验通过。
 */
class A2aJwtTokenValidatorTest {

    private static final String JWT_SECRET = "shared-secret-for-mcp-auth-and-a2a-gateway";

    private SecretKey signingKey() {
        byte[] keyBytes = JWT_SECRET.length() < 32
                ? java.util.Arrays.copyOf(JWT_SECRET.getBytes(StandardCharsets.UTF_8), 32)
                : JWT_SECRET.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String issueToken(String subject, long ttlMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .claim("tokenType", "mcp-session")
                .signWith(signingKey())
                .compact();
    }

    @Test
    @DisplayName("mcp-auth 签发的有效令牌可通过 A2A 网关校验（互通）")
    void validTokenFromMcpAuthPasses() {
        A2aJwtTokenValidator validator = new A2aJwtTokenValidator(JWT_SECRET);
        String token = issueToken("svc-agent-1", 3600_000L);
        assertEquals("svc-agent-1", validator.validate(token));
    }

    @Test
    @DisplayName("过期令牌被拒绝")
    void expiredTokenRejected() {
        A2aJwtTokenValidator validator = new A2aJwtTokenValidator(JWT_SECRET);
        String token = issueToken("svc-agent-1", -1000L); // 已过期
        assertNull(validator.validate(token));
    }

    @Test
    @DisplayName("错误密钥签发的令牌被拒绝（防伪造）")
    void wrongSecretRejected() {
        A2aJwtTokenValidator validator = new A2aJwtTokenValidator(JWT_SECRET);
        byte[] otherKey = "another-secret-key-entirely-different-123456".getBytes(StandardCharsets.UTF_8);
        String token = Jwts.builder()
                .subject("attacker")
                .expiration(new Date(System.currentTimeMillis() + 3600_000L))
                .signWith(Keys.hmacShaKeyFor(otherKey))
                .compact();
        assertNull(validator.validate(token));
    }

    @Test
    @DisplayName("非 JWT / 垃圾输入被安全拒绝")
    void garbageRejected() {
        A2aJwtTokenValidator validator = new A2aJwtTokenValidator(JWT_SECRET);
        assertNull(validator.validate("not-a-jwt"));
        assertNull(validator.validate(""));
        assertNull(validator.validate(null));
        assertNull(validator.validate("a.b.c"));
    }

    @Test
    @DisplayName("Authorization 头 Bearer 提取符合 RFC 6750")
    void bearerExtraction() {
        assertNull(A2aJwtTokenValidator.extractBearerToken(null));
        assertNull(A2aJwtTokenValidator.extractBearerToken(""));
        assertNull(A2aJwtTokenValidator.extractBearerToken("Basic dXNlcjpwYXNz"));
        assertNull(A2aJwtTokenValidator.extractBearerToken("Bearer "));
        assertEquals("abc.def.ghi", A2aJwtTokenValidator.extractBearerToken("Bearer abc.def.ghi"));
        assertEquals("abc.def.ghi", A2aJwtTokenValidator.extractBearerToken("bearer abc.def.ghi"));
        assertEquals("abc.def.ghi", A2aJwtTokenValidator.extractBearerToken("  Bearer   abc.def.ghi  "));
    }

    @Test
    @DisplayName("短密钥自动补足到 32 字节（与 mcp-auth 派生规则一致）")
    void shortSecretPaddedLikeMcpAuth() {
        A2aJwtTokenValidator validator = new A2aJwtTokenValidator("short");
        // 用同一规则派生密钥签发，应能互通
        byte[] keyBytes = java.util.Arrays.copyOf("short".getBytes(StandardCharsets.UTF_8), 32);
        String token = Jwts.builder()
                .subject("svc")
                .expiration(new Date(System.currentTimeMillis() + 3600_000L))
                .signWith(Keys.hmacShaKeyFor(keyBytes))
                .compact();
        assertEquals("svc", validator.validate(token));
    }

    @Test
    @DisplayName("支持 schemes 查询")
    void supportedSchemes() {
        assertNotNull(A2aJwtTokenValidator.supportedSchemes());
        assertEquals(1, A2aJwtTokenValidator.supportedSchemes().size());
    }
}