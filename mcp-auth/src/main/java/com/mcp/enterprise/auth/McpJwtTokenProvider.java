package com.mcp.enterprise.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * MCP JWT 令牌提供者
 *
 * 支持两种模式：
 * 1. 自签名 JWT（内置密钥）- 开发/测试用
 * 2. OIDC JWT 验证委托 - 生产环境用（委托给 IdP）
 */
public class McpJwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(McpJwtTokenProvider.class);

    private final McpAuthProperties properties;
    private final SecretKey signingKey;
    private final long expirationMs;

    public McpJwtTokenProvider(McpAuthProperties properties) {
        this.properties = properties;
        this.expirationMs = properties.getJwtExpirationMinutes() * 60 * 1000L;

        // 优先使用配置的密钥，否则生成一个随机密钥
        String secret = properties.getJwtSecret();
        if (secret == null || secret.isBlank()) {
            // 生产环境：必须配置密钥！
            secret = Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes());
            log.warn("⚠️ 未配置 mcp.auth.jwt-secret，使用随机密钥（重启后失效，仅用于开发测试）");
        }

        // 密钥必须至少 256 位（HS256）
        byte[] keyBytes = secret.length() < 32
                ? Arrays.copyOf(secret.getBytes(StandardCharsets.UTF_8), 32)
                : secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成会话令牌
     */
    public String generateToken(String subject, Set<String> roles, Map<String, Object> extraClaims) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiry)
                .claim("roles", roles)
                .claim("tokenType", "mcp-session")
                .id(UUID.randomUUID().toString());

        if (extraClaims != null) {
            extraClaims.forEach(builder::claim);
        }

        return builder.signWith(signingKey).compact();
    }

    /**
     * 生成 API Key → JWT 转换令牌
     */
    public String generateTokenFromApiKey(String apiKey, String owner, Set<String> roles) {
        return generateToken(owner, roles, Map.of(
                "apiKey", apiKey,
                "authMethod", "api-key-converted"
        ));
    }

    /**
     * 验证并解析 JWT 令牌
     */
    public TokenValidationResult validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            @SuppressWarnings("unchecked")
            List<String> rolesList = claims.get("roles", List.class);
            Set<String> roles = rolesList != null ? new HashSet<>(rolesList) : Set.of();

            return new TokenValidationResult(
                    true,
                    claims.getSubject(),
                    roles,
                    claims.get("tokenType", String.class),
                    claims.get("authMethod", String.class),
                    claims
            );
        } catch (ExpiredJwtException e) {
            log.debug("JWT 令牌已过期");
            return new TokenValidationResult(false, null, Set.of(), null, null, null);
        } catch (Exception e) {
            log.warn("JWT 令牌验证失败: {}", e.getMessage());
            return new TokenValidationResult(false, null, Set.of(), null, null, null);
        }
    }

    public long getExpirationMs() { return expirationMs; }

    public record TokenValidationResult(
            boolean valid,
            String subject,
            Set<String> roles,
            String tokenType,
            String authMethod,
            Claims claims
    ) {}
}
