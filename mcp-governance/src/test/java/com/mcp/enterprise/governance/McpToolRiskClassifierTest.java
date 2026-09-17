package com.mcp.enterprise.governance;

import com.mcp.enterprise.core.model.ToolDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1.26 工具风险分类器测试：显式覆盖 > 分类语义 > 关键词启发式 > 默认等级。
 */
class McpToolRiskClassifierTest {

    private McpToolRiskClassifier classifier(McpGovernanceProperties props) {
        return new McpToolRiskClassifier(props);
    }

    @Test
    @DisplayName("显式 tool-tiers 覆盖优先")
    void explicitOverrideWins() {
        McpGovernanceProperties props = new McpGovernanceProperties();
        props.getToolTiers().put("finance_transfer", "T4");
        McpToolRiskClassifier c = classifier(props);

        assertThat(c.classify("finance_transfer")).isEqualTo(RiskTier.T4);
        assertThat(c.getExplicitToolNames()).contains("finance_transfer");
    }

    @Test
    @DisplayName("关键词启发式：critical→T4 / write→T3 / sensitive→T2 / read→T1 / 未知→默认 T2")
    void keywordHeuristics() {
        McpToolRiskClassifier c = classifier(new McpGovernanceProperties());

        assertThat(c.classify("db_delete_all")).isEqualTo(RiskTier.T4);   // delete = critical
        assertThat(c.classify("user_create")).isEqualTo(RiskTier.T3);     // create = write
        assertThat(c.classify("customer_query")).isEqualTo(RiskTier.T2);  // customer = sensitive
        assertThat(c.classify("weather_get")).isEqualTo(RiskTier.T1);     // get+weather = read
        assertThat(c.classify("weirdToolXYZ")).isEqualTo(RiskTier.T2);    // 默认等级
    }

    @Test
    @DisplayName("ToolDefinition 分类语义兜底：system→T3 / finance→T2 / search→T1")
    void categorySemantics() {
        McpToolRiskClassifier c = classifier(new McpGovernanceProperties());

        assertThat(c.classify("sys_audit_tool", def("system"))).isEqualTo(RiskTier.T3);
        assertThat(c.classify("fin_ratio", def("finance"))).isEqualTo(RiskTier.T2);
        assertThat(c.classify("kw_search", def("search"))).isEqualTo(RiskTier.T1);
    }

    @Test
    @DisplayName("自定义默认等级与关键词列表生效")
    void customDefaultsAndKeywords() {
        McpGovernanceProperties props = new McpGovernanceProperties();
        props.setDefaultTier("T1");
        props.setCriticalKeywords(java.util.List.of("nuke"));
        McpToolRiskClassifier c = classifier(props);

        assertThat(c.classify("nuke_cleanup")).isEqualTo(RiskTier.T4);   // 自定义 critical
        assertThat(c.classify("whatever")).isEqualTo(RiskTier.T1);       // 自定义默认
        assertThat(c.getDefaultTier()).isEqualTo(RiskTier.T1);
    }

    private static ToolDefinition def(String category) {
        return new ToolDefinition("t", "t", "desc", category, "1.0", "test",
                true, null, 30000, 10, null, null);
    }
}