package com.mcp.enterprise.core.security;

import com.mcp.enterprise.core.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具级 Scope 授权策略（V1.19）。
 *
 * <p>把 OAuth2 令牌的 {@code scope} claim 映射为 MCP 工具级权限（Tool ACL），
 * 让「拿到令牌」与「能调用哪些工具」解耦——对标企业 JD 中的
 * per-user scoping（Greelow）、token permissions（Cotality）、per-user quota（TalentAlly）。</p>
 *
 * <p>策略解析优先级（由高到低）：</p>
 * <ol>
 *   <li>{@link ToolDefinition#getRequiredScopes()} —— 工具自身显式声明（最高优先级）</li>
 *   <li>{@code toolOverrides} —— 配置按工具名覆盖（无需改代码，运维可调）</li>
 *   <li>{@code categoryDefaults} —— 配置按分类兜底（finance → tools:finance:*）</li>
 *   <li>均未命中 —— 无 scope 约束 → 放行（向后兼容）</li>
 * </ol>
 *
 * <p>授权决策（{@link #authorize}）：</p>
 * <ul>
 *   <li>策略未启用（enabled=false）→ 恒定放行，行为与 V1.18 完全一致</li>
 *   <li>工具无 scope 约束 → 放行</li>
 *   <li>令牌 scope 命中任一所需模式（支持 * / ** 通配）→ 放行，并记录命中 scope</li>
 *   <li>否则 → 拒绝，返回 requiredScopes / tokenScopes 供 RFC 6750 insufficient_scope 响应</li>
 * </ul>
 */
public class ToolScopePolicy {

    private static final Logger log = LoggerFactory.getLogger(ToolScopePolicy.class);

    /** 开关：false 时恒放行（向后兼容，与未引入本特性行为一致） */
    private final boolean enabled;

    /** 工具名 → 所需 scope 模式（空格/逗号分隔，可含 * / **） */
    private final Map<String, String> toolOverrides;

    /** 工具分类 → 所需 scope 模式（空格/逗号分隔） */
    private final Map<String, String> categoryDefaults;

    public ToolScopePolicy(boolean enabled, Map<String, String> toolOverrides, Map<String, String> categoryDefaults) {
        this.enabled = enabled;
        this.toolOverrides = toolOverrides == null ? Map.of() : toolOverrides;
        this.categoryDefaults = categoryDefaults == null ? Map.of() : categoryDefaults;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 解析工具所需的 scope 模式集合（按 工具显式声明 &gt; 工具名覆盖 &gt; 分类兜底 优先级）。
     *
     * @return 空集合 = 无 scope 约束（放行）
     */
    public Set<String> resolveRequiredScopes(ToolDefinition def) {
        if (def == null) {
            return Set.of();
        }
        // 1) 工具显式声明
        if (def.getRequiredScopes() != null && !def.getRequiredScopes().isBlank()) {
            return splitScopePatterns(def.getRequiredScopes());
        }
        // 2) 配置按工具名覆盖
        String override = toolOverrides.get(def.getName());
        if (override != null && !override.isBlank()) {
            return splitScopePatterns(override);
        }
        // 3) 配置按分类兜底
        String category = def.getCategory();
        if (category != null) {
            String defaultPattern = categoryDefaults.get(category);
            if (defaultPattern != null && !defaultPattern.isBlank()) {
                return splitScopePatterns(defaultPattern);
            }
        }
        return Set.of();
    }

    /**
     * 授权决策：令牌 scope 是否允许调用该工具。
     *
     * @param tokenScopes 令牌携带的 scope 集合（可为空；null 表示调用方无令牌上下文）
     * @param def         目标工具定义
     */
    public ScopeDecision authorize(Set<String> tokenScopes, ToolDefinition def) {
        if (!enabled) {
            return ScopeDecision.allow(null, Set.of());
        }
        Set<String> required = resolveRequiredScopes(def);
        if (required.isEmpty()) {
            return ScopeDecision.allow(null, Set.of());
        }
        if (tokenScopes == null || tokenScopes.isEmpty()) {
            return ScopeDecision.deny(required, Set.of());
        }
        if (ScopeMatcher.matchesAny(tokenScopes, required)) {
            String matched = ScopeMatcher.findMatchedScope(tokenScopes, required);
            return ScopeDecision.allow(matched, required);
        }
        return ScopeDecision.deny(required, tokenScopes);
    }

    /** 把 "tools:finance:read tools:audit:view" / "a,b" 切分为模式集合 */
    private static Set<String> splitScopePatterns(String raw) {
        return Arrays.stream(raw.split("[\\s,]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 授权决策结果。
     *
     * @param allowed       是否放行
     * @param matchedScope  命中的令牌 scope（放行且命中时非空；无约束放行为 null）
     * @param requiredScopes 工具所需 scope 模式（拒绝时必填，供 insufficient_scope 响应）
     * @param tokenScopes    令牌实际 scope（拒绝时展示，便于排障）
     */
    public record ScopeDecision(boolean allowed, String matchedScope, Set<String> requiredScopes, Set<String> tokenScopes) {

        public static ScopeDecision allow(String matchedScope, Set<String> requiredScopes) {
            return new ScopeDecision(true, matchedScope, requiredScopes, Set.of());
        }

        public static ScopeDecision deny(Set<String> requiredScopes, Set<String> tokenScopes) {
            return new ScopeDecision(false, null, requiredScopes, tokenScopes);
        }
    }
}