package com.mcp.enterprise.server.endpoint;

import com.mcp.enterprise.core.endpoint.McpStatelessEndpoint;
import com.mcp.enterprise.core.security.McpOAuth2Manager;
import com.mcp.enterprise.monitor.McpMetricsCollector;
import com.mcp.enterprise.server.security.McpBearerAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 无状态 (Stateless) Web 控制器
 * <p>
 * 实现 2026-07-28 无状态核心协议，适用于 Kubernetes/Cloud Run 弹性伸缩。
 * 无需 SSE 长连接，每次请求独立处理。
 * <p>
 * Streamable HTTP 双通道 (2026-07-28 新默认传输)：
 * <ul>
 *   <li>GET  /api/mcp/v2/stream — server→client 事件流（tools/listChanged 通知 + 心跳）</li>
 *   <li>POST /api/mcp/v2/message — 客户端→服务端 JSON-RPC 请求/响应</li>
 *   <li>POST /api/mcp/v2/notify  — 管理员/事件源触发 tools/listChanged 广播</li>
 * </ul>
 * <p>
 * 兼容模式：自动检测客户端协议版本，降级到 2025-03-26 SSE 端点。
 */
@RestController
@RequestMapping("/api/mcp/v2")
public class McpStatelessController {

    private static final Logger log = LoggerFactory.getLogger(McpStatelessController.class);

    /** 心跳间隔：15s，避免网关/负载均衡长连接超时断开 */
    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;

    private final McpStatelessEndpoint statelessEndpoint;
    private final Map<String, SseEmitter> streamEmitters = new ConcurrentHashMap<>();

    /** V1.6: 网关路由指标采集器（mcp-monitor 在 classpath 时注入，否则为 null 静默降级） */
    @Autowired(required = false)
    private McpMetricsCollector metricsCollector;

    public McpStatelessController(McpStatelessEndpoint statelessEndpoint) {
        this.statelessEndpoint = statelessEndpoint;
    }

    /**
     * 协议能力声明 (2026-07-28 无状态)
     */
    @GetMapping("")
    public Map<String, Object> getCapabilities() {
        return McpStatelessEndpoint.SERVER_CAPABILITIES_V2026;
    }

    /**
     * MCP JSON-RPC 无状态消息处理
     * <p>
     * 每个请求独立处理，不依赖 session。
     * 支持 initialize / tools/list / tools/call / ping。
     * <p>
     * ✨ 2026-07-28 最终版：网关友好标头（Mcp-Method / Mcp-Name）
     * 网关可仅凭标头进行速率限制/授权；后端执行传输验证，
     * 拒绝任何与请求体不符的标头（防止标头掩盖真实调用）。
     */
    /**
     * 兼容重载（V1.19 前签名）：无令牌上下文 → scope 不拦截；无 response → 不叠加 HTTP 状态。
     */
    public Map<String, Object> handleStatelessMessage(
            Map<String, Object> message,
            String traceId,
            String apiKey,
            String mcpMethod,
            String mcpName) {
        return handleStatelessMessage(message, traceId, apiKey, mcpMethod, mcpName, null, null, false);
    }

