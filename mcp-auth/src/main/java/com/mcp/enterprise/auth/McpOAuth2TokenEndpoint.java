package com.mcp.enterprise.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OAuth2 Token Endpoint（Client Credentials 模式）
 *
 * <p>符合 OAuth 2.0 RFC 6749 Section 4.4 与 MCP Enterprise Auth 规范：
 * 支持「机器对机器」（service-to-service）授权——AI Agent / 集成服务
 * 用 client_id + client_secret 换取访问令牌，无需用户交互。</p>
 *
 * <p>端点：{@code POST /api/auth/oauth2/token}</p>
 *
 * <p>请求体（application/x-www-form-urlencoded）：</p>
 * <pre>
 * grant_type=client_credentials
 * &client_id=my-service
 * &client_secret=my-secret
 * &scope=tools:read
 * </pre>
 *
 * <p>响应：</p>
 * <pre>
 * { "access_token": "...", "token_type": "Bearer", "expires_in": 3600 }
 * </pre>
 */
@RestController
@RequestMapping("/api/auth/oauth2")
@ConditionalOnProperty(name = "mcp.auth.oauth2.client-credentials-enabled", havingValue = "true", matchIfMissing = true)
public class McpOAuth2TokenEndpoint {

    private static final Logger log = LoggerFactory.getLogger(McpOAuth2TokenEndpoint.class);

    /** 默认服务账户密钥（生产环境必须修改） */
    public static final String DEFAULT_SERVICE_SECRET = "change-me-client-secret";

    /** 已注册的服务账户：clientId -> {secret, roles} */
    private final Map<String, ServiceAccount> serviceAccounts = new ConcurrentHashMap<>();
    private final McpAuthProperties properties;
    private final McpJwtTokenProvider tokenProvider;

    public McpOAuth2TokenEndpoint(McpAuthProperties properties, McpJwtTokenProvider tokenProvider) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        registerDefaultAccounts();
    }

    private void registerDefaultAccounts() {
        // 默认服务账户（生产环境必须通过管理端点/配置覆盖此密钥）
        serviceAccounts.put("mcp-service", new ServiceAccount(DEFAULT_SERVICE_SECRET, Set.of("admin"), new AtomicInteger(0)));
        log.info("🔐 已注册默认服务账户: mcp-service (client-credentials 模式，生产环境请修改默认密钥)");
    }

    /**
     * 注册服务账户（供管理端点调用）
     */
    public void registerServiceAccount(String clientId, String clientSecret, Set<String> roles) {
        serviceAccounts.put(clientId, new ServiceAccount(clientSecret, roles, new AtomicInteger(0)));
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam(value = "scope", required = false) String scope) {

        // 1. 仅支持 client_credentials
        if (!"client_credentials".equals(grantType)) {
            return error("unsupported_grant_type", "仅支持 client_credentials 授权模式");
        }

        // 2. 校验客户端凭证
        ServiceAccount account = serviceAccounts.get(clientId);
        if (account == null || !account.secret().equals(clientSecret)) {
            log.warn("🔐 client-credentials 认证失败: client_id={}", clientId);
            return error("invalid_client", "客户端凭证无效");
        }

        // 3. 签发访问令牌
        account.counter().incrementAndGet();
        String token = tokenProvider.generateToken(
                "service:" + clientId,
                account.roles(),
                Map.of("authMethod", "client-credentials", "scope", scope == null ? "" : scope)
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", token);
        response.put("token_type", "Bearer");
        response.put("expires_in", properties.getJwtExpirationMinutes() * 60);
        response.put("scope", scope == null ? "default" : scope);

        log.info("🔐 client-credentials 签发令牌: client_id={}, scope={}", clientId, scope);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> error(String code, String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("error_description", description);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(401).body(body);
    }

    /** 服务账户记录 */
    private record ServiceAccount(String secret, Set<String> roles, AtomicInteger counter) {
    }
}
