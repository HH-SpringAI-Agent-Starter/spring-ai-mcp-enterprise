package com.mcp.integration.a2a;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

/**
 * V1.17: A2A 网关 OAuth2 Bearer (JWT) 校验器 — RFC 6750 Bearer Token Usage
 *
 * <p>与 mcp-auth 的 {@code McpJwtTokenProvider} 使用一致的 HS256 密钥派生规则：
 * 当 {@code mcp.enterprise.a2a.jwt-secret} 与 {@code mcp.auth.jwt-secret} 配置为同值时，
 * mcp-auth OAuth2 Client Credentials 令牌端点签发的 access_token 可直接通过本网关校验——
 * 即「mcp-auth 发证、A2A 网关验证」的完整 OAuth2 闭环（V1.16 只声明 securitySchemes，V1.17 强制校验）。</p>
 *
 * <p>支持标准 Authorization 头：{@code Authorization: Bearer &lt;JWT&gt;}</p>
 */
public class A2aJwtTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(A2aJwtTokenValidator.class);

    private final SecretKey signingKey;

    public A2aJwtTokenValidator(String jwtSecret) {
        // 与 mcp-auth McpJwtTokenProvider 完全相同的密钥派生规则（HS256 至少 32 字节）
        byte[] keyBytes = jwtSecret.length() < 32
                ? java.util.Arrays.copyOf(jwtSecret.getBytes(StandardCharsets.UTF_8), 32)
                : jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 校验 Bearer JWT。
     *
     * @return 校验通过返回 subject（token 持有者身份），失败返回 null
     */
    public String validate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String subject = claims.getSubject();
            log.debug("✅ A2A Bearer 校验通过: subject={}", subject);
            return subject;
        } catch (ExpiredJwtException e) {
            log.warn("🚫 A2A Bearer 校验失败: 令牌已过期");
            return null;
        } catch (Exception e) {
            log.warn("🚫 A2A Bearer 校验失败: {} ({})", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /** 从 Authorization 头提取 Bearer 令牌（RFC 6750：大小写不敏感前缀，可带可选空白） */
    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        String trimmed = authorizationHeader.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = trimmed.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    /** 供审计/健康检查用：返回校验器是否已启用（有密钥即启用） */
    public static Set<String> supportedSchemes() {
        return Collections.singleton("oauth2");
    }
}