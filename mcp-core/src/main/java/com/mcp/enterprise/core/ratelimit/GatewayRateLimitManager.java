package com.mcp.enterprise.core.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关限流路由表 — 按 Mcp-Method / Mcp-Name 维度配置速率限制
 *
 * V1.7: 2026-07-28 无状态化规范「网关按操作限流」的完整落地。
 * API 网关（或本 Server）无需解析 JSON 请求体，仅凭 Mcp-Method / Mcp-Name
 * 标头即可对每个操作应用独立的限流规则。
 *
 * 规则匹配语义：
 *   - namePattern 支持精确匹配与通配符 {@code *}（{@code greet}、{@code *}、{@code finance_*}）
 *   - 具体规则优先于通配规则；同一 method 下 name 精确匹配 > 前缀通配 > 全通配
 *   - 未命中任何规则 → 默认放行（由全局限流兜底）
 *
 * 示例规则：
 *   tools/call:*          → 100 QPS  （所有工具调用总限流）
 *   tools/call:greet      → 10 QPS   （单工具限流）
 *   tools/call:finance_*  → 20 QPS   （金融工具组限流）
 *   tools/list:           → 5 QPS    （目录拉取限流，防刷）
 *   ping:                 → 20 QPS
 */
public class GatewayRateLimitManager {

    private static final Logger log = LoggerFactory.getLogger(GatewayRateLimitManager.class);

    /** 规则上限，防止配置失控 */
    private static final int MAX_RULES = 256;

    /** 规则列表（CopyOnWriteArrayList：读多写少） */
    private final List<RateLimitRule> rules = new CopyOnWriteArrayList<>();

    /** 已实例化的限流器缓存：key = method + ":" + name */
    private final ConcurrentHashMap<String, TokenBucket> limiterCache = new ConcurrentHashMap<>();

    /** 是否启用（默认启用） */
    private volatile boolean enabled = true;

    public GatewayRateLimitManager() {
        log.info("🚦 网关限流路由表已初始化");
    }

    /**
     * 添加限流规则（具体规则自动优先于通配规则）
     *
     * @param methodPattern Mcp-Method 匹配模式（tools/call、tools/list、*）
     * @param namePattern   Mcp-Name 匹配模式（greet、*、finance_*；空串表示无 name 的操作）
     * @param maxPerSecond  每秒最大请求数
     */
    public void addRule(String methodPattern, String namePattern, int maxPerSecond) {
        if (rules.size() >= MAX_RULES) {
            log.warn("网关限流规则超出上限({})，忽略规则 {}/{}", MAX_RULES, methodPattern, namePattern);
            return;
        }
        if (maxPerSecond <= 0) {
            throw new IllegalArgumentException("maxPerSecond must be positive: " + maxPerSecond);
        }
        RateLimitRule rule = new RateLimitRule(methodPattern, namePattern, maxPerSecond);
        rules.removeIf(r -> r.methodPattern.equals(methodPattern) && r.namePattern.equals(namePattern));
        rules.add(rule);
        // 清理相关缓存，避免旧限流器残留（按 method:name 前缀保守清理）
        limiterCache.entrySet().removeIf(e -> e.getKey().startsWith(methodPattern + ":"));
        log.info("🚦 限流规则已添加: {}:{} → {} QPS", methodPattern, namePattern, maxPerSecond);
    }

    /** 移除规则 */
    public boolean removeRule(String methodPattern, String namePattern) {
        boolean removed = rules.removeIf(r -> r.methodPattern.equals(methodPattern) && r.namePattern.equals(namePattern));
        if (removed) {
            // 清理相关限流器缓存（保守：按 method:name 前缀清理，同模式规则已删，缓存无引用风险）
            limiterCache.entrySet().removeIf(e -> e.getKey().startsWith(methodPattern + ":"));
        }
        return removed;
    }

    /** 清空所有规则 */
    public void clearRules() {
        rules.clear();
        limiterCache.clear();
    }

