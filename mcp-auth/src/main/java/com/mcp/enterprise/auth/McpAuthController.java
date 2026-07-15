package com.mcp.enterprise.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP 企业认证 REST 端点
 *
 * 提供 SSO 登录、令牌交换、会话管理接口
 * 符合 MCP Enterprise Auth 规范
 */
@RestController
@RequestMapping("/api/auth")
public class McpAuthController {

    private static final Logger log = LoggerFactory.getLogger(McpAuthController.class);

    private final McpAuthProperties properties;
    private final McpJwtTokenProvider tokenProvider;

    public McpAuthController(McpAuthProperties properties,
                             McpJwtTokenProvider tokenProvider) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
    }

    /**
     * SSO 登录页面信息
     */
    @GetMapping("/login")
    public Map<String, Object> loginPage() {
        String mode = properties.getMode();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authMode", mode);
        result.put("serverName", "Spring AI MCP Enterprise");

        if ("oauth2".equals(mode)) {
            result.put("loginUrl", "/oauth2/authorization/mcp");
            result.put("providers", properties.getIdentityProviders().stream()
                    .map(p -> Map.of("name", p.getName(), "displayName", p.getDisplayName()))
                    .toList());
        }

        result.put("supportedAuthMethods", List.of(
                Map.of("type", "api-key", "header", "X-API-Key"),
                Map.of("type", "bearer-token", "header", "Authorization: Bearer <token>"),
                Map.of("type", "oauth2-sso", "url", "/oauth2/authorization/mcp")
        ));

        return result;
    }

    /**
     * SSO 登录回调 - 将 OAuth2 令牌交换为 MCP 会话令牌
     */
    @GetMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(@AuthenticationPrincipal OAuth2User oauth2User) {
        if (oauth2User == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false, "error", "OAuth2 authentication failed"
            ));
        }

        String name = oauth2User.getAttribute("name");
        String email = oauth2User.getAttribute("email");
        String preferredUsername = oauth2User.getAttribute("preferred_username");
        String subject = email != null ? email : (preferredUsername != null ? preferredUsername : name);

        // 提取角色
        Set<String> roles = new HashSet<>();
        Object rolesAttr = oauth2User.getAttribute("roles");
        if (rolesAttr instanceof Collection<?> roleList) {
            roleList.forEach(r -> roles.add("ROLE_" + r.toString().toUpperCase()));
        }
        Object groupsAttr = oauth2User.getAttribute("groups");
        if (groupsAttr instanceof Collection<?> groupList) {
            groupList.forEach(g -> roles.add("ROLE_" + g.toString().toUpperCase()));
        }

        // 映射到 MCP 角色
        mapMcpRoles(roles);

        // 生成 MCP 会话令牌
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("email", email);
        extraClaims.put("name", name);
        extraClaims.put("authMethod", "oauth2-sso");

        String mcpToken = tokenProvider.generateToken(subject, roles, extraClaims);

        log.info("✅ SSO 登录成功: {} (roles: {})", subject, roles);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("token", mcpToken);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", tokenProvider.getExpirationMs() / 1000);
        response.put("user", Map.of(
                "name", name,
                "email", email,
                "username", preferredUsername,
                "roles", roles
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * 交换 API Key 为会话令牌（无状态 -> 有状态迁移）
     */
    @PostMapping("/exchange")
    public Map<String, Object> exchangeApiKey(@RequestHeader("X-API-Key") String apiKey) {
        String token = tokenProvider.generateTokenFromApiKey(apiKey, "api-key-user", Set.of("ROLE_USER"));

        return Map.of(
                "success", true,
                "token", token,
                "tokenType", "Bearer",
                "expiresIn", tokenProvider.getExpirationMs() / 1000,
                "message", "API Key 已交换为会话令牌。建议后续请求使用 Authorization: Bearer <token>"
        );
    }

    /**
     * 验证令牌（供客户端校验）
     */
    @PostMapping("/verify")
    public Map<String, Object> verifyToken(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        var result = tokenProvider.validateToken(token);

        if (!result.valid()) {
            return Map.of("valid", false, "error", "令牌无效或已过期");
        }

        return Map.of(
                "valid", true,
                "subject", result.subject(),
                "roles", result.roles(),
                "authMethod", result.authMethod(),
                "expiresAt", result.claims() != null ? result.claims().getExpiration() : null
        );
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Map<String, Object> currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Map.of("authenticated", false);
        }

        return Map.of(
                "authenticated", true,
                "name", authentication.getName(),
                "roles", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList()
        );
    }

    /**
     * 获取支持的 IdP 列表
     */
    @GetMapping("/providers")
    public List<Map<String, Object>> listProviders() {
        return properties.getIdentityProviders().stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", p.getName());
                    m.put("displayName", p.getDisplayName());
                    m.put("issuerUri", p.getIssuerUri());
                    return m;
                })
                .toList();
    }

    private void mapMcpRoles(Set<String> roles) {
        McpAuthProperties.RoleMapping mapping = properties.getRoleMapping();

        boolean isAdmin = roles.stream().anyMatch(r ->
                mapping.getAdminRoles().stream().anyMatch(adminRole ->
                        r.equalsIgnoreCase("ROLE_" + adminRole) || r.equalsIgnoreCase(adminRole)));

        boolean isUser = roles.stream().anyMatch(r ->
                mapping.getUserRoles().stream().anyMatch(userRole ->
                        r.equalsIgnoreCase("ROLE_" + userRole) || r.equalsIgnoreCase(userRole)));

        if (isAdmin) roles.add("ROLE_ADMIN");
        if (isUser) roles.add("ROLE_USER");
    }
}
