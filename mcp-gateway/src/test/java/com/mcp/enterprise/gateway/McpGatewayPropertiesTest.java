package com.mcp.enterprise.gateway;

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
 * V1.25 配置绑定测试：YAML 属性 → {@link McpGatewayProperties}，含默认值语义。
 */
class McpGatewayPropertiesTest {

    private McpGatewayProperties bind(Map<String, Object> props) {
        MapPropertySource source = new MapPropertySource("test", props);
        Binder binder = new Binder(ConfigurationPropertySources.from(source));
        return binder.bind("mcp.enterprise.gateway", Bindable.of(McpGatewayProperties.class))
                .orElseThrow(() -> new IllegalStateException("binding failed"));
    }

    @Test
    @DisplayName("多上游绑定：name/url/api-key/prefix/超时")
    void bindsMultipleUpstreams() {
        Map<String, Object> props = new HashMap<>();
        props.put("mcp.enterprise.gateway.sync-on-startup", "false");
        props.put("mcp.enterprise.gateway.upstreams[0].name", "hr");
        props.put("mcp.enterprise.gateway.upstreams[0].url", "http://hr:8080/mcp");
        props.put("mcp.enterprise.gateway.upstreams[0].api-key", "k1");
        props.put("mcp.enterprise.gateway.upstreams[0].prefix", "hr-prod");
        props.put("mcp.enterprise.gateway.upstreams[0].request-timeout-ms", "15000");
        props.put("mcp.enterprise.gateway.upstreams[1].name", "fin");
        props.put("mcp.enterprise.gateway.upstreams[1].url", "http://fin:8080/mcp");

        McpGatewayProperties cfg = bind(props);

        assertThat(cfg.isSyncOnStartup()).isFalse();
        assertThat(cfg.isEnabled()).isTrue(); // 默认
        assertThat(cfg.getUpstreams()).hasSize(2);

        McpGatewayProperties.Upstream hr = cfg.getUpstreams().get(0);
        assertThat(hr.getName()).isEqualTo("hr");
        assertThat(hr.getUrl()).isEqualTo("http://hr:8080/mcp");
        assertThat(hr.getApiKey()).isEqualTo("k1");
        assertThat(hr.getPrefix()).isEqualTo("hr-prod");
        assertThat(hr.getRequestTimeoutMs()).isEqualTo(15000);
        assertThat(hr.isEnabled()).isTrue();

        McpGatewayProperties.Upstream fin = cfg.getUpstreams().get(1);
        assertThat(fin.getName()).isEqualTo("fin");
        assertThat(fin.getTransport()).isEqualTo(McpGatewayProperties.Transport.STREAMABLE_HTTP);
        assertThat(fin.getConnectTimeoutMs()).isEqualTo(3000); // 默认
        assertThat(fin.getRateLimitPerSecond()).isEqualTo(10); // 默认
    }

    @Test
    @DisplayName("空配置 → 上游为空、默认开启")
    void emptyConfigDefaults() {
        // Binder 需要至少一个属性键才能触发绑定；用仅含默认语义的键验证缺省值
        McpGatewayProperties cfg = bind(Map.of("mcp.enterprise.gateway.sync-on-startup", "true"));
        assertThat(cfg.isEnabled()).isTrue();
        assertThat(cfg.isSyncOnStartup()).isTrue();
        assertThat(cfg.getUpstreams()).isEmpty();
    }

    @Test
    @DisplayName("前缀空时状态层回退为上游 name")
    void statusPrefixFallsBackToName() {
        McpGatewayProperties.Upstream u = new McpGatewayProperties.Upstream();
        u.setName("hr");
        u.setUrl("http://hr:8080/mcp");
        McpUpstreamStatus status = new McpUpstreamStatus(u);
        assertThat(status.getPrefix()).isEqualTo("hr");
        assertThat(status.getTransport()).isEqualTo("streamable-http");
        assertThat(status.isEnabled()).isTrue();
    }
}