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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A2A 桥接服务 — MCP 工具注册中心 ↔ A2A Agent 协议
 *
 * 核心职责：
 * 1. 将 ToolRegistry 中的 MCP 工具派生为 A2A Agent Card / Skill（id = 工具名）
 * 2. 处理 A2A JSON-RPC 方法：message/send、task/send、task/get、task/cancel
 * 3. 调用 McpToolManager.invoke 执行 MCP 工具，结果包装为 A2A Message / Task Artifact
 *
 * 约定（写进集成文档）：
 * - A2A 调用方通过 message.metadata.skillId 指定 MCP 工具名，通过 metadata.arguments 传参
 * - 支持消息前缀路由：文本以 "tool:<name>" 开头时自动路由（无 metadata 场景的兜底）
 * - 一次工具调用 = 一个 Task；任务状态 submitted → working → completed/failed/canceled
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

    private final ToolRegistry registry;
    private final McpToolManager toolManager;
    private final McpA2aProperties properties;

    /** 任务存储（按 A2A taskId） */
    private final Map<String, A2aTask> tasks = new ConcurrentHashMap<>();
    /** 任务取消标记 */
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public A2aBridgeService(ToolRegistry registry, McpToolManager toolManager, McpA2aProperties properties) {
        this.registry = registry;
        this.toolManager = toolManager;
        this.properties = properties;
        log.info("🌐 A2A 网关已初始化 (agent={}, skills 将按需派生自 ToolRegistry)", properties.getAgentName());
    }

    // ==================== Agent Card ====================

    /**
     * 构建 Agent Card：全部启用的 MCP 工具 → A2A Skills
     */
    public A2aAgentCard buildAgentCard(String baseUrl) {
        List<A2aSkill> skills = listSkills();
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("streaming", false);
        capabilities.put("pushNotifications", false);
        capabilities.put("stateTransitionHistory", false);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("name", properties.getAgentName());
        card.put("description", properties.getAgentDescription());
        card.put("url", baseUrl == null ? "" : baseUrl);
        card.put("version", properties.getVersion());
        card.put("capabilities", capabilities);
        card.put("skills", skills);
        return new A2aAgentCard(properties.getAgentName(), properties.getAgentDescription(),
                baseUrl == null ? "" : baseUrl, properties.getVersion(), capabilities, skills);
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

        log.info("🔄 A2A task/send: task={}, skill={}", taskId, skillId);

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
                    "工具不存在: " + skillId + "，可用: " + availableSkillNames());
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