    /**
     * 检查一次网关调用是否允许通过
     *
     * @param method Mcp-Method 标头值（可为 null → "unknown"）
     * @param name   Mcp-Name 标头值（可为 null → ""）
     * @return true=放行, false=限流拒绝
     */
    public boolean checkRateLimit(String method, String name) {
        if (!enabled) {
            return true;
        }
        String methodKey = method == null || method.isBlank() ? "unknown" : method;
        String nameKey = name == null ? "" : name;
        String opKey = methodKey + ":" + nameKey;

        RateLimitRule rule = findBestMatch(methodKey, nameKey);
        if (rule == null) {
            return true; // 未配置规则 → 放行（由全局限流兜底）
        }

        TokenBucket bucket = limiterCache.computeIfAbsent(
                opKey + "#" + rule.id(),
                k -> new TokenBucket(rule.maxPerSecond()));
        return bucket.tryAcquire();
    }

    /**
     * 查找最佳匹配规则：方法精确匹配优先于通配；
     * 同一方法下 name 精确 > 前缀通配 > 全通配。
     */
    RateLimitRule findBestMatch(String method, String name) {
        RateLimitRule best = null;
        int bestScore = -1;
        for (RateLimitRule rule : rules) {
            int score = rule.matchScore(method, name);
            if (score > bestScore) {
                bestScore = score;
                best = rule;
            }
        }
        return best;
    }

    /** 当前规则数 */
    public int getRuleCount() {
        return rules.size();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** 规则快照（管理端点用） */
    public List<Map<String, Object>> getRuleSnapshot() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (RateLimitRule rule : rules) {
            list.add(Map.of(
                    "method", rule.methodPattern(),
                    "name", rule.namePattern(),
                    "maxPerSecond", rule.maxPerSecond()
            ));
        }
        return list;
    }

    // ===== 内部类 =====

    /**
     * 限流规则：method/name 模式 + 每秒配额
     * id() 用于区分不同规则实例的限流器缓存（同模式重配时隔离旧桶）
     */
    public static final class RateLimitRule {
        private static final AtomicLong SEQ = new AtomicLong();
        private final String methodPattern;
        private final String namePattern;
        private final int maxPerSecond;
        private final long id = SEQ.incrementAndGet();

        RateLimitRule(String methodPattern, String namePattern, int maxPerSecond) {
            this.methodPattern = methodPattern;
            this.namePattern = namePattern;
            this.maxPerSecond = maxPerSecond;
        }

        public String methodPattern() { return methodPattern; }
        public String namePattern() { return namePattern; }
        public int maxPerSecond() { return maxPerSecond; }
        public long id() { return id; }

        /** 匹配打分：返回匹配优先级分数，不匹配返回 -1 */
        int matchScore(String method, String name) {
            if (!wildcardMatch(methodPattern, method)) {
                return -1;
            }
            if (namePattern.isEmpty()) {
                // name 模式为空 → 仅匹配空 name 的操作
                return name.isEmpty() ? 30 : -1;
            }
            if (!wildcardMatch(namePattern, name)) {
                return -1;
            }
            // name 匹配：精确 > 前缀通配 > 全通配
            if (namePattern.equals(name)) return 30;
            if (namePattern.endsWith("*") && !namePattern.equals("*")) return 20;
            return 10; // "*"
        }

        /** 通配符匹配：* 匹配任意序列（含空） */
        static boolean wildcardMatch(String pattern, String value) {
            if (pattern.equals("*")) return true;
            if (pattern.indexOf('*') < 0) return pattern.equals(value);
            String prefix = pattern.substring(0, pattern.indexOf('*'));
            String suffix = pattern.substring(pattern.indexOf('*') + 1);
            if (suffix.isEmpty()) {
                return value.startsWith(prefix);
            }
            return value.startsWith(prefix) && value.endsWith(suffix)
                    && value.length() >= prefix.length() + suffix.length();
        }
    }

    /** 简单令牌桶（与 McpSecurityManager.RateLimiter 同语义，独立实现避免耦合） */
    static final class TokenBucket {
        private final int maxPerSecond;
        private final AtomicLong lastRefill = new AtomicLong(System.nanoTime());
        private final AtomicLong tokens;

        TokenBucket(int maxPerSecond) {
            this.maxPerSecond = maxPerSecond;
            this.tokens = new AtomicLong(maxPerSecond);
        }

        boolean tryAcquire() {
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
}
