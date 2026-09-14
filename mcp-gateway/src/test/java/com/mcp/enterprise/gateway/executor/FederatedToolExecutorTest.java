package com.mcp.enterprise.gateway.executor;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.gateway.McpGatewayProperties;
import com.mcp.enterprise.gateway.RemoteToolDescriptor;
import com.mcp.enterprise.gateway.UpstreamMcpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V1.25 联邦工具执行器测试：命名空间前缀、定义元数据、执行委托与结果映射、健康检查。
 */
class FederatedToolExecutorTest {

    // ===== 工厂 =====

    private McpGatewayProperties.Upstream config(String prefix) {
        McpGatewayProperties.Upstream u = new McpGatewayProperties.Upstream();
        u.setName("hr");
        u.setUrl("http://hr:8080/mcp");
        u.setPrefix(prefix);
        u.setRequestTimeoutMs(7777);
        u.setRateLimitPerSecond(5);
        return u;
    }

    private RemoteToolDescriptor descriptor() {
        return new RemoteToolDescriptor("get_employee", "查询员工", Map.of(
                "type", "object",
                "properties", Map.of("empId", Map.of("type", "integer"))));
    }

    private UpstreamMcpClient fakeClient() {
        UpstreamMcpClient client = mock(UpstreamMcpClient.class);
        when(client.name()).thenReturn("hr");
        when(client.ping()).thenReturn(Mono.just(true));
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("success", true);
        ok.put("result", Map.of("empId", 42, "name", "张三"));
        when(client.callTool("get_employee", Map.of("empId", 42))).thenReturn(Mono.just(ok));
        return client;
    }

    // ===== 测试 =====

    @Test
    @DisplayName("本地工具名 = prefix__remoteName，定义携带联邦元数据")
    void definition_prefixedAndMetadata() {
        FederatedToolExecutor executor = new FederatedToolExecutor(fakeClient(), config("hr"), descriptor());
        ToolDefinition def = executor.getDefinition();
        assertThat(def.getName()).isEqualTo("hr__get_employee");
        assertThat(def.getCategory()).isEqualTo("federated");
        assertThat(def.getModule()).isEqualTo("upstream:hr");
        assertThat(def.getTimeoutMs()).isEqualTo(7777);
        assertThat(def.getRateLimitPerSecond()).isEqualTo(5);
        assertThat(def.getMetadata()).containsEntry("federated", true);
        assertThat(def.getMetadata()).containsEntry("remoteName", "get_employee");
        assertThat(def.getInputSchema()).containsKey("properties");
    }

    @Test
    @DisplayName("prefix 为空时退化为上游 name")
    void definition_prefixDefaultsToUpstreamName() {
        FederatedToolExecutor executor = new FederatedToolExecutor(fakeClient(), config(""), descriptor());
        assertThat(executor.getLocalName()).isEqualTo("hr__get_employee");
        assertThat(executor.getRemoteName()).isEqualTo("get_employee");
    }

    @Test
    @DisplayName("execute 委托上游并附加 tool/upstream 上下文")
    void execute_delegatesAndEnriches() {
        FederatedToolExecutor executor = new FederatedToolExecutor(fakeClient(), config("hr"), descriptor());
        Map<String, Object> result = executor.execute(Map.of("empId", 42)).block();
        assertThat(result.get("success")).isEqualTo(Boolean.TRUE);
        assertThat(result.get("tool")).isEqualTo("hr__get_employee");
        assertThat(result.get("upstream")).isEqualTo("hr");
        assertThat(result.get("result")).isEqualTo(Map.of("empId", 42, "name", "张三"));
    }

    @Test
    @DisplayName("execute 上游异常 → success=false 且带错误信息")
    void execute_upstreamError() {
        UpstreamMcpClient client = mock(UpstreamMcpClient.class);
        when(client.callTool("get_employee", Map.of())).thenReturn(Mono.error(new RuntimeException("upstream down")));
        FederatedToolExecutor executor = new FederatedToolExecutor(client, config("hr"), descriptor());
        Map<String, Object> result = executor.execute(Map.of()).block();
        assertThat(result.get("success")).isEqualTo(Boolean.FALSE);
        assertThat(String.valueOf(result.get("error"))).contains("upstream down");
        assertThat(result.get("upstream")).isEqualTo("hr");
    }

    @Test
    @DisplayName("healthCheck 委托上游 ping")
    void healthCheck_delegates() {
        UpstreamMcpClient client = fakeClient();
        FederatedToolExecutor executor = new FederatedToolExecutor(client, config("hr"), descriptor());
        assertThat(executor.healthCheck().block()).isTrue();
        verify(client).ping();
    }
}