    @PostMapping("/message")
    public Map<String, Object> handleStatelessMessage(
            @RequestBody Map<String, Object> message,
            @RequestHeader(value = "X-MCP-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = McpStatelessEndpoint.MCP_METHOD_HEADER, required = false) String mcpMethod,
            @RequestHeader(value = McpStatelessEndpoint.MCP_NAME_HEADER, required = false) String mcpName,
            HttpServletRequest request, HttpServletResponse response) {
        return handleStatelessMessage(message, traceId, apiKey, mcpMethod, mcpName, request, response, true);
    }

    /**
     * 内部实现：scope 上下文仅当 {@code fromHttp} 时从 request attribute 提取（兼容旧调用方传 null）。
     */
    private Map<String, Object> handleStatelessMessage(
            Map<String, Object> message,
            String traceId,
            String apiKey,
            String mcpMethod,
            String mcpName,
            HttpServletRequest request, HttpServletResponse response, boolean fromHttp) {

        log.debug("MCP Stateless message: method={}, traceId={}, Mcp-Method={}, Mcp-Name={}",
                message != null ? message.get("method") : null, traceId, mcpMethod, mcpName);

        // ✨ 传输验证：标头与请求体必须一致（2026-07-28 最终版）
        Map<String, Object> validationError = statelessEndpoint.validateGatewayHeaders(mcpMethod, mcpName, message);
        if (validationError != null) {
            log.warn("MCP transport validation failed: Mcp-Method={}, Mcp-Name={}", mcpMethod, mcpName);
            recordGatewayMetric(mcpMethod, mcpName, 0L, false);
            return validationError;
        }

        long start = System.currentTimeMillis();
        // V1.19: 携带令牌 scope 上下文（无 Bearer 令牌时返回 null → 不拦截，向后兼容）
        Set<String> tokenScopes = fromHttp ? extractTokenScopes(request) : null;
        Map<String, Object> mcpResponse = statelessEndpoint.handleStatelessMessage(message, traceId, tokenScopes);
        // V1.19: insufficient_scope → HTTP 403 + WWW-Authenticate（RFC 6750 §3.1）
        applyScopeStatus(mcpResponse, fromHttp ? response : null);
        recordGatewayMetric(mcpMethod, mcpName, System.currentTimeMillis() - start, true);
        return mcpResponse;
    }

    /**
     * V1.6: 记录网关路由指标（Mcp-Method/Mcp-Name 标头维度）
     * 供 API 网关按操作限流/授权的流量观测：GET /api/monitor/metrics/gateway
     *
     * @param mcpMethod Mcp-Method 标头值
     * @param mcpName   Mcp-Name 标头值（可为空）
     * @param latencyMs 处理耗时（毫秒）
     * @param success   是否成功
     */
    private void recordGatewayMetric(String mcpMethod, String mcpName, long latencyMs, boolean success) {
        if (metricsCollector == null) {
            return;
        }
        metricsCollector.recordGatewayInvocation(mcpMethod, mcpName, latencyMs, success);
    }

    /**
     * MCP 初始化端点 (2026-07-28 无状态)
     */
    @PostMapping("/initialize")
    public Map<String, Object> initialize(@RequestBody(required = false) Map<String, Object> params) {
        Map<String, Object> initMessage = Map.of(
                "jsonrpc", "2.0",
                "id", "init-1",
                "method", "initialize",
                "params", params != null ? params : Map.of()
        );
        return statelessEndpoint.handleStatelessMessage(initMessage, null);
    }

    /**
     * MCP tools/list 端点 (2026-07-28 无状态，支持分页)
     */
    @GetMapping("/tools")
    public Map<String, Object> listTools(
            @RequestParam(required = false) String cursor) {
        Map<String, Object> listMessage = Map.of(
                "jsonrpc", "2.0",
                "id", "tools-list",
                "method", "tools/list",
                "params", cursor != null ? Map.of("cursor", cursor) : Map.of()
        );
        return statelessEndpoint.handleStatelessMessage(listMessage, null);
    }

    /**
     * 兼容重载（V1.19 前签名）：无令牌上下文 → scope 不拦截。
     */
    public Map<String, Object> callTool(Map<String, Object> params) {
        return callTool(params, null, null);
    }

    /**
     * MCP tools/call 端点 (2026-07-28 无状态)
     * V1.19: 按令牌 scope 做工具级授权，不足时返回 403 + RFC 6750 insufficient_scope
     */
    @PostMapping("/tools/call")
    public Map<String, Object> callTool(@RequestBody Map<String, Object> params,
                                        HttpServletRequest request, HttpServletResponse response) {
        String toolName = params != null ? (String) params.get("name") : null;
        if (toolName == null) {
            return McpStatelessEndpoint.errorResponse("tool-call", -32602, "Missing tool name");
        }

        Set<String> tokenScopes = extractTokenScopes(request);
        Map<String, Object> callMessage = Map.of(
                "jsonrpc", "2.0",
                "id", "tool-call-" + toolName,
                "method", "tools/call",
                "params", params
        );
        Map<String, Object> mcpResponse = statelessEndpoint.handleStatelessMessage(callMessage, null, tokenScopes);
        applyScopeStatus(mcpResponse, response);
        return mcpResponse;
    }

    // ===== V1.19: 工具级 Scope 授权（Token Scope → Tool ACL） =====

    /**
     * 从请求属性提取令牌 scope 集合（Bearer 校验通过后由 {@link McpBearerAuthFilter} 写入）。
     * 无令牌上下文返回 null → 调用方走旧版鉴权路径，scope 不拦截（向后兼容）。
     */
    private Set<String> extractTokenScopes(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object info = request.getAttribute(McpBearerAuthFilter.ATTR_TOKEN_INFO);
        if (info instanceof McpOAuth2Manager.TokenInfo tokenInfo && tokenInfo.scopes() != null) {
            return tokenInfo.scopes();
        }
        return null;
    }

    /**
     * 若响应为 insufficient_scope（JSON-RPC 错误码 -32090），叠加 HTTP 403 与 WWW-Authenticate 头（RFC 6750 §3.1）。
     */
    private void applyScopeStatus(Map<String, Object> mcpResponse, HttpServletResponse response) {
        if (mcpResponse == null || response == null) {
            return;
        }
        Object error = mcpResponse.get("error");
        if (error instanceof Map<?, ?> errMap) {
            Object code = errMap.get("code");
            if (code instanceof Number n && n.intValue() == -32090) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setHeader("WWW-Authenticate",
                        "Bearer realm=\"mcp-enterprise\", error=\"insufficient_scope\", " +
                                "error_description=\"The request requires higher privileges than provided by the access token\"");
            }
        }
    }

