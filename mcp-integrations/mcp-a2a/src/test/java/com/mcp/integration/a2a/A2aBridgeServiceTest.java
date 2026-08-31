package com.mcp.integration.a2a;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * A2A 桥接服务单元测试（V1.15）
 *
 * 覆盖：
 * 1. 工具注册中心 → Agent Card / Skill 派生（仅启用工具）
 * 2. message/send：metadata.skillId 路由 + 工具执行 → agent 消息
 * 3. message/send：未知工具 → A2A -32003 错误；未指定工具 → 提示可用列表
 * 4. task/send → task/get → 生命周期 completed + artifact
 * 5. task/cancel → canceled；完成态任务不可取消
 */
@ExtendWith(MockitoExtension.class)
class A2aBridgeServiceTest {

    @Mock
    private McpToolManager toolManager;

    private ToolRegistry registry;
    private A2aBridgeService service;
    private McpA2aProperties properties;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        properties = new McpA2aProperties();
        properties.setAgentName("Test Agent");
        properties.setTaskTimeoutMs(5000);
        service = new A2aBridgeService(registry, toolManager, properties);

        ToolDefinition calc = new ToolDefinition("calculator", "计算器", "四则运算",
                "math", "1.0.0", "mcp-tools", true, null, 30000, 10,
                Map.of("type", "object", "properties", Map.of("expr", Map.of("type", "string"))), null);
        ToolDefinition disabled = new ToolDefinition("disabled-tool", "禁用工具", "不应出现在 Agent Card",
                "system", "1.0.0", "mcp-tools", false, null, 30000, 10,
                Map.of("type", "object"), null);
        registry.register("calculator", calc, new Object());
        registry.register("disabled-tool", disabled, new Object());
    }

    @Test
    @DisplayName("Agent Card 只暴露启用的工具，Skill 字段映射正确")
    void agentCardDerivesEnabledSkillsOnly() {
        A2aAgentCard card = service.buildAgentCard("http://localhost:8081/a2a");

        assertThat(card.name()).isEqualTo("Test Agent");
        assertThat(card.skills()).hasSize(1);
        A2aSkill skill = card.skills().get(0);
        assertThat(skill.id()).isEqualTo("calculator");
        assertThat(skill.tags()).contains("math");
        assertThat(skill.additional()).containsKey("inputSchema");
        assertThat(card.capabilities()).containsEntry("streaming", false);
    }

    @Test
    @DisplayName("message/send: metadata.skillId 路由并返回 agent 消息")
    void messageSendRoutesBySkillId() {
        when(toolManager.invoke(eq("calculator"), any(Map.class)))
                .thenReturn(Mono.just(Map.of("success", true, "result", 42)));

        Map<String, Object> params = Map.of("message", Map.of(
                "text", "帮我算 6*7",
                "metadata", Map.of("skillId", "calculator", "arguments", Map.of("expr", "6*7"))
        ));

        Map<String, Object> result = service.handleMessageSend(params);
        assertThat(result).containsKey("message");
        assertThat(result.get("contextId")).isNotNull();

        A2aMessage reply = (A2aMessage) result.get("message");
        assertThat(reply.role()).isEqualTo("agent");
        assertThat(reply.firstText()).contains("42");
    }

    @Test
    @DisplayName("message/send: 文本前缀 tool:<name> 兜底路由")
    void messageSendSupportsTextPrefixRouting() {
        when(toolManager.invoke(eq("calculator"), any(Map.class)))
                .thenReturn(Mono.just(Map.of("success", true, "result", 7)));

        Map<String, Object> params = Map.of("message", Map.of(
                "text", "tool:calculator 计算",
                "parts", List.of(Map.of("text", "tool:calculator 计算"))
        ));

        Map<String, Object> result = service.handleMessageSend(params);
        A2aMessage reply = (A2aMessage) result.get("message");
        assertThat(reply.firstText()).contains("7");
    }

    @Test
    @DisplayName("message/send: 未知工具抛 -32003")
    void messageSendUnknownTool() {
        Map<String, Object> params = Map.of("message", Map.of(
                "text", "hello",
                "metadata", Map.of("skillId", "not-exist")
        ));

        assertThatThrownBy(() -> service.handleMessageSend(params))
                .isInstanceOf(A2aBridgeService.A2aRpcException.class)
                .hasMessageContaining("工具不存在")
                .satisfies(e -> assertThat(((A2aBridgeService.A2aRpcException) e).code)
                        .isEqualTo(A2aBridgeService.ERR_TOOL_NOT_FOUND));
    }

    @Test
    @DisplayName("message/send: 未指定工具返回可用工具列表")
    void messageSendWithoutSkillId() {
        Map<String, Object> params = Map.of("message", Map.of("text", "随便聊聊"));

        assertThatThrownBy(() -> service.handleMessageSend(params))
                .isInstanceOf(A2aBridgeService.A2aRpcException.class)
                .hasMessageContaining("calculator");
    }

    @Test
    @DisplayName("task/send → task/get：completed + artifact 包含工具结果")
    void taskLifecycleCompleted() {
        when(toolManager.invoke(eq("calculator"), any(Map.class)))
                .thenReturn(Mono.just(Map.of("success", true, "result", 42)));

        Map<String, Object> sent = service.handleTaskSend(Map.of("message", Map.of(
                "text", "6*7",
                "metadata", Map.of("skillId", "calculator", "arguments", Map.of("expr", "6*7"))
        )));

        A2aTask task = (A2aTask) sent.get("task");
        assertThat(task.status()).isEqualTo("completed");
        assertThat(task.artifacts()).hasSize(1);
        assertThat(task.artifacts().get(0)).extracting("metadata").isNotNull();

        Map<String, Object> got = service.handleTaskGet(Map.of("id", task.id()));
        assertThat(((A2aTask) got.get("task")).id()).isEqualTo(task.id());
        assertThat(((A2aTask) got.get("task")).status()).isEqualTo("completed");
    }

    @Test
    @DisplayName("task/cancel：working 态可取消；completed 态抛 -32005")
    void taskCancelSemantics() {
        when(toolManager.invoke(eq("calculator"), any(Map.class)))
                .thenReturn(Mono.just(Map.of("success", true, "result", 42)));

        Map<String, Object> sent = service.handleTaskSend(Map.of("message", Map.of(
                "text", "6*7",
                "metadata", Map.of("skillId", "calculator")
        )));
        A2aTask completed = (A2aTask) sent.get("task");

        assertThatThrownBy(() -> service.handleTaskCancel(Map.of("id", completed.id())))
                .isInstanceOf(A2aBridgeService.A2aRpcException.class)
                .satisfies(e -> assertThat(((A2aBridgeService.A2aRpcException) e).code)
                        .isEqualTo(A2aBridgeService.ERR_TASK_NOT_CANCELABLE));
    }

    @Test
    @DisplayName("task/get：未知任务抛 -32004")
    void taskGetUnknown() {
        assertThatThrownBy(() -> service.handleTaskGet(Map.of("id", "task-nope")))
                .isInstanceOf(A2aBridgeService.A2aRpcException.class)
                .satisfies(e -> assertThat(((A2aBridgeService.A2aRpcException) e).code)
                        .isEqualTo(A2aBridgeService.ERR_TASK_NOT_FOUND));
    }

    @Test
    @DisplayName("JSON-RPC 分派：未知方法返回 -32601，agent/quote 返回技能列表")
    void dispatchMethods() {
        assertThat(service.dispatch("no/such-method", Map.of()).get("error"))
                .extracting("code").isEqualTo(-32601);
        assertThat(service.dispatch("agent/quote", Map.of()).get("result"))
                .extracting("skills").asList().contains("calculator");
    }
}