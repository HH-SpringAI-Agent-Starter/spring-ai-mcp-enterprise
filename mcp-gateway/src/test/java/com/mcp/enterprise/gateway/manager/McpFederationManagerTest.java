package com.mcp.enterprise.gateway.manager;

import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.tool.McpToolManager;
import com.mcp.enterprise.gateway.McpGatewayProperties;
import com.mcp.enterprise.gateway.RemoteToolDescriptor;
import com.mcp.enterprise.gateway.UpstreamMcpClient;
import com.mcp.enterprise.gateway.client.McpUpstreamClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1.25 联邦管理器测试：注册/刷新/失败保留/禁用移除/下线路由，全链路用真实
 * {@code McpToolManager} + {@code ToolRegistry} 验证治理继承。
 */
class McpFederationManagerTest {

    private McpToolManager toolManager;
    private FakeFactory factory;

    @BeforeEach
    void setUp() {
        toolManager = new McpToolManager(new ToolRegistry());
        factory = new FakeFactory();
    }

    // ===== 测试 =====

    @Test
    @DisplayName("同步上游 → 联邦工具注册进 ToolManager（治理自动继承）")
    void sync_registersFederatedTools() {
        McpFederationManager manager = buildManager(List.of(
                upstream("hr", true, "hr", List.of(
                        tool("get_employee"), tool("list_team")))));

        Map<String, Object> result = manager.sync("hr").block();

        assertThat(result.get("success")).isEqualTo(Boolean.TRUE);
        assertThat(toolManager.isRegistered("hr__get_employee")).isTrue();
        assertThat(toolManager.isRegistered("hr__list_team")).isTrue();
        assertThat(toolManager.count()).isEqualTo(2);
        assertThat(toolManager.getExecutor("hr__get_employee").getDefinition().getCategory())
                .isEqualTo("federated");
        assertThat(toolManager.getExecutor("hr__get_employee").getDefinition().getMetadata())
                .containsEntry("upstream", "hr");

        Map<String, Object> status = statusOf(manager, "hr");
        assertThat(status.get("healthy")).isEqualTo(Boolean.TRUE);
        assertThat(status.get("toolCount")).isEqualTo(2);
    }

    @Test
    @DisplayName("重新同步 → 旧工具摘除、新工具挂载（无残留）")
    void sync_resyncReplacesTools() {
        McpFederationManager manager = buildManager(List.of(
                upstream("hr", true, "hr", List.of(tool("a"), tool("b")))));
        manager.sync("hr").block();
        assertThat(toolManager.count()).isEqualTo(2);

        // 上游版本更新：b 下架、c 上架
        factory.clients.get("hr").tools = List.of(tool("a"), tool("c"));
        manager.sync("hr").block();

        assertThat(toolManager.isRegistered("hr__a")).isTrue();
        assertThat(toolManager.isRegistered("hr__b")).isFalse();
        assertThat(toolManager.isRegistered("hr__c")).isTrue();
        assertThat(toolManager.count()).isEqualTo(2);
        assertThat(statusOf(manager, "hr").get("toolCount")).isEqualTo(2);
    }

    @Test
    @DisplayName("同步失败 → 保留已注册工具、标记 unhealthy、result success=false")
    void sync_failureKeepsTools() {
        McpFederationManager manager = buildManager(List.of(
                upstream("hr", true, "hr", List.of(tool("a")))));
        manager.sync("hr").block();
        assertThat(toolManager.isRegistered("hr__a")).isTrue();

        factory.clients.get("hr").fail = true;
        Map<String, Object> result = manager.sync("hr").block();

        assertThat(result.get("success")).isEqualTo(Boolean.FALSE);
        assertThat(toolManager.isRegistered("hr__a")).isTrue(); // 可用性优先
        Map<String, Object> status = statusOf(manager, "hr");
        assertThat(status.get("healthy")).isEqualTo(Boolean.FALSE);
        assertThat(String.valueOf(status.get("error"))).contains("boom");
    }

    @Test
    @DisplayName("禁用上游 → 其联邦工具被移除且保持未注册")
    void sync_disabledUpstreamRemovesTools() {
        McpFederationManager manager = buildManager(List.of(
                upstream("hr", true, "hr", List.of(tool("a"))),
                upstream("fin", true, "fin", List.of(tool("x")))));
        manager.syncAll().block();
        assertThat(toolManager.count()).isEqualTo(2);

        factory.clients.get("hr").config.setEnabled(false);
        Map<String, Object> result = manager.sync("hr").block();

        assertThat(result.get("disabled")).isEqualTo(Boolean.TRUE);
        assertThat(toolManager.isRegistered("hr__a")).isFalse();
        assertThat(toolManager.isRegistered("fin__x")).isTrue();
        assertThat(statusOf(manager, "hr").get("toolCount")).isEqualTo(0);
    }

