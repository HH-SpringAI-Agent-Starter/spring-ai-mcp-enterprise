package com.mcp.enterprise.core.security;

import java.util.Set;

/**
 * OAuth2 scope 通配符匹配器（V1.19 工具级 Scope 权限映射）。
 *
 * <p>企业 scope 常按资源维度分层，如 {@code tools:finance:read} / {@code tools:database:write}。
 * 令牌签发时客户端请求精确 scope，工具声明所需 scope 时允许使用通配符模式：</p>
 *
 * <ul>
 *   <li>{@code tools:finance:read} —— 精确匹配（RFC 6749 scope 语法：资源:动作）</li>
 *   <li>{@code tools:finance:*}   —— 单段通配：匹配 tools:finance: 下的任意动作（read/write/admin…）</li>
 *   <li>{@code tools:**}          —— 多段通配：匹配 tools: 下任意层级（含嵌套资源）</li>
 *   <li>{@code *}                 —— 全部匹配（等价于不过滤）</li>
 * </ul>
 *
 * <p>匹配语义：{@code tokenScope} 命中任意一个 {@code requiredPattern} 即视为满足。
 * 段落按 {@code :} 切分；{@code *} 仅匹配当前段，{@code **} 匹配当前段及之后所有段。</p>
 */
public final class ScopeMatcher {

    private ScopeMatcher() {
    }

    /**
     * 判断单个令牌 scope 是否命中所需 scope 模式。
     *
     * @param tokenScope      令牌携带的 scope（如 tools:finance:read）
     * @param requiredPattern 工具声明的 scope 模式（支持 * / ** 通配）
     * @return 命中返回 true
     */
    public static boolean matches(String tokenScope, String requiredPattern) {
        if (tokenScope == null || requiredPattern == null) {
            return false;
        }
        String token = tokenScope.trim();
        String pattern = requiredPattern.trim();
        if (token.isEmpty() || pattern.isEmpty()) {
            return false;
        }
        if ("*".equals(pattern)) {
            return true; // 全匹配
        }
        String[] tokenParts = token.split(":");
        String[] patternParts = pattern.split(":");
        int ti = 0;
        for (int pi = 0; pi < patternParts.length; pi++) {
            String p = patternParts[pi];
            if ("**".equals(p)) {
                return true; // 多段通配：剩余任意段均满足
            }
            if (ti >= tokenParts.length) {
                return false; // token 段用尽但模式还有普通段
            }
            if (!"*".equals(p) && !p.equals(tokenParts[ti])) {
                return false;
            }
            ti++;
        }
        // 模式段用尽：token 必须恰好也用尽（精确匹配不允许 token 多出段）
        return ti == tokenParts.length;
    }

    /**
     * 判断令牌 scope 集合是否满足任一所需模式。
     *
     * @param tokenScopes      令牌 scope 集合（可为空）
     * @param requiredPatterns 工具所需 scope 模式集合（可为空）
     * @return 任一命中返回 true；两边皆空返回 true（无约束视为放行）
     */
    public static boolean matchesAny(Set<String> tokenScopes, Set<String> requiredPatterns) {
        if (requiredPatterns == null || requiredPatterns.isEmpty()) {
            return true; // 工具未声明 scope 约束 → 放行（向后兼容）
        }
        if (tokenScopes == null || tokenScopes.isEmpty()) {
            return false; // 有约束但令牌无 scope → 拒绝
        }
        for (String pattern : requiredPatterns) {
            for (String tokenScope : tokenScopes) {
                if (matches(tokenScope, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 返回令牌中实际命中的第一个 scope（供审计日志 / 错误响应展示）。
     *
     * @return 命中 scope 原文；未命中返回 null
     */
    public static String findMatchedScope(Set<String> tokenScopes, Set<String> requiredPatterns) {
        if (requiredPatterns == null || tokenScopes == null) {
            return null;
        }
        for (String pattern : requiredPatterns) {
            for (String tokenScope : tokenScopes) {
                if (matches(tokenScope, pattern)) {
                    return tokenScope;
                }
            }
        }
        return null;
    }
}