package com.mcp.enterprise.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;

/**
 * MCP Enterprise OAuth2/SSO 安全配置
 *
 * 三种认证模式：
 * 1. none - 完全开放（仅开发环境）
 * 2. api-key - API Key 认证（向后兼容，默认）
 * 3. oauth2 - OAuth2/OIDC 企业认证（符合 MCP Enterprise Auth 规范）
 *
 * 符合 2026-07-13 MCP 企业统一授权规范：
 * - 支持身份提供商（Keycloak/Okta/Azure AD）
 * - 零接触访问（一次登录，无需逐服务器授权）
 * - 集中权限管控
 */
@Configuration
@EnableWebSecurity
public class McpAuthSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(McpAuthSecurityConfig.class);

    private final McpAuthProperties properties;

    public McpAuthSecurityConfig(McpAuthProperties properties) {
        this.properties = properties;
        log.info("🔐 MCP 企业认证模式: {}", properties.getMode());
    }

    /**
     * API Key 认证模式（默认）
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.auth.mode", havingValue = "api-key", matchIfMissing = true)
    @Order(1)
    public SecurityFilterChain apiKeyFilterChain(HttpSecurity http) throws Exception {
        log.info("   → API Key 认证模式（向后兼容）");
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/mcp/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new McpApiKeyAuthFilter(properties), org.springframework.security.web.access.ExceptionTranslationFilter.class)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /**
     * OAuth2/OIDC 企业认证模式（符合 MCP Enterprise Auth 规范）
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.auth.mode", havingValue = "oauth2")
    @Order(1)
    public SecurityFilterChain oauth2FilterChain(HttpSecurity http) throws Exception {
        log.info("   → OAuth2/OIDC 企业认证模式（MCP Enterprise Auth 规范）");

        http.securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/mcp/health", "/api/auth/**", "/login/**", "/oauth2/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/api/auth/login")
                        .defaultSuccessUrl("/api/auth/callback", true)
                )
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.decoder(jwtDecoder()))
                )
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        return http.build();
    }

    /**
     * 无认证模式（开发环境）
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.auth.mode", havingValue = "none")
    @Order(1)
    public SecurityFilterChain noAuthFilterChain(HttpSecurity http) throws Exception {
        log.warn("   ⚠️ 无认证模式 - 仅用于开发环境！");
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /**
     * JWT Decoder（OAuth2 模式用）
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.auth.mode", havingValue = "oauth2")
    public JwtDecoder jwtDecoder() {
        String issuerUri = properties.getOauth2().getIssuerUri();
        if (issuerUri != null && !issuerUri.isBlank()) {
            log.info("   → 使用 OIDC Issuer URI: {}", issuerUri);
            try {
                return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
            } catch (Exception e) {
                log.warn("   ⚠️ 无法从 Issuer URI 获取 JWKS，回退到本地密钥：{}", e.getMessage());
            }
        }
        // 回退：本地密钥解码
        String secret = properties.getJwtSecret();
        if (secret != null && !secret.isBlank()) {
            byte[] keyBytes = secret.length() < 32
                    ? java.util.Arrays.copyOf(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), 32)
                    : secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
            return NimbusJwtDecoder.withSecretKey(key).build();
        }
        log.warn("   ⚠️ 未配置 JWT 密钥，OAuth2 模式可能无法正常工作");
        return token -> null;
    }

    @Bean
    @ConditionalOnMissingBean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
