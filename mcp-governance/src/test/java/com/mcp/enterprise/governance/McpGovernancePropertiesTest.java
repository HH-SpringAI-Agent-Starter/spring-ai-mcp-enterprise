package com.mcp.enterprise.governance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1.26 配置绑定测试：YAML 属性 → {@link McpGovernanceProperties}，含默认值语义。
 */
class McpGovernancePropertiesTest {

    private McpGovernanceProperties bind(Map<String, Object> props) {
        MapPropertySource source = new MapPropertySource("test", props);
        Binder binder = new Binder(ConfigurationPropertySources.from(source));
        return binder.bind("mcp.enterprise.governance", Bindable.of(McpGovernanceProperties.class))
                .orElseThrow(() -> new IllegalStateException("binding failed"));
    }

    @Test
    @DisplayName("默认值语义：enabled=true / enforce=false / T3,T4 需审批 / T2 默认等级")
    void bindsDefaults() {
        McpGovernanceProperties cfg = bind(Map.of("mcp.enterprise.governance.enabled", "true"));
        assertThat(cfg.isEnabled()).isTrue();
        assertThat(cfg.isEnforce()).isFalse();
        assertThat(cfg.getDefaultTier()).isEqualTo("T2");
        assertThat(cfg.getRequireApprovalTiers()).containsExactly("T3", "T4");
        assertThat(cfg.getDenyTiers()).isEmpty();
        assertThat(cfg.getApprovalHeader()).isEqualTo("X-MCP-Approval-Id");
        assertThat(cfg.getApproval().getTtlSeconds()).isEqualTo(900);
        assertThat(cfg.getApproval().isEnabled()).isTrue();
        assertThat(cfg.getRedaction().isEnabled()).isTrue();
        assertThat(cfg.getMessagePaths()).contains("/api/mcp/message", "/mcp");
        assertThat(cfg.getToolTiers()).isEmpty();
    }

    @Test
    @DisplayName("自定义绑定：enforce / deny-tiers / tool-tiers / 审批 TTL")
    void bindsCustom() {
        Map<String, Object> props = new HashMap<>();
        props.put("mcp.enterprise.governance.enforce", "true");
        props.put("mcp.enterprise.governance.deny-tiers[0]", "T4");
        props.put("mcp.enterprise.governance.tool-tiers.finance_transfer", "T4");
        props.put("mcp.enterprise.governance.tool-tiers.weather_query", "T1");
        props.put("mcp.enterprise.governance.approval.ttl-seconds", "60");
        props.put("mcp.enterprise.governance.approval.enabled", "false");
        props.put("mcp.enterprise.governance.redaction.enabled", "false");

        McpGovernanceProperties cfg = bind(props);
        assertThat(cfg.isEnforce()).isTrue();
        assertThat(cfg.getDenyTiers()).containsExactly("T4");
        assertThat(cfg.getToolTiers())
                .containsEntry("finance_transfer", "T4")
                .containsEntry("weather_query", "T1");
        assertThat(cfg.getApproval().getTtlSeconds()).isEqualTo(60);
        assertThat(cfg.getApproval().isEnabled()).isFalse();
        assertThat(cfg.getRedaction().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("非法枚举自动兜底：带后缀等级码可解析（宽容解析）")
    void tierParsingSmoke() {
        assertThat(RiskTier.fromCode("t3")).isEqualTo(RiskTier.T3);
        assertThat(RiskTier.fromCode("T3_WRITE")).isEqualTo(RiskTier.T3);
        assertThat(RiskTier.fromCode("3")).isEqualTo(RiskTier.T3);
        assertThat(RiskTier.fromCode("T9")).isNull();
        assertThat(RiskTier.fromCode(null)).isNull();
        assertThat(RiskTier.T4.isSideEffect()).isTrue();
        assertThat(RiskTier.T1.isSideEffect()).isFalse();
    }
}