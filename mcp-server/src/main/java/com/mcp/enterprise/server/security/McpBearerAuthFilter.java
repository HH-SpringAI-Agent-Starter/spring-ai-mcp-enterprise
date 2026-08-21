package com.mcp.enterprise.server.security;

import com.mcp.enterprise.autoconfigure.McpEnterpriseProperties;
import com.mcp.enterprise.core.security.McpOAuth2Manager;
import com.mcp.enterprise.core.security.McpSecurityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * V1.9 网关 Bearer 自动校验过滤器。
 *
 * <p>当 {@code mcp.enterprise.security.oauth2.enforce-bearer=true} 时，对所有非公开路径强制
 * {@code Authorization: Bearer <access_token>} 校验（fail-closed）：</p>
 *
 * <ul>
 *   <li>携带 Bearer → 调用 {@link McpOAuth2Manager#validateToken} 校验签名/过期/吊销/EMA 委托；
 *       通过后把 {@link McpOAuth2Manager.TokenInfo} 写入 request attribute {@code mcp.tokenInfo}，
 *       下游 Controller 可直接读取（如做 scope/RBAC 二次鉴权）</li>
 *   <li>校验失败 → 401 + {@code WWW-Authenticate: Bearer}（防止令牌状态被探测）</li>
 *   <li>未携带 Bearer 但带旧版 {@code X-API-Key} → 平滑迁移：委托给 McpSecurityManager 校验</li>
 *   <li>两者皆无 → 401</li>
 * </ul>
 *
 * <p>公开路径自动放行：{@code /oauth2/**}（token/introspect/revoke 自带 client 认证）、
 * {@code /api/mcp/health}、{@code /actuator/**}、{@code /error}、OPTIONS（CORS 预检）。</p>
 */
@Component
public class McpBearerAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpBearerAuthFilter.class);

    /** TokenInfo 的 request attribute key，下游可读取做 scope 级鉴权 */
    public static final String ATTR_TOKEN_INFO = "mcp.tokenInfo";

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/oauth2/", "/actuator/", "/api/mcp/health", "/error", "/swagger-ui", "/v3/api-docs"
    );

    private final McpOAuth2Manager oauth2Manager;
    private final McpSecurityManager securityManager;
    private final McpEnterpriseProperties properties;

    public McpBearerAuthFilter(McpOAuth2Manager oauth2Manager,
                               McpSecurityManager securityManager,
                               McpEnterpriseProperties properties) {
        this.oauth2Manager = oauth2Manager;
        this.securityManager = securityManager;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        if (path == null) return true;
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 开关默认关闭（兼容现有 X-API-Key 调用方），配置开启后强制 Bearer 校验
        if (!properties.getOauth2().isEnforceBearer()) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authHeader.substring(7).trim();
            McpOAuth2Manager.TokenInfo info = oauth2Manager.validateToken(token);
            if (info == null) {
                log.warn("🔐 Bearer 校验失败: path={} client={}", request.getRequestURI(), "unknown");
                unauthorized(response, "invalid_token", "Token invalid, expired, or revoked");
                return;
            }
            request.setAttribute(ATTR_TOKEN_INFO, info);
            filterChain.doFilter(request, response);
            return;
        }

        // 平滑迁移：旧版 X-API-Key 调用方（V1.9 前接入的客户端）
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank() && securityManager != null) {
            if (Boolean.TRUE.equals(securityManager.validateApiKey(apiKey).block())) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        unauthorized(response, "missing_token", "Bearer token or X-API-Key required");
    }

    private void unauthorized(HttpServletResponse response, String code, String desc) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader("WWW-Authenticate", "Bearer realm=\"mcp-enterprise\", error=\"" + code + "\"");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"error\":\"" + code + "\",\"error_description\":\"" + desc + "\"}";
        response.getWriter().write(body);
    }
}