package com.mcp.enterprise.governance;

/**
 * V1.26 工具风险分级（对齐 OWASP MCP Governance &amp; Risk Project 的五级模型）。
 *
 * <p>等级由「数据敏感度 + 动作副作用 + 影响半径」决定，取服务器上最高风险的
 * 那个工具作为该工具的等级：</p>
 *
 * <table border="1">
 *   <tr><th>等级</th><th>含义</th><th>典型示例</th></tr>
 *   <tr><td>T0</td><td>公开数据、只读</td><td>天气 / 汇率查询</td></tr>
 *   <tr><td>T1</td><td>内部数据、非敏感读</td><td>搜索 / 目录列举</td></tr>
 *   <tr><td>T2</td><td>敏感数据读</td><td>CRM / 用户 / 财务指标查询</td></tr>
 *   <tr><td>T3</td><td>写操作 / 有副作用</td><td>创建工单、发邮件、退款</td></tr>
 *   <tr><td>T4</td><td>特权 / 关键操作</td><td>删除、清库、权限变更、部署</td></tr>
 * </table>
 */
public enum RiskTier {

    T0("T0", 0, "公开数据只读"),
    T1("T1", 1, "内部数据非敏感读"),
    T2("T2", 2, "敏感数据读"),
    T3("T3", 3, "写操作/有副作用"),
    T4("T4", 4, "特权/关键操作");

    private final String code;
    private final int rank;
    private final String label;

    RiskTier(String code, int rank, String label) {
        this.code = code;
        this.rank = rank;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public int getRank() {
        return rank;
    }

    public String getLabel() {
        return label;
    }

    /** 是否属于「有副作用」等级（T3 及以上），用于默认审批策略。 */
    public boolean isSideEffect() {
        return rank >= T3.rank;
    }

    /**
     * 宽容解析：接受 {@code T0}..{@code T4}、小写、数字形式（{@code 3}）、
     * 以及带后缀的枚举名（如 {@code T3_WRITE}）。解析失败返回 {@code null}
     * （由调用方决定回退策略，避免静默降级为低风险）。
     */
    public static RiskTier fromCode(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toUpperCase();
        if (v.isEmpty()) {
            return null;
        }
        // 带后缀枚举名（T3_WRITE / T3_read）：取 T 前缀段再解析
        if (v.length() > 2 && v.startsWith("T") && Character.isDigit(v.charAt(1))) {
            v = v.substring(0, 2);
        }
        for (RiskTier t : values()) {
            if (t.code.equals(v) || t.name().equals(v)) {
                return t;
            }
        }
        // 纯数字形式 "3"
        if (v.length() == 1 && Character.isDigit(v.charAt(0))) {
            return fromCode("T" + v.charAt(0));
        }
        return null;
    }
}
