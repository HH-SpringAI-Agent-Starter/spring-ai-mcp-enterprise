package com.mcp.enterprise.core.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分布式速率限制器 — V0.15 新增
 *
 * 支持两种模式：
 * 1. 内存模式（默认，单实例）：使用滑动窗口算法
 * 2. Redis 模式（多实例，生产环境）：使用 Redis Lua 脚本
 *
 * MCP 2026-07-28 无状态架构要求每个请求独立进行速率检查，
 * 因此速率限制组件必须支持分布式部署。
 *
 * 当前实现：内存滑动窗口（V0.15）
 * V0.16 计划：Redis 滑动窗口（支持多实例无状态部署）
 */
public class RedisRateLimiter {

    private static final Map<String, WindowCounter> WINDOWS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> GLOBAL_COUNTERS = new ConcurrentHashMap<>();

    private final long windowMs;
    private final int maxRequests;
    private final int globalMaxPerSecond;

    public RedisRateLimiter(long windowMs, int maxRequests, int globalMaxPerSecond) {
        this.windowMs = windowMs;
        this.maxRequests = maxRequests;
        this.globalMaxPerSecond = globalMaxPerSecond;
    }

    public RedisRateLimiter() {
        this(1000, 100, 1000);
    }

    public boolean tryAcquire(String apiKey) {
        if (!checkGlobalLimit()) {
            return false;
        }
        return checkUserLimit(apiKey);
    }

    public RateLimitInfo getInfo(String apiKey) {
        WindowCounter counter = WINDOWS.getOrDefault(apiKey, new WindowCounter(windowMs));
        return new RateLimitInfo(
                counter.getCurrentCount(),
                maxRequests,
                counter.getWindowStartMs(),
                windowMs,
                counter.getRemainingTokens(maxRequests)
        );
    }

    private boolean checkGlobalLimit() {
        long now = System.currentTimeMillis();
        String minuteKey = "global:" + (now / 1000);
        AtomicLong counter = GLOBAL_COUNTERS.computeIfAbsent(minuteKey, k -> new AtomicLong(0));
        return counter.incrementAndGet() <= globalMaxPerSecond;
    }

    private boolean checkUserLimit(String apiKey) {
        long now = System.currentTimeMillis();
        WindowCounter counter = WINDOWS.computeIfAbsent(apiKey, k -> new WindowCounter(windowMs));
        if (now - counter.getWindowStartMs() > windowMs) {
            WindowCounter newWindow = new WindowCounter(windowMs);
            newWindow.increment();
            WINDOWS.put(apiKey, newWindow);
            return true;
        }
        return counter.increment() <= maxRequests;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        WINDOWS.entrySet().removeIf(entry ->
                now - entry.getValue().getWindowStartMs() > windowMs * 2);
        GLOBAL_COUNTERS.entrySet().removeIf(entry -> {
            try {
                long timeKey = Long.parseLong(entry.getKey().replace("global:", ""));
                return (now / 1000) - timeKey > 2;
            } catch (NumberFormatException e) {
                return true;
            }
        });
    }

    private static class WindowCounter {
        private final AtomicLong count;
        private final long windowStartMs;
        private final long windowMs;

        WindowCounter(long windowMs) {
            this.count = new AtomicLong(0);
            this.windowStartMs = System.currentTimeMillis();
            this.windowMs = windowMs;
        }

        long increment() { return count.incrementAndGet(); }
        long getCurrentCount() { return count.get(); }
        long getWindowStartMs() { return windowStartMs; }
        long getRemainingTokens(int maxRequests) { return Math.max(0, maxRequests - count.get()); }
    }

    public static class RateLimitInfo {
        private final long currentCount;
        private final int maxRequests;
        private final long windowStartMs;
        private final long windowMs;
        private final long remaining;

        public RateLimitInfo(long currentCount, int maxRequests, long windowStartMs,
                             long windowMs, long remaining) {
            this.currentCount = currentCount;
            this.maxRequests = maxRequests;
            this.windowStartMs = windowStartMs;
            this.windowMs = windowMs;
            this.remaining = remaining;
        }

        public long getCurrentCount() { return currentCount; }
        public int getMaxRequests() { return maxRequests; }
        public long getWindowStartMs() { return windowStartMs; }
        public long getWindowMs() { return windowMs; }
        public long getRemaining() { return remaining; }

        public Map<String, String> toResponseHeaders() {
            return Map.of(
                    "X-RateLimit-Limit", String.valueOf(maxRequests),
                    "X-RateLimit-Remaining", String.valueOf(remaining),
                    "X-RateLimit-Reset", String.valueOf((windowStartMs + windowMs) / 1000),
                    "X-RateLimit-Window", Duration.ofMillis(windowMs).toString()
            );
        }
    }
}
