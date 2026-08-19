package com.mcp.enterprise.server.endpoint;

import com.mcp.enterprise.core.endpoint.McpStatelessEndpoint;
import com.mcp.enterprise.monitor.McpMetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
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
    @PostMapping("/message")
    public Map<String, Object> handleStatelessMessage(
            @RequestBody Map<String, Object> message,
            @RequestHeader(value = "X-MCP-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = McpStatelessEndpoint.MCP_METHOD_HEADER, required = false) String mcpMethod,
            @RequestHeader(value = McpStatelessEndpoint.MCP_NAME_HEADER, required = false) String mcpName) {

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
        Map<String, Object> response = statelessEndpoint.handleStatelessMessage(message, traceId);
        recordGatewayMetric(mcpMethod, mcpName, System.currentTimeMillis() - start, true);
        return response;
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
     * MCP tools/call 端点 (2026-07-28 无状态)
     */
    @PostMapping("/tools/call")
    public Map<String, Object> callTool(@RequestBody Map<String, Object> params) {
        String toolName = params != null ? (String) params.get("name") : null;
        if (toolName == null) {
            return McpStatelessEndpoint.errorResponse("tool-call", -32602, "Missing tool name");
        }

        Map<String, Object> callMessage = Map.of(
                "jsonrpc", "2.0",
                "id", "tool-call-" + toolName,
                "method", "tools/call",
                "params", params
        );
        return statelessEndpoint.handleStatelessMessage(callMessage, null);
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
}
