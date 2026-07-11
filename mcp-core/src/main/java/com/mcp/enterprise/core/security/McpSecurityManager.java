package com.mcp.enterprise.core.security;

import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP 安全管理器
 *
 * 企业级 MCP 的核心安全组件，包含：
 * 1. API Key 认证
 * 2. 角色鉴权（RBAC）
 * 3. 频率限制（Rate Limiting）
 * 4. 调用审计日志
 * 5. IP 白名单
 */
public class McpSecurityManager {

    private final Map<String, ApiKeyInfo> apiKeys = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final List<AuditLogEntry> auditLog = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> ipWhitelist = ConcurrentHashMap.newKeySet();

    /** 最大审计日志条目 */
    private int maxAuditLogSize = 10000;

    // ===== API Key 管理 =====

    public String createApiKey(String owner, Set<String> roles) {
        String key = UUID.randomUUID().toString().replace("-", "");
        apiKeys.put(key, new ApiKeyInfo(key, owner, roles, System.currentTimeMillis(), true));
        return key;
    }

    public void revokeApiKey(String key) {
        ApiKeyInfo info = apiKeys.get(key);
        if (info != null) info.setActive(false);
    }

    public Mono<Boolean> validateApiKey(String key) {
        ApiKeyInfo info = apiKeys.get(key);
        if (info == null || !info.isActive()) return Mono.just(false);
        return Mono.just(true);
    }

    // ===== RBAC 鉴权 =====

    public Mono<Boolean> checkPermission(String apiKey, String toolName, String requiredRole) {
        ApiKeyInfo info = apiKeys.get(apiKey);
        if (info == null || !info.isActive()) return Mono.just(false);
        if (requiredRole == null || requiredRole.isEmpty()) return Mono.just(true);
        return Mono.just(info.getRoles().contains(requiredRole));
    }

    // ===== 频率限制 =====

    public boolean checkRateLimit(String key, int maxPerSecond) {
        RateLimiter limiter = rateLimiters.computeIfAbsent(key, k -> new RateLimiter(maxPerSecond));
        return limiter.tryAcquire();
    }

    // ===== 审计日志 =====

    public void audit(String apiKey, String toolName, String action, boolean success, String detail) {
        if (auditLog.size() >= maxAuditLogSize) {
            auditLog.subList(0, 1000).clear();
        }
        auditLog.add(new AuditLogEntry(apiKey, toolName, action, success, detail, System.currentTimeMillis()));
    }

    public List<AuditLogEntry> getAuditLog(int limit) {
        int size = auditLog.size();
        if (size == 0) return List.of();
        int from = Math.max(0, size - limit);
        return new ArrayList<>(auditLog.subList(from, size));
    }

    // ===== IP 白名单 =====

    public void addIpToWhitelist(String ip) { ipWhitelist.add(ip); }
    public void removeIpFromWhitelist(String ip) { ipWhitelist.remove(ip); }
    public boolean isIpAllowed(String ip) {
        return ipWhitelist.isEmpty() || ipWhitelist.contains(ip);
    }

    // ===== 内部类 =====

    public static class ApiKeyInfo {
        private final String key;
        private final String owner;
        private final Set<String> roles;
        private final long createdAt;
        private volatile boolean active;

        public ApiKeyInfo(String key, String owner, Set<String> roles, long createdAt, boolean active) {
            this.key = key; this.owner = owner; this.roles = roles; this.createdAt = createdAt; this.active = active;
        }
        public String getKey() { return key; }
        public String getOwner() { return owner; }
        public Set<String> getRoles() { return roles; }
        public long getCreatedAt() { return createdAt; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    public static class RateLimiter {
        private final int maxPerSecond;
        private final AtomicLong lastRefill = new AtomicLong(System.nanoTime());
        private final AtomicLong tokens;

        public RateLimiter(int maxPerSecond) {
            this.maxPerSecond = maxPerSecond;
            this.tokens = new AtomicLong(maxPerSecond);
        }

        public boolean tryAcquire() {
            long now = System.nanoTime();
            long last = lastRefill.get();
            long elapsed = now - last;
            if (elapsed > 1_000_000_000L) {
                long newTokens = Math.min(maxPerSecond, tokens.get() + (elapsed * maxPerSecond / 1_000_000_000L));
                if (lastRefill.compareAndSet(last, now)) {
                    tokens.set(newTokens);
                }
            }
            long t = tokens.get();
            while (t > 0) {
                if (tokens.compareAndSet(t, t - 1)) return true;
                t = tokens.get();
            }
            return false;
        }
    }

    public record AuditLogEntry(String apiKey, String toolName, String action, boolean success, String detail, long timestamp) {}
}