    @Test
    @DisplayName("removeTools 下线 → 联邦工具全部注销")
    void removeTools_unregisters() {
        McpFederationManager manager = buildManager(List.of(
                upstream("hr", true, "hr", List.of(tool("a"), tool("b")))));
        manager.sync("hr").block();
        assertThat(toolManager.count()).isEqualTo(2);

        Map<String, Object> result = manager.removeTools("hr").block();
        assertThat(result.get("removed")).isEqualTo(2);
        assertThat(toolManager.isRegistered("hr__a")).isFalse();
        assertThat(toolManager.isRegistered("hr__b")).isFalse();
        assertThat(toolManager.count()).isZero();
    }

    @Test
    @DisplayName("未知上游 → success=false")
    void sync_unknownUpstream() {
        McpFederationManager manager = buildManager(List.of());
        Map<String, Object> result = manager.sync("ghost").block();
        assertThat(result.get("success")).isEqualTo(Boolean.FALSE);
        assertThat(String.valueOf(result.get("error"))).contains("unknown upstream");
    }

    @Test
    @DisplayName("聚合健康：healthy/total/unhealthy/federatedTools")
    void health_aggregates() {
        McpFederationManager manager = buildManager(List.of(
                upstream("hr", true, "hr", List.of(tool("a"))),
                upstream("fin", true, "fin", List.of(tool("x")))));
        manager.syncAll().block();

        Map<String, Object> health = manager.health().block();
        assertThat(health.get("total")).isEqualTo(2);
        assertThat(health.get("healthy")).isEqualTo(2);
        assertThat(health.get("unhealthy")).isEqualTo(0);
        assertThat(health.get("federatedTools")).isEqualTo(2);
    }

    @Test
    @DisplayName("同步配置非法上游（缺 name/url）被跳过")
    void constructor_skipsInvalidUpstreams() {
        McpGatewayProperties props = new McpGatewayProperties();
        McpGatewayProperties.Upstream bad = new McpGatewayProperties.Upstream();
        bad.setName("no-url");
        props.getUpstreams().add(bad);
        McpFederationManager manager = new McpFederationManager(props, factory, toolManager);
        assertThat(manager.listUpstreams()).isEmpty();
    }

    // ===== 辅助 =====

    private McpFederationManager buildManager(List<McpGatewayProperties.Upstream> upstreams) {
        McpGatewayProperties props = new McpGatewayProperties();
        props.getUpstreams().addAll(upstreams);
        return new McpFederationManager(props, factory, toolManager);
    }

    private McpGatewayProperties.Upstream upstream(String name, boolean enabled, String prefix,
                                                    List<RemoteToolDescriptor> tools) {
        McpGatewayProperties.Upstream u = new McpGatewayProperties.Upstream();
        u.setName(name);
        u.setUrl("http://" + name + ":8080/mcp");
        u.setPrefix(prefix);
        u.setEnabled(enabled);
        factory.provisionedTools.put(name, tools);
        return u;
    }

    private RemoteToolDescriptor tool(String name) {
        return new RemoteToolDescriptor(name, "工具 " + name, Map.of("type", "object"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> statusOf(McpFederationManager manager, String name) {
        return manager.listUpstreams().stream()
                .filter(m -> name.equals(m.get("name")))
                .findFirst()
                .orElseThrow();
    }

    /** 可注入的假客户端工厂 */
    private static class FakeFactory implements McpUpstreamClientFactory {
        final Map<String, FakeClient> clients = new ConcurrentHashMap<>();
        final Map<String, List<RemoteToolDescriptor>> provisionedTools = new ConcurrentHashMap<>();

        @Override
        public UpstreamMcpClient create(McpGatewayProperties.Upstream config) {
            FakeClient client = new FakeClient(config);
            client.tools = provisionedTools.get(config.getName()) == null
                    ? new ArrayList<>() : new ArrayList<>(provisionedTools.get(config.getName()));
            clients.put(config.getName(), client);
            return client;
        }
    }

    private static class FakeClient implements UpstreamMcpClient {
        final McpGatewayProperties.Upstream config;
        List<RemoteToolDescriptor> tools = new ArrayList<>();
        boolean fail = false;
        boolean enabled = true;

        FakeClient(McpGatewayProperties.Upstream config) {
            this.config = config;
        }

        @Override
        public String name() {
            return config.getName();
        }

        @Override
        public Mono<Boolean> ping() {
            return Mono.just(!fail);
        }

        @Override
        public Mono<List<RemoteToolDescriptor>> listTools() {
            if (fail) {
                return Mono.error(new RuntimeException("boom"));
            }
            return Mono.just(tools);
        }

        @Override
        public Mono<Map<String, Object>> callTool(String toolName, Map<String, Object> arguments) {
            return Mono.just(Map.of("success", true, "result", Map.of("tool", toolName)));
        }
    }
}