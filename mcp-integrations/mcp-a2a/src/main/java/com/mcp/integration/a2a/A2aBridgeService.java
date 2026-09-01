package com.mcp.integration.a2a;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.registry.ToolRegistry;
import com.mcp.enterprise.core.tool.McpToolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A2A 桥接服务 —— MCP 工具注册中心 → A2A Agent 协议
 *
 * 核心职责：
 * 1. 将 ToolRegistry 中的 MCP 工具派生为 A2A Agent Card / Skill（id = 工具名）
 * 2. 处理 A2A JSON-RPC 方法：message/send、task/send、task/get、task/cancel
 * 3. V1.16 起支持 SSE 流式：message/stream（异步工具执行 + 事件流）、task/resubscribe（历史重放）
 * 4. V1.16 起 Agent Card 声明 securitySchemes（apiKey / oauth2），供 A2A 编排器做鉴权协商（mcp-auth 打通第一步）
 * 5. 调用 McpToolManager.invoke 执行 MCP 工具，结果包装为 A2A Message / Task Artifact
 *
 * 约定（写进集成文档）：
 * - A2A 调用方通过 message.metadata.skillId 指定 MCP 工具名，通过 metadata.arguments 传参
 * - 支持消息前缀路由：文本以 "tool:<name>" 开头时自动路由（无 metadata 场景的兜底）
 * - 一次工具调用 = 一个 Task；任务状态 submitted → working → completed/failed/canceled
 *
 * 流式事件（A2A v1.0 SSE 事件名）：
 * - TaskStatusUpdateEvent   任务状态变更（working/completed/failed/canceled）
 * - TaskArtifactUpdateEvent 工具结果产出 Artifact
 * - MessageDeliveryEvent    message/stream 最终投递的 agent 消息
 * - TaskNotFoundEvent       task/resubscribe 目标任务不存在
 */
public class A2aBridgeService {

    private static final Logger log = LoggerFactory.getLogger(A2aBridgeService.class);

    /** A2A JSON-RPC 自定义错误码 */
    public static final int ERR_INVALID_REQUEST = -32600;
    public static final int ERR_METHOD_NOT_FOUND = -32601;
    public static final int ERR_INVALID_PARAMS = -32602;
    public static final int ERR_TOOL_NOT_FOUND = -32003;   // 对应 A2A agent not found
    public static final int ERR_TASK_NOT_FOUND = -32004;
    public static final int ERR_TASK_NOT_CANCELABLE = -32005;

    /** A2A SSE 流式事件名 */
    public static final String EVT_TASK_STATUS = "TaskStatusUpdateEvent";
    public static final String EVT_TASK_ARTIFACT = "TaskArtifactUpdateEvent";
    public static final String EVT_MESSAGE_DELIVERY = "MessageDeliveryEvent";
    public static final String EVT_TASK_NOT_FOUND = "TaskNotFoundEvent";

    private final ToolRegistry registry;
    private final McpToolManager toolManager;
    private final McpA2aProperties properties;

