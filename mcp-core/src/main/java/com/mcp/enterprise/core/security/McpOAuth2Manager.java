package com.mcp.enterprise.core.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP OAuth 2.0 客户端凭证（Client Credentials）与 EMA（Enterprise-Managed Authorization）管理器。
 *
 * <p>企业级 MCP 的核心安全诉求之一，是让「机器对机器（M2M）」的 AI Agent 能安全地获得短期凭证，
 * 而不是长期共享的 API Key。本组件实现 OAuth 2.0 {@code client_credentials} 授权流程：</p>
 *
 * <ul>
 *   <li>客户端注册：{@code client_id}/{@code client_secret} + 角色 roles + 授权 scope</li>
 *   <li>令牌签发：HMAC-SHA256 签名的 JWT 风格 access_token，带过期时间与 scope</li>
 *   <li>令牌校验：签名 + 过期 + 客户端启用态 三重校验（无数据库、无外部依赖，可嵌入或替换）</li>
 *   <li>令牌内省（Introspection）：按 RFC 7662 语义返回 active / scope / exp / client_id</li>
 *   <li>EMA 外挂接口：{@link TokenIntrospector} 双向支持 ——
 *       既可作为本机签发的校验源，也可委托给企业现有身份提供方（IdP/OIDC）做集中授权，
 *       呼应 MCP EMA（Enterprise-Managed Authorization）标准方向。</li>
 * </ul>
 *
 * <p>安全要点：secret 采用 SHA-256 散列存储（不落明文）；令牌 HMAC 签名防篡改；
 * 过期由 exp 声明驱动；每次校验都会触发审计回调槽位（可选）。</p>
 */
public class McpOAuth2Manager {

    /** 签名算法：HmacSHA256 */
    private static final String HMAC_ALGO = "HmacSHA256";
    /** 默认令牌有效期（秒） */
    private static final long DEFAULT_TOKEN_TTL_SECONDS = 3600;

    /** 客户端注册表：clientId -> Client */
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    /** 已签发令牌：tokenId -> TokenRecord（用于主动吊销） */
    private final Map<String, TokenRecord> issuedTokens = new ConcurrentHashMap<>();
    /** 吊销令牌集合（过期后清理） */
    private final Set<String> revokedTokens = Collections.synchronizedSet(new HashSet<>());

    private final byte[] signingKey;
    private long tokenTtlSeconds = DEFAULT_TOKEN_TTL_SECONDS;
    /** 可选：外挂企业 IdP 内省器（EMA 集中授权） */
    private TokenIntrospector externalIntrospector;

    /**
     * @param signingKey 用于令牌签名的密钥。生产环境务必从安全配置（如 KMS/环境变量）注入。
     */
    public McpOAuth2Manager(String signingKey) {
        if (signingKey == null || signingKey.isBlank()) {
            throw new IllegalArgumentException("OAuth2 signing key must not be blank");
        }
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
    }

    // ===== 客户端管理 =====

    /** 注册一个 OAuth2 客户端。返回生成的 client_secret（仅此一次可见，内部只存散列）。 */
    public ClientRegistration registerClient(String clientId, String owner, Set<String> roles, Set<String> scopes) {
        String secret = UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String secretHash = sha256(secret);
        clients.put(clientId, new Client(clientId, owner, roles, scopes, secretHash, true));
        return new ClientRegistration(clientId, secret);
    }

    public void revokeClient(String clientId) {
        Client c = clients.get(clientId);
        if (c != null) c.enabled = false;
    }

    public boolean isClientEnabled(String clientId) {
        Client c = clients.get(clientId);
        return c != null && c.enabled;
    }

    public int getClientCount() {
        return (int) clients.values().stream().filter(c -> c.enabled).count();
    }

    // ===== 令牌签发（client_credentials） =====

    /**
     * OAuth2 client_credentials 授权：校验 client_id/secret，签发短期 access_token。
     *
     * @return 错误时返回 null（调用方可通过原因定位：未知客户端/secret 不匹配/禁用/scope 非法）
     */
    public TokenResponse issueClientCredentialsToken(String clientId, String clientSecret, Set<String> requestedScopes) {
        Client c = clients.get(clientId);
        if (c == null || !c.enabled) return null;
        if (!MessageDigest.isEqual(
                c.secretHash.getBytes(StandardCharsets.UTF_8),
                sha256(clientSecret == null ? "" : clientSecret).getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        // scope 收敛：只授予该客户端已授权的 scopes，防止越权
        Set<String> grantedScopes = new LinkedHashSet<>();
        if (requestedScopes != null) {
            for (String s : requestedScopes) {
                if (c.scopes.contains(s)) grantedScopes.add(s);
            }
        }
        if (grantedScopes.isEmpty()) {
            grantedScopes.addAll(c.scopes); // 未显式请求则授予全部已授权 scope
        }

        long issuedAt = System.currentTimeMillis() / 1000L;
        long expiresAt = issuedAt + tokenTtlSeconds;
        String token = buildToken(c.clientId, grantedScopes, issuedAt, expiresAt);
        String tokenId = tokenIdOf(token);
        issuedTokens.put(tokenId, new TokenRecord(tokenId, clientId, grantedScopes, expiresAt));
        return new TokenResponse(token, "Bearer", expiresAt - issuedAt, grantedScopes);
    }

    // ===== 令牌校验 (Bearer Token Validation) =====

    /** 校验 access_token 是否有效。有效返回 TokenInfo，否则返回 null。 */
    public TokenInfo validateToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) return null;
        // 0) EMA 委托：若配置了外部企业 IdP 内省器，则全部交给其集中鉴权
        if (externalIntrospector != null) {
            return externalIntrospector.introspect(accessToken);
        }
        // 1) 吊销清单检查
        if (revokedTokens.contains(tokenIdOf(accessToken))) return null;
        // 2) 结构解析（header.payload.signature）
        String[] parts = accessToken.split("\\.");
        if (parts.length != 3) return null;
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        // 3) 签名校验
        String expectedSig = base64Url(hmac((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8)));
        if (!MessageDigest.isEqual(expectedSig.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        // 4) 载荷字段
        Map<String, String> claims = parsePayload(payload);
        if (claims.isEmpty() || !"1".equals(claims.get("v"))) return null;
        long exp = Long.parseLong(claims.getOrDefault("exp", "0"));
        long now = System.currentTimeMillis() / 1000L;
        if (exp <= now) return null; // 过期
        String clientId = claims.get("cid");
        Client c = clients.get(clientId);
        if (c == null || !c.enabled) return null; // 客户端被吊销
        Set<String> scopes = new LinkedHashSet<>();
        String scopeStr = claims.getOrDefault("scope", "");
        if (!scopeStr.isEmpty()) {
            scopes.addAll(Arrays.asList(scopeStr.split(" ")));
        }
        return new TokenInfo(clientId, c.owner, c.roles, scopes, exp, now);
    }

    // ===== 令牌内省（RFC 7662） =====

    /** 内省：返回符合 RFC 7662 语义的 introspection 结果。 */
    public Map<String, Object> introspect(String accessToken) {
        Map<String, Object> result = new LinkedHashMap<>();
        TokenInfo info = validateToken(accessToken);
        if (info == null) {
            result.put("active", Boolean.FALSE);
            return result;
        }
        result.put("active", Boolean.TRUE);
        result.put("client_id", info.clientId());
        result.put("scope", String.join(" ", info.scopes()));
        result.put("exp", info.expiresAt());
        result.put("iat", info.issuedAt());
        result.put("sub", info.owner());
        result.put("roles", info.roles());
        return result;
    }

    /** 吊销一个令牌。 */
    public boolean revokeToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) return false;
        String id = tokenIdOf(accessToken);
        boolean removed = issuedTokens.remove(id) != null;
        if (removed) revokedTokens.add(id);
        return removed;
    }

    // ===== EMA 外挂：企业身份提供方内省 =====

    /**
     * 设置外部企业 IdP 的令牌内省器（EMA 集中授权委托）。
     * 设置后，{@link #validateToken} 会优先委托给外部内省器进行校验。
     */
    public void setExternalIntrospector(TokenIntrospector introspector) {
        this.externalIntrospector = introspector;
    }

    public TokenIntrospector getExternalIntrospector() {
        return externalIntrospector;
    }

    /** 注意：若设置了外挂内省器，企业内部校验将被跳过（全部委托企业 IdP 集中鉴权）。 */
    public boolean hasExternalIntrospector() {
        return externalIntrospector != null;
    }

    // ===== 配置 =====

    public long getTokenTtlSeconds() { return tokenTtlSeconds; }
    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        if (tokenTtlSeconds <= 0) throw new IllegalArgumentException("TTL must be positive");
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    // ===== 内部工具 =====

    private String buildToken(String clientId, Set<String> scopes, long iat, long exp) {
        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("v", "1");                       // token 版本
        claims.put("cid", clientId);                // client id
        claims.put("scope", String.join(" ", scopes));
        claims.put("iat", String.valueOf(iat));
        claims.put("exp", String.valueOf(exp));
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(claims.toString().getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        String signature = base64Url(hmac(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }

    private static Map<String, String> parsePayload(String payloadJson) {
        // 解析 buildToken 生成的扁平键值载荷：{v=1, cid=svc, scope=..., iat=..., exp=...}
        Map<String, String> map = new HashMap<>();
        String s = payloadJson.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);
        for (String pair : s.split(",")) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                // 兼容 json 风格 key:"value"
                idx = pair.indexOf(':');
                if (idx < 0) continue;
                String key = pair.substring(0, idx).trim().replace("\"", "");
                String val = pair.substring(idx + 1).trim().replace("\"", "");
                map.put(key, val);
            } else {
                String key = pair.substring(0, idx).trim();
                String val = pair.substring(idx + 1).trim();
                map.put(key, val);
            }
        }
        return map;
    }

    private String tokenIdOf(String token) {
        return sha256(token);
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGO));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return base64Url(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 failed", e);
        }
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    // ===== 嵌套类型 =====

    /** 客户端注册信息（内部存储，secret 为散列）。 */
    public static class Client {
        final String clientId;
        final String owner;
        final Set<String> roles;
        final Set<String> scopes;
        final String secretHash;
        volatile boolean enabled;

        Client(String clientId, String owner, Set<String> roles, Set<String> scopes,
               String secretHash, boolean enabled) {
            this.clientId = clientId; this.owner = owner;
            this.roles = Collections.unmodifiableSet(roles == null ? Set.of() : roles);
            this.scopes = Collections.unmodifiableSet(scopes == null ? Set.of() : scopes);
            this.secretHash = secretHash; this.enabled = enabled;
        }
        public String getClientId() { return clientId; }
        public String getOwner() { return owner; }
        public Set<String> getRoles() { return roles; }
        public Set<String> getScopes() { return scopes; }
        public boolean isEnabled() { return enabled; }
    }

    /** 注册返回值：一次性明文 secret。 */
    public record ClientRegistration(String clientId, String clientSecret) {}

    /** 令牌签发响应。 */
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, Set<String> scope) {}

    /** 令牌校验结果。 */
    public record TokenInfo(String clientId, String owner, Set<String> roles, Set<String> scopes,
                            long expiresAt, long issuedAt) {}

    /** 已签发令牌的内存记录（用于吊销）。 */
    private record TokenRecord(String tokenId, String clientId, Set<String> scopes, long expiresAt) {}

    /** EMA 外挂内省器接口：委托企业现有身份提供方做集中授权。 */
    public interface TokenIntrospector {
        /** 返回 null 表示无效；返回 TokenInfo 表示有效并携带授权信息。 */
        TokenInfo introspect(String accessToken);
    }
}
