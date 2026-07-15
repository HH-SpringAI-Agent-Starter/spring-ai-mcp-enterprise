package com.mcp.enterprise.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP API Key 认证过滤器
 *
 * 从请求头 X-API-Key 或 Authorization: Bearer <token> 中提取凭证
 * 支持两种凭证：
 * 1. API Key（向后兼容）
 * 2. JWT 会话令牌（mcp-auth 模块发行）
 */
public class McpApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpApiKeyAuthFilter.class);

    private final McpAuthProperties properties;
    private volatile McpJwtTokenProvider jwtTokenProvider;

    public McpApiKeyAuthFilter(McpAuthProperties properties) {
        this.properties = properties;
    }

    public void setJwtTokenProvider(McpJwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 健康检查端点免认证
        String path = request.getRequestURI();
        if (path.equals("/api/mcp/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. 尝试从请求头获取凭证
        String apiKey = request.getHeader("X-API-Key");
        String authHeader = request.getHeader("Authorization");

        String credential = null;
        boolean isJwt = false;

        if (apiKey != null && !apiKey.isBlank()) {
            credential = apiKey;
        } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
            credential = authHeader.substring(7);
            isJwt = true;
        }

        if (credential == null || credential.isBlank()) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("""
                    {"success":false,"error":"Missing credentials","message":"需要 X-API-Key 或 Authorization: Bearer <token>"}""");
            return;
        }

        // 2. 验证凭证
        Set<String> roles;
        String principal;

        if (isJwt && jwtTokenProvider != null) {
            // JWT 令牌验证
            var result = jwtTokenProvider.validateToken(credential);
            if (!result.valid()) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("""
                        {"success":false,"error":"Invalid or expired token"}""");
                return;
            }
            roles = result.roles();
            principal = result.subject();
        } else {
            // API Key 验证（使用默认角色）
            roles = Set.of("ROLE_USER");
            principal = "api-key-user";
        }

        // 3. 构建 Spring Security 认证对象
        List<GrantedAuthority> authorities = roles.stream()
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, credential, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