    /**
     * Streamable HTTP — GET 事件流通道 (2026-07-28 新默认传输)
     * <p>
     * server→client 通知流：tools/listChanged 等事件通过此通道推送。
     * 客户端可通过 curl -N 或 EventSource 连接，保持长连接。
     * 每 15s 发送一次心跳，防止代理/网关断开空闲连接。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        String streamId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L); // 无超时，靠心跳保活
        streamEmitters.put(streamId, emitter);

        emitter.onCompletion(() -> streamEmitters.remove(streamId));
        emitter.onTimeout(() -> streamEmitters.remove(streamId));
        emitter.onError(e -> streamEmitters.remove(streamId));

        // 连接建立：立即发送初始端点事件
        try {
            emitter.send(SseEmitter.event()
                    .id(streamId)
                    .name("endpoint")
                    .data(Map.of(
                            "protocolVersion", McpStatelessEndpoint.MCP_2026_PROTOCOL_VERSION,
                            "streamId", streamId,
                            "heartbeatIntervalMs", HEARTBEAT_INTERVAL_MS
                    )));
        } catch (IOException e) {
            log.warn("Failed to send initial stream event: {}", e.getMessage());
            streamEmitters.remove(streamId);
            emitter.complete();
            return emitter;
        }

        // 后台心跳线程：每 15s 发送 keep-alive，保持连接存活
        Thread heartbeat = new Thread(() -> {
            try {
                while (streamEmitters.containsKey(streamId)) {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                    if (!streamEmitters.containsKey(streamId)) {
                        break;
                    }
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data(Map.of("t", System.currentTimeMillis())));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                log.debug("Stream {} heartbeat ended: {}", streamId, e.getMessage());
                streamEmitters.remove(streamId);
                emitter.complete();
            }
        }, "mcp-stream-heartbeat-" + streamId);
        heartbeat.setDaemon(true);
        heartbeat.start();

        log.info("MCP Streamable HTTP stream opened: {}", streamId);
        return emitter;
    }

    /**
     * Streamable HTTP — 触发 tools/listChanged 广播
     * <p>
     * 当工具注册中心发生变化（新增/移除/更新工具）时，调用此端点
     * 向所有已连接的流客户端推送通知，客户端随即重新拉取 tools/list。
     */
    @PostMapping("/notify")
    public Map<String, Object> notifyToolsChanged() {
        int delivered = 0;
        for (Map.Entry<String, SseEmitter> entry : streamEmitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event()
                        .name("notifications/tools/list_changed")
                        .data(Map.of("changedAt", System.currentTimeMillis())));
                delivered++;
            } catch (IOException e) {
                log.debug("Failed to notify stream {}: {}", entry.getKey(), e.getMessage());
                streamEmitters.remove(entry.getKey());
            }
        }
        log.info("tools/listChanged broadcast delivered to {} streams", delivered);
        return Map.of(
                "status", "ok",
                "delivered", delivered,
                "connectedStreams", streamEmitters.size()
        );
    }

    /**
     * 健康检查端点 (无状态模式)
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "version", "1.0.0",
                "protocol", "2026-07-28",
                "mode", "stateless",
                "transport", "streamable-http",
                "connectedStreams", streamEmitters.size()
        );
    }

    // ===== V1.7: 网关限流路由表管理端点 =====
    // 2026-07-28 规范「网关按操作限流」的运行时管理面：
    // 无需重启即可按 Mcp-Method / Mcp-Name 调整各操作 QPS。

    /**
     * 获取当前限流规则列表
     */
    @GetMapping("/ratelimit/rules")
    public Map<String, Object> getRateLimitRules() {
        var limiter = statelessEndpoint.getGatewayRateLimiter();
        return Map.of(
                "enabled", limiter.isEnabled(),
                "rules", limiter.getRuleSnapshot(),
                "total", limiter.getRuleCount()
        );
    }

    /**
     * 新增/更新限流规则
     * <p>
     * 请求体：{"method": "tools/call", "name": "greet", "maxPerSecond": 10}
     * name 支持通配符 *（greet、finance_*、*）；空串表示无 name 的操作。
     */
    @PostMapping("/ratelimit/rules")
    public Map<String, Object> addRateLimitRule(@RequestBody Map<String, Object> rule) {
        String method = rule != null ? String.valueOf(rule.getOrDefault("method", "*")) : "*";
        String name = rule != null && rule.get("name") != null ? String.valueOf(rule.get("name")) : "*";
        int maxPerSecond;
        try {
            maxPerSecond = rule != null && rule.get("maxPerSecond") != null
                    ? ((Number) rule.get("maxPerSecond")).intValue()
                    : 10;
        } catch (ClassCastException e) {
            return McpStatelessEndpoint.errorResponse(null, -32602, "maxPerSecond must be a number");
        }
        if (maxPerSecond <= 0) {
            return McpStatelessEndpoint.errorResponse(null, -32602, "maxPerSecond must be positive");
        }
        statelessEndpoint.getGatewayRateLimiter().addRule(method, name, maxPerSecond);
        return Map.of("status", "ok", "rule", Map.of("method", method, "name", name, "maxPerSecond", maxPerSecond));
    }

    /**
     * 删除限流规则（query: method + name）
     */
    @DeleteMapping("/ratelimit/rules")
    public Map<String, Object> removeRateLimitRule(
            @RequestParam String method,
            @RequestParam(required = false, defaultValue = "") String name) {
        boolean removed = statelessEndpoint.getGatewayRateLimiter().removeRule(method, name);
        return Map.of("status", removed ? "removed" : "not-found", "method", method, "name", name);
    }

    /**
     * 清空所有限流规则（谨慎操作：恢复为全放行）
     */
    @DeleteMapping("/ratelimit/rules/all")
    public Map<String, Object> clearRateLimitRules() {
        statelessEndpoint.getGatewayRateLimiter().clearRules();
        return Map.of("status", "cleared", "total", 0);
    }

    /**
     * 切换限流开关
     * 请求体：{"enabled": false}
     */
    @PostMapping("/ratelimit/toggle")
    public Map<String, Object> toggleRateLimit(@RequestBody Map<String, Object> body) {
        boolean enabled = body != null && body.get("enabled") != null
                ? Boolean.parseBoolean(String.valueOf(body.get("enabled")))
                : true;
        statelessEndpoint.getGatewayRateLimiter().setEnabled(enabled);
        return Map.of("status", "ok", "enabled", enabled);
    }
}
