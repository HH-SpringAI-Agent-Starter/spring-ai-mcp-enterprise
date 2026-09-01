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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * A2A 桥接服务单元测试（V1.15 → V1.16）
 *
 * 覆盖：
 * 1. 工具注册中心 → Agent Card / Skill 派生（仅启用工具）
 * 2. message/send：metadata.skillId 路由 + 工具执行 → agent 消息
 * 3. message/send：未知工具 → A2A -32003 错误；未指定工具 → 提示可用列表
 * 4. task/send → task/get → 生命周期 completed + artifact
 * 5. task/cancel → canceled；完成态任务不可取消
 * 6. V1.16: Agent Card securitySchemes 声明（api-key / oauth2 / none）
 * 7. V1.16: SSE 流式 message/stream（事件序列 + 异步完成）
 * 8. V1.16: task/resubscribe（已完成任务历史重放 + 未知任务 TaskNotFoundEvent）
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
        assertThat(card.capabilities()).containsEntry("streaming", true); // V1.16: 默认开启 SSE 流式
        assertThat(card.securitySchemes()).isEmpty();                      // 未配置 → 空声明
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
        // V1.16: 流式方法走 JSON 通道应提示需 SSE
        assertThat(service.dispatch("message/stream", Map.of()).get("error"))
                .extracting("code").isEqualTo(-32601);
        assertThat(service.dispatch("task/resubscribe", Map.of()).get("error"))
                .extracting("code").isEqualTo(-32601);
    }

    // ==================== V1.16: securitySchemes ====================

    @Test
    @DisplayName("V1.16: securitySchemes - api-key 配置时声明 apiKey header 方案")
    void securitySchemesApiKey() {
        properties.setApiKey("secret-key");
        A2aAgentCard card = service.buildAgentCard("http://localhost:8081/a2a");
        assertThat(card.securitySchemes()).hasSize(1);
        assertThat(card.securitySchemes().get(0))
                .containsEntry("type", "apiKey")
                .containsEntry("in", "header")
                .containsEntry("name", "X-A2A-Key");
    }

    @Test
    @DisplayName("V1.16: securitySchemes - oauth2 配置时声明 clientCredentials 流")
    void securitySchemesOauth2() {
        properties.setSecurityScheme("oauth2");
        properties.setOauth2TokenUrl("https://idp.example.com/oauth2/token");
        A2aAgentCard card = service.buildAgentCard("http://localhost:8081/a2a");
        assertThat(card.securitySchemes()).hasSize(1);
        assertThat(card.securitySchemes().get(0)).containsEntry("type", "oauth2");
        @SuppressWarnings("unchecked")
        Map<String, Object> flows = (Map<String, Object>) card.securitySchemes().get(0).get("flows");
        assertThat(flows).containsKey("clientCredentials");
    }

    @Test
    @DisplayName("V1.16: securitySchemes - 显式 none 时即使有 api-key 也不声明")
    void securitySchemesExplicitNone() {
        properties.setApiKey("secret-key");
        properties.setSecurityScheme("none");
        A2aAgentCard card = service.buildAgentCard("http://localhost:8081/a2a");
        assertThat(card.securitySchemes()).isEmpty();
    }

    // ==================== V1.16: SSE 流式 ====================

    @Test
    @DisplayName("V1.16: message/stream 启动异步任务并产生事件序列")
    void streamTaskSendProducesEventSequence() throws Exception {
        when(toolManager.invoke(eq("calculator"), any(Map.class)))
                .thenReturn(Mono.just(Map.of("success", true, "result", 42)));

        String taskId = service.streamTaskSend(Map.of("message", Map.of(
                "text", "6*7",
                "metadata", Map.of("skillId", "calculator", "arguments", Map.of("expr", "6*7"))
        )));
        assertThat(taskId).startsWith("task-");

        A2aTask finalTask = awaitTask(taskId);
        assertThat(finalTask.status()).isEqualTo("completed");

        List<A2aStreamEvent> events = service.streamEvents(taskId);
        assertThat(events).extracting(A2aStreamEvent::event).contains(
                A2aBridgeService.EVT_TASK_STATUS,
                A2aBridgeService.EVT_TASK_ARTIFACT,
                A2aBridgeService.EVT_MESSAGE_DELIVERY
        );
        // 顺序：artifact 之后必有 completed 状态
        List<String> order = events.stream().map(A2aStreamEvent::event).toList();
        assertThat(order).containsSubsequence(
                A2aBridgeService.EVT_TASK_ARTIFACT,
                A2aBridgeService.EVT_TASK_STATUS);
    }

    @Test
    @DisplayName("V1.16: subscribe 重放已完成任务历史并立即 complete（task/resubscribe 核心）")
    void resubscribeReplaysCompletedHistory() throws Exception {
        when(toolManager.invoke(eq("calculator"), any(Map.class)))
                .thenReturn(Mono.just(Map.of("success", true, "result", 7)));

        String taskId = service.streamTaskSend(Map.of("message", Map.of(
                "text", "3+4",
                "metadata", Map.of("skillId", "calculator")
        )));
        awaitTask(taskId);

        List<A2aStreamEvent> replayed = new ArrayList<>();
        boolean[] completed = {false};
        boolean ok = service.subscribe(taskId,
                evt -> replayed.add(evt),
                () -> completed[0] = true);

        assertThat(ok).isTrue();
        assertThat(completed[0]).isTrue();
        assertThat(replayed).hasSize(service.streamEvents(taskId).size());
        assertThat(replayed.get(replayed.size() - 1).event())
                .isEqualTo(A2aBridgeService.EVT_MESSAGE_DELIVERY);
    }

    @Test
    @DisplayName("V1.16: subscribe 未知任务发出 TaskNotFoundEvent 并完成")
    void resubscribeUnknownTask() {
        List<A2aStreamEvent> received = new ArrayList<>();
        boolean[] completed = {false};
        boolean ok = service.subscribe("task-nope",
                evt -> received.add(evt),
                () -> completed[0] = true);

        assertThat(ok).isFalse();
        assertThat(completed[0]).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).event()).isEqualTo(A2aBridgeService.EVT_TASK_NOT_FOUND);
    }

    private A2aTask awaitTask(String taskId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Object taskObj = service.handleTaskGet(Map.of("id", taskId)).get("task");
            A2aTask task = taskObj instanceof A2aTask t ? t : null;
            if (task != null && !"working".equals(task.status())) {
                return task;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Task did not finish in time: " + taskId);
    }
}