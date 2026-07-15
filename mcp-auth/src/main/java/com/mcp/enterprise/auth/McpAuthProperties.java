package com.mcp.enterprise.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 企业认证配置属性
 *
 * 支持多种身份提供商（IdP）：
 * - Keycloak
 * - Okta
 * - Azure AD / Entra ID
 * - 自定义 OIDC 提供商
 *
 * 符合 MCP Enterprise Auth 规范（2026-07-13 稳定版）
 */
@ConfigurationProperties(prefix = "mcp.auth")
public class McpAuthProperties {

    /** 认证模式：none | api-key | oauth2 | oidc */
    private String mode = "api-key";

    /** JWT 密钥（自签名模式使用） */
    private String jwtSecret = "";

    /** JWT 过期时间（分钟） */
    private long jwtExpirationMinutes = 60;

    /** 是否启用会话令牌（替代每次请求带 API Key） */
    private boolean sessionTokens = true;

    /** OAuth2 提供商配置 */
    private final OAuth2 oauth2 = new OAuth2();

    /** 身份提供商列表 */
    private final List<IdentityProvider> identityProviders = new ArrayList<>();

    /** RBAC 角色映射 */
    private final RoleMapping roleMapping = new RoleMapping();

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }

    public long getJwtExpirationMinutes() { return jwtExpirationMinutes; }
    public void setJwtExpirationMinutes(long jwtExpirationMinutes) { this.jwtExpirationMinutes = jwtExpirationMinutes; }

    public boolean isSessionTokens() { return sessionTokens; }
    public void setSessionTokens(boolean sessionTokens) { this.sessionTokens = sessionTokens; }

    public OAuth2 getOauth2() { return oauth2; }
    public List<IdentityProvider> getIdentityProviders() { return identityProviders; }
    public RoleMapping getRoleMapping() { return roleMapping; }

    public static class OAuth2 {
        /** 授权服务器 URI */
        private String issuerUri = "";
        /** 客户端 ID */
        private String clientId = "";
        /** 客户端密钥 */
        private String clientSecret = "";
        /** 额外的 scope */
        private List<String> scopes = List.of("openid", "profile", "email");

        public String getIssuerUri() { return issuerUri; }
        public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public List<String> getScopes() { return scopes; }
        public void setScopes(List<String> scopes) { this.scopes = scopes; }
    }

    public static class IdentityProvider {
        /** 提供商名称（如 keycloak, okta, azure-ad） */
        private String name;
        /** 显示名称 */
        private String displayName;
        /** 颁发者 URI */
        private String issuerUri;
        /** 客户端 ID */
        private String clientId;
        /** 客户端密钥 */
        private String clientSecret;
        /** 授权端点 */
        private String authorizationUri;
        /** 令牌端点 */
        private String tokenUri;
        /** JWKS URI（令牌验证） */
        private String jwksUri;
        /** 用户信息端点 */
        private String userInfoUri;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getIssuerUri() { return issuerUri; }
        public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getAuthorizationUri() { return authorizationUri; }
        public void setAuthorizationUri(String authorizationUri) { this.authorizationUri = authorizationUri; }
        public String getTokenUri() { return tokenUri; }
        public void setTokenUri(String tokenUri) { this.tokenUri = tokenUri; }
        public String getJwksUri() { return jwksUri; }
        public void setJwksUri(String jwksUri) { this.jwksUri = jwksUri; }
        public String getUserInfoUri() { return userInfoUri; }
        public void setUserInfoUri(String userInfoUri) { this.userInfoUri = userInfoUri; }
    }

    public static class RoleMapping {
        /** 管理员角色名单（在 IdP 中的角色名） */
        private List<String> adminRoles = List.of("admin", "mcp-admin", "ROLE_ADMIN");
        /** 用户角色名单 */
        private List<String> userRoles = List.of("user", "mcp-user", "ROLE_USER");
        /** 只读角色名单 */
        private List<String> readOnlyRoles = List.of("viewer", "readonly", "ROLE_VIEWER");

        /** 角色 -> MCP 角色 映射前缀 */
        private String rolePrefix = "ROLE_";

        public List<String> getAdminRoles() { return adminRoles; }
        public void setAdminRoles(List<String> adminRoles) { this.adminRoles = adminRoles; }
        public List<String> getUserRoles() { return userRoles; }
        public void setUserRoles(List<String> userRoles) { this.userRoles = userRoles; }
        public List<String> getReadOnlyRoles() { return readOnlyRoles; }
        public void setReadOnlyRoles(List<String> readOnlyRoles) { this.readOnlyRoles = readOnlyRoles; }
        public String getRolePrefix() { return rolePrefix; }
        public void setRolePrefix(String rolePrefix) { this.rolePrefix = rolePrefix; }
    }
}