    /** 任务存储（按 A2A taskId） */
    private final Map<String, A2aTask> tasks = new ConcurrentHashMap<>();
    /** 任务取消标记 */
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
    /** 流式历史事件（供 task/resubscribe 重放） */
    private final Map<String, CopyOnWriteArrayList<A2aStreamEvent>> streamHistory = new ConcurrentHashMap<>();
    /** 流式活跃订阅者（任务完成后由 completeStream 清空） */
    private final Map<String, CopyOnWriteArrayList<StreamSubscriber>> streamSubscribers = new ConcurrentHashMap<>();
    /** 流式异步执行线程池（daemon，不阻塞 HTTP 线程） */
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "a2a-stream");
        t.setDaemon(true);
        return t;
    });

    public A2aBridgeService(ToolRegistry registry, McpToolManager toolManager, McpA2aProperties properties) {
        this.registry = registry;
        this.toolManager = toolManager;
        this.properties = properties;
        log.info("🙋 A2A 网关已初始化 (agent={}, streaming={}, securityScheme={}, skills 将按需派生自 ToolRegistry)",
                properties.getAgentName(), properties.isStreamingEnabled(), properties.resolvedSecurityScheme());
    }

    // ==================== Agent Card ====================

    /**
     * 构建 Agent Card：全部启用的 MCP 工具 → A2A Skills
     * V1.16: capabilities.streaming 跟随配置；securitySchemes 按安全方案声明
     */
    public A2aAgentCard buildAgentCard(String baseUrl) {
        List<A2aSkill> skills = listSkills();
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("streaming", properties.isStreamingEnabled());
        capabilities.put("pushNotifications", false);
        capabilities.put("stateTransitionHistory", false);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("name", properties.getAgentName());
        card.put("description", properties.getAgentDescription());
        card.put("url", baseUrl == null ? "" : baseUrl);
        card.put("version", properties.getVersion());
        card.put("capabilities", capabilities);
        card.put("securitySchemes", buildSecuritySchemes());
        card.put("skills", skills);
        return new A2aAgentCard(properties.getAgentName(), properties.getAgentDescription(),
                baseUrl == null ? "" : baseUrl, properties.getVersion(), capabilities,
                buildSecuritySchemes(), skills);
    }

    /**
     * 构建 A2A 规范 securitySchemes 声明（V1.16，mcp-auth 打通第一步）：
     * - api-key: { type: apiKey, in: header, name: X-A2A-Key }
     * - oauth2 : { type: oauth2, flows.clientCredentials.tokenUrl = 配置的 token 端点 }
     * - none   : 空列表
     */
    public List<Map<String, Object>> buildSecuritySchemes() {
        String scheme = properties.resolvedSecurityScheme();
        if ("oauth2".equalsIgnoreCase(scheme)) {
            String tokenUrl = properties.getOauth2TokenUrl();
            if (tokenUrl == null || tokenUrl.isBlank()) {
                tokenUrl = "/oauth2/token"; // 默认对接 mcp-auth 令牌端点
            }
            return List.of(Map.of(
                    "type", "oauth2",
                    "flows", Map.of("clientCredentials", Map.of(
                            "tokenUrl", tokenUrl,
                            "scopes", Map.of()
                    ))
            ));
        }
        if ("api-key".equalsIgnoreCase(scheme)) {
            return List.of(Map.of(
                    "type", "apiKey",
                    "in", "header",
                    "name", "X-A2A-Key"
            ));
        }
        return List.of();
    }

    /**
     * 工具注册中心 → A2A Skill 列表（仅启用工具）
     */
    public List<A2aSkill> listSkills() {
        List<A2aSkill> skills = new ArrayList<>();
        registry.listAll()
                .filter(ToolDefinition::isEnabled)
                .collectList()
                .blockOptional(Duration.ofSeconds(5))
                .ifPresent(defs -> defs.forEach(def -> skills.add(toSkill(def))));
        return skills;
    }

    private A2aSkill toSkill(ToolDefinition def) {
        List<String> tags = new ArrayList<>();
        if (def.getCategory() != null) {
            tags.add(def.getCategory());
        }
        if (def.getModule() != null) {
            tags.add(def.getModule());
        }
        return A2aSkill.of(def.getName(), def.getDisplayName() != null ? def.getDisplayName() : def.getName(),
                def.getDescription() != null ? def.getDescription() : "", tags, def.getInputSchema());
    }

    // ==================== JSON-RPC 方法分派 ====================

    /**
     * 统一入口：解析 A2A JSON-RPC 请求并返回结果
     *
     * @return Map 含 "result" 或 "error"（调用方无需再包 jsonrpc/id）
     */
    public Map<String, Object> dispatch(String method, Map<String, Object> params) {
        if (params == null) {
            params = Map.of();
        }
        try {
            switch (method) {
                case "message/send":
                    return Map.of("result", handleMessageSend(params));
                case "task/send":
                    return Map.of("result", handleTaskSend(params));
                case "task/get":
                    return Map.of("result", handleTaskGet(params));
                case "task/cancel":
                    return Map.of("result", handleTaskCancel(params));
                case "agent/quote":
                    return Map.of("result", handleQuote());
                case "message/stream":
                case "task/resubscribe":
                    // 流式方法需走 SSE 传输（Accept: text/event-stream），见 A2aRpcController
                    return Map.of("error", rpcError(ERR_METHOD_NOT_FOUND,
                            method + " requires SSE transport (Accept: text/event-stream)"));
                default:
                    return Map.of("error", rpcError(ERR_METHOD_NOT_FOUND, "Method not found: " + method));
            }
        } catch (A2aRpcException e) {
            return Map.of("error", rpcError(e.code, e.getMessage()));
        }
    }

    // ==================== message/send ====================

    /**
     * 一次性消息交互：解析 user 消息 → 定位工具 → 执行 → 返回 agent 消息
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleMessageSend(Map<String, Object> params) {
        Object messageObj = params.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            throw new A2aRpcException(ERR_INVALID_PARAMS, "params.message (object) is required");
        }
        Map<String, Object> metadata = messageMap.get("metadata") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        String text = String.valueOf(messageMap.get("text") == null
                ? extractTextFromParts(messageMap.get("parts"))
                : messageMap.get("text"));

        A2aMessage userMessage = new A2aMessage("user", List.of(Map.of("text", text)), metadata, null, null);
        String skillId = resolveSkillId(userMessage);

        Map<String, Object> result = executeSkill(skillId, userMessage.arguments());
        boolean ok = !Boolean.FALSE.equals(result.get("success"));
        Map<String, Object> agentMetadata = Map.of("skillId", skillId);

        if (ok) {
            String output = result.get("output") != null ? String.valueOf(result.get("output")) : toJson(result);
            return Map.of(
                    "message", A2aMessage.agentText(output, agentMetadata),
                    "contextId", "ctx-" + UUID.randomUUID().toString().substring(0, 8)
            );
        }
        throw new A2aRpcException(ERR_INVALID_PARAMS, "Tool execution failed: "
                + result.getOrDefault("error", "unknown error"));
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromParts(Object partsObj) {
        if (partsObj instanceof List<?> parts && !parts.isEmpty() && parts.get(0) instanceof Map<?, ?> part) {
            Object text = part.get("text");
            if (text != null) {
                return String.valueOf(text);
            }
        }
        return "";
    }

    // ==================== task/send / task/get / task/cancel ====================

    @SuppressWarnings("unchecked")
    public Map<String, Object> handleTaskSend(Map<String, Object> params) {
        Object messageObj = params.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            throw new A2aRpcException(ERR_INVALID_PARAMS, "params.message (object) is required");
        }
        Map<String, Object> metadata = messageMap.get("metadata") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        String text = String.valueOf(messageMap.get("text") == null
                ? extractTextFromParts(messageMap.get("parts"))
                : messageMap.get("text"));

        A2aMessage userMessage = new A2aMessage("user", List.of(Map.of("text", text)), metadata, null, null);
        String skillId = resolveSkillId(userMessage);

        String taskId = "task-" + UUID.randomUUID().toString().substring(0, 12);
        A2aTask working = A2aTask.working(taskId, userMessage);
        tasks.put(taskId, working);
        cancelFlags.put(taskId, new AtomicBoolean(false));
        trimTasks();

        log.info("🧵 A2A task/send: task={}, skill={}", taskId, skillId);

        if (cancelFlags.get(taskId).get()) {
            A2aTask canceled = working.canceled();
            tasks.put(taskId, canceled);
            return Map.of("task", canceled, "contextId", "ctx-" + taskId);
        }

        Map<String, Object> result = executeSkill(skillId, userMessage.arguments());
        boolean ok = !Boolean.FALSE.equals(result.get("success"));

        A2aTask finished;
        if (ok) {
            List<Map<String, Object>> artifacts = List.of(Map.of(
                    "name", "tool-result",
                    "parts", List.of(Map.of("text",
                            result.get("output") != null ? String.valueOf(result.get("output")) : toJson(result))),
                    "metadata", Map.of("skillId", skillId, "tool", skillId, "success", true)
            ));
            finished = working.completed(artifacts)
                    .withAgentMessage(A2aMessage.agentText("Task completed", Map.of("skillId", skillId)));
        } else {
            finished = working.failed(String.valueOf(result.getOrDefault("error", "unknown error")));
        }
        tasks.put(taskId, finished);
        return Map.of("task", finished, "contextId", "ctx-" + taskId);
    }

    public Map<String, Object> handleTaskGet(Map<String, Object> params) {
        Object taskId = params.get("id");
        if (taskId == null) {
            throw new A2aRpcException(ERR_INVALID_PARAMS, "params.id (taskId) is required");
        }
        A2aTask task = tasks.get(String.valueOf(taskId));
        if (task == null) {
            throw new A2aRpcException(ERR_TASK_NOT_FOUND, "Task not found: " + taskId);
        }
        return Map.of("task", task);
    }

    public Map<String, Object> handleTaskCancel(Map<String, Object> params) {
        Object taskId = params.get("id");
        if (taskId == null) {
            throw new A2aRpcException(ERR_INVALID_PARAMS, "params.id (taskId) is required");
        }
        String id = String.valueOf(taskId);
        A2aTask task = tasks.get(id);
        if (task == null) {
            throw new A2aRpcException(ERR_TASK_NOT_FOUND, "Task not found: " + id);
        }
        if ("completed".equals(task.status()) || "failed".equals(task.status())) {
            throw new A2aRpcException(ERR_TASK_NOT_CANCELABLE, "Task already finished: " + id);
        }
        AtomicBoolean flag = cancelFlags.computeIfAbsent(id, k -> new AtomicBoolean(false));
        flag.set(true);
        A2aTask canceled = task.canceled();
        tasks.put(id, canceled);
        return Map.of("task", canceled);
    }

    public Map<String, Object> handleQuote() {
        return Map.of(
                "quotedMessage", "MCP Enterprise A2A Gateway — 通过 metadata.skillId + metadata.arguments 调用 MCP 工具",
                "skills", listSkills().stream().map(A2aSkill::id).toList()
        );
    }

    // ==================== V1.16: SSE 流式 (message/stream / task/resubscribe) ====================

    /**
     * 启动一个流式任务（message/stream）：立即返回 taskId，工具在后台异步执行，
     * 事件通过 {@link #subscribe} 推送给订阅者。事件序列：
     * TaskStatusUpdateEvent(working) → TaskArtifactUpdateEvent → TaskStatusUpdateEvent(completed/failed) → MessageDeliveryEvent
     */
    @SuppressWarnings("unchecked")
    public String streamTaskSend(Map<String, Object> params) {
        Object messageObj = params.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            throw new A2aRpcException(ERR_INVALID_PARAMS, "params.message (object) is required");
        }
        Map<String, Object> metadata = messageMap.get("metadata") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        String text = String.valueOf(messageMap.get("text") == null
                ? extractTextFromParts(messageMap.get("parts"))
                : messageMap.get("text"));
        A2aMessage userMessage = new A2aMessage("user", List.of(Map.of("text", text)), metadata, null, null);
        String skillId = resolveSkillId(userMessage);

        String taskId = "task-" + UUID.randomUUID().toString().substring(0, 12);
        A2aTask working = A2aTask.working(taskId, userMessage);
        tasks.put(taskId, working);
        cancelFlags.put(taskId, new AtomicBoolean(false));
        streamHistory.put(taskId, new CopyOnWriteArrayList<>());
        trimTasks();

        // 初始状态事件（同步发出，确保订阅即得）
        emit(taskId, new A2aStreamEvent(EVT_TASK_STATUS, taskId,
                Map.of("taskId", taskId, "status", "working", "skillId", skillId)));

        log.info("🌊 A2A message/stream: task={}, skill={}", taskId, skillId);

        streamExecutor.submit(() -> {
            try {
                if (cancelFlags.get(taskId).get()) {
                    A2aTask canceled = working.canceled();
                    tasks.put(taskId, canceled);
                    emit(taskId, new A2aStreamEvent(EVT_TASK_STATUS, taskId,
                            Map.of("taskId", taskId, "status", "canceled")));
                    completeStream(taskId);
                    return;
                }
                Map<String, Object> result = executeSkill(skillId, userMessage.arguments());
                boolean ok = !Boolean.FALSE.equals(result.get("success"));

                if (ok) {
                    List<Map<String, Object>> artifacts = List.of(Map.of(
                            "name", "tool-result",
                            "parts", List.of(Map.of("text",
                                    result.get("output") != null ? String.valueOf(result.get("output")) : toJson(result))),
                            "metadata", Map.of("skillId", skillId, "tool", skillId, "success", true)
                    ));
                    A2aTask done = working.completed(artifacts)
                            .withAgentMessage(A2aMessage.agentText("Task completed", Map.of("skillId", skillId)));
                    tasks.put(taskId, done);

                    A2aStreamEvent artifactEvt = new A2aStreamEvent(EVT_TASK_ARTIFACT, taskId,
                            Map.of("taskId", taskId, "artifact", artifacts.get(0)));
                    emit(taskId, artifactEvt);

                    emit(taskId, new A2aStreamEvent(EVT_TASK_STATUS, taskId,
                            Map.of("taskId", taskId, "status", "completed")));

                    emit(taskId, new A2aStreamEvent(EVT_MESSAGE_DELIVERY, taskId, Map.of(
                            "taskId", taskId,
                            "message", A2aMessage.agentText(
                                    result.get("output") != null ? String.valueOf(result.get("output")) : toJson(result),
                                    Map.of("skillId", skillId)),
                            "contextId", "ctx-" + taskId
                    )));
                } else {
                    A2aTask failed = working.failed(String.valueOf(result.getOrDefault("error", "unknown error")));
                    tasks.put(taskId, failed);
                    emit(taskId, new A2aStreamEvent(EVT_TASK_STATUS, taskId,
                            Map.of("taskId", taskId, "status", "failed",
                                    "error", String.valueOf(result.getOrDefault("error", "unknown error")))));
                }
            } catch (A2aRpcException e) {
                A2aTask failed = working.failed(e.getMessage());
                tasks.put(taskId, failed);
                emit(taskId, new A2aStreamEvent(EVT_TASK_STATUS, taskId,
                        Map.of("taskId", taskId, "status", "failed", "error", e.getMessage())));
            } catch (Exception e) {
                log.error("A2A stream task {} unexpected failure", taskId, e);
                A2aTask failed = working.failed("internal error: " + e.getMessage());
                tasks.put(taskId, failed);
                emit(taskId, new A2aStreamEvent(EVT_TASK_STATUS, taskId,
                        Map.of("taskId", taskId, "status", "failed", "error", failed.error())));
            }
            completeStream(taskId);
        });
        return taskId;
    }

    /**
     * 订阅任务事件流（task/resubscribe 与 message/stream 共用）：
     * 1. 先同步重放历史事件（解决“任务已完成、订阅来晚”的竞态）
     * 2. 任务未终态时挂接活跃订阅，终态时立即触发 onComplete
     *
     * @return false 表示任务不存在（已发出 TaskNotFoundEvent 并立即 complete）
     */
    public boolean subscribe(String taskId, Consumer<A2aStreamEvent> consumer, Runnable onComplete) {
        CopyOnWriteArrayList<A2aStreamEvent> history = streamHistory.get(taskId);
        if (history == null) {
            consumer.accept(new A2aStreamEvent(EVT_TASK_NOT_FOUND, taskId,
                    Map.of("taskId", taskId, "error", "Task not found: " + taskId)));
            onComplete.run();
            return false;
        }
        // 重放历史（含同步过程的 working 事件）
        try {
            for (A2aStreamEvent evt : history) {
                consumer.accept(evt);
            }
        } catch (Exception e) {
            log.warn("A2A subscriber replay aborted for task {}: {}", taskId, e.getMessage());
        }

        A2aTask task = tasks.get(taskId);
        boolean terminal = task != null && List.of("completed", "failed", "canceled").contains(task.status());
        if (terminal) {
            onComplete.run();
            return true;
        }
        streamSubscribers.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>())
                .add(new StreamSubscriber(consumer, onComplete));
        return true;
    }

    /** 事件历史（测试/运维可查） */
    public List<A2aStreamEvent> streamEvents(String taskId) {
        CopyOnWriteArrayList<A2aStreamEvent> history = streamHistory.get(taskId);
        return history == null ? List.of() : new ArrayList<>(history);
    }

    /** 任务是否存在 */
    public boolean hasTask(String taskId) {
        return tasks.containsKey(taskId);
    }

    // ==================== 内部：事件流 ====================

    private void emit(String taskId, A2aStreamEvent event) {
        CopyOnWriteArrayList<A2aStreamEvent> history =
                streamHistory.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>());
        history.add(event);
        CopyOnWriteArrayList<StreamSubscriber> subs = streamSubscribers.get(taskId);
        if (subs != null) {
            for (StreamSubscriber s : subs) {
                try {
                    s.consumer.accept(event);
                } catch (Exception e) {
                    log.warn("A2A subscriber error on {}: {}", taskId, e.getMessage());
                }
            }
        }
    }

    private void completeStream(String taskId) {
        CopyOnWriteArrayList<StreamSubscriber> subs = streamSubscribers.remove(taskId);
        if (subs != null) {
            for (StreamSubscriber s : subs) {
                try {
                    s.onComplete.run();
                } catch (Exception e) {
                    log.warn("A2A onComplete error on {}: {}", taskId, e.getMessage());
                }
            }
        }
    }

    private record StreamSubscriber(Consumer<A2aStreamEvent> consumer, Runnable onComplete) {
    }

    // ==================== 工具执行 ====================

    /**
     * 解析目标工具名：
     * 1. metadata.skillId 优先
     * 2. 消息文本前缀 "tool:<name>" 兜底（无 metadata 的纯文本通道）
     */
    private String resolveSkillId(A2aMessage message) {
        String skillId = message.skillId();
        if (skillId != null && !skillId.isBlank()) {
            return skillId;
        }
        String text = message.firstText();
        if (text.startsWith("tool:")) {
            return text.substring(5).trim().split("\\s+")[0];
        }
        throw new A2aRpcException(ERR_TOOL_NOT_FOUND,
                "未指定工具: 请在 message.metadata.skillId 中指定 MCP 工具名，可用: " + availableSkillNames());
    }

    /**
     * 执行 MCP 工具并统一包装结果
     */
    public Map<String, Object> executeSkill(String skillId, Map<String, Object> args) {
        if (skillId == null || skillId.isBlank()) {
            throw new A2aRpcException(ERR_TOOL_NOT_FOUND, "skillId is required");
        }
        if (!registry.isRegistered(skillId)) {
            log.warn("🚫 A2A 请求未知工具: {}", skillId);
            throw new A2aRpcException(ERR_TOOL_NOT_FOUND,
                    "工具不存在: " + skillId + "，可用 " + availableSkillNames());
        }
        Map<String, Object> result = toolManager.invoke(skillId, args == null ? Map.of() : args)
                .block(Duration.ofMillis(properties.getTaskTimeoutMs()));
        if (result == null) {
            throw new A2aRpcException(ERR_INVALID_REQUEST, "工具执行超时: " + skillId);
        }
        // 统一产出：output 字段（字符串）便于 A2A text part 直接使用
        Map<String, Object> normalized = new LinkedHashMap<>(result);
        if (!normalized.containsKey("output")) {
            normalized.put("output", toJson(result));
        }
        return normalized;
    }

    // ==================== 工具 ====================

    private String availableSkillNames() {
        return listSkills().stream().map(A2aSkill::id).sorted().toList().toString();
    }

    private void trimTasks() {
        if (tasks.size() <= properties.getMaxTasks()) {
            return;
        }
        int overflow = tasks.size() - properties.getMaxTasks();
        tasks.keySet().stream().sorted().limit(overflow).forEach(tasks::remove);
        cancelFlags.keySet().stream().sorted().limit(overflow).forEach(cancelFlags::remove);
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private Map<String, Object> rpcError(int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        return error;
    }

    /** A2A RPC 业务异常 */
    public static class A2aRpcException extends RuntimeException {
        public final int code;

        public A2aRpcException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}