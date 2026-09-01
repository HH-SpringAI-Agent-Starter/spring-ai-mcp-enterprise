package com.mcp.integration.a2a;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A2A JSON-RPC HTTP 端点
 *
 * 路由（basePath 默认 /a2a）：
 * - GET  {base}/agent-card        → Agent Card（技能列表派生自 MCP 工具注册中心）
 * - POST {base}/rpc               → A2A JSON-RPC 2.0 分派（message/send、task/send、task/get、task/cancel、agent/quote）
 * - POST {base}/rpc/stream        → V1.16: A2A SSE 流式（message/stream、task/resubscribe）
 * - GET  {base}/health            → 存活检查
 *
 * 可选鉴权：mcp.enterprise.a2a.api-key 非空时要求 X-A2A-Key 请求头。
 * 说明：本控制器以 @Bean 方式注册（AutoConfiguration），Spring MVC 可自动发现，
 * 无需应用方调整 @ComponentScan。
 */
@RestController
public class A2aRpcController {

    private static final Logger log = LoggerFactory.getLogger(A2aRpcController.class);

    private final A2aBridgeService bridgeService;
    private final McpA2aProperties properties;

    public A2aRpcController(A2aBridgeService bridgeService, McpA2aProperties properties) {
        this.bridgeService = bridgeService;
        this.properties = properties;
    }

    // ===== Agent Card =====

    @GetMapping(path = "${mcp.enterprise.a2a.base-path:/a2a}/agent-card", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Object agentCard(HttpServletRequest request) {
        if (!authorized(request)) {
            return unauthorized();
        }
        String baseUrl = request.getRequestURL().toString().replace("/agent-card", "");
        return bridgeService.buildAgentCard(baseUrl);
    }

    /**
     * A2A 协议标准发现路径别名：允许 A2A 客户端默认请求 /.well-known/agent-card.json
     */
    @GetMapping(path = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Object agentCardWellKnown(HttpServletRequest request) {
        if (!authorized(request)) {
            return unauthorized();
        }
        String baseUrl = request.getRequestURL().toString().replace("/.well-known/agent-card.json", "");
        return bridgeService.buildAgentCard(baseUrl);
    }

    // ===== JSON-RPC =====

    @PostMapping(path = "${mcp.enterprise.a2a.base-path:/a2a}/rpc", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> rpc(@RequestBody(required = false) Map<String, Object> body,
                                      HttpServletRequest request) {
        if (!authorized(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "jsonrpc", "2.0", "id", null,
                    "error", Map.of("code", -32009, "message", "Authentication required (X-A2A-Key)")
            ));
        }
        if (body == null || !"2.0".equals(body.get("jsonrpc"))) {
            return invalidRequest(null, "Invalid JSON-RPC request (jsonrpc=2.0 required)");
        }
        Object id = body.getOrDefault("id", null);
        String method = body.get("method") == null ? "" : String.valueOf(body.get("method"));
        Object paramsObj = body.get("params");

        Map<String, Object> params = new LinkedHashMap<>();
        if (paramsObj instanceof Map<?, ?> p) {
            p.forEach((k, v) -> params.put(String.valueOf(k), v));
        }

        Map<String, Object> dispatched = bridgeService.dispatch(method, params);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        if (dispatched.containsKey("error")) {
            response.put("error", dispatched.get("error"));
        } else {
            response.put("result", dispatched.get("result"));
        }
        return ResponseEntity.ok(response);
    }

    // ===== V1.16: SSE 流式端点 (message/stream / task/resubscribe) =====

    /**
     * A2A SSE 流式端点：
     * - message/stream   : 启动异步工具执行，推送 TaskStatusUpdateEvent → TaskArtifactUpdateEvent → MessageDeliveryEvent，完成后关闭流
     * - task/resubscribe : 重放目标任务事件历史（含已完成任务），随后保持连接直至新事件/完成
     *
     * 请求体与 JSON-RPC 相同（jsonrpc=2.0, id, method, params），响应为 text/event-stream。
     */
    @PostMapping(path = "${mcp.enterprise.a2a.base-path:/a2a}/rpc/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter rpcStream(@RequestBody(required = false) Map<String, Object> body,
                                HttpServletRequest request) {
        if (!authorized(request)) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("code", -32009, "message", "Authentication required (X-A2A-Key)")));
            } catch (IOException e) {
                // ignore
            }
            emitter.complete();
            return emitter;
        }
        SseEmitter emitter = new SseEmitter(properties.getTaskTimeoutMs() + 30_000L);

        if (body == null || !"2.0".equals(body.get("jsonrpc"))) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("code", -32600, "message", "Invalid JSON-RPC request (jsonrpc=2.0 required)")));
            } catch (IOException e) {
                // ignore
            }
            emitter.complete();
            return emitter;
        }

        String method = body.get("method") == null ? "" : String.valueOf(body.get("method"));
        Object paramsObj = body.get("params");
        Map<String, Object> params = new LinkedHashMap<>();
        if (paramsObj instanceof Map<?, ?> p) {
            p.forEach((k, v) -> params.put(String.valueOf(k), v));
        }

        log.info("🌊 A2A SSE stream: method={}, from={}", method, request.getRemoteAddr());

        if ("message/stream".equals(method)) {
            if (!properties.isStreamingEnabled()) {
                completeWithError(emitter, -32601, "message/stream disabled (mcp.enterprise.a2a.streaming-enabled=false)");
                return emitter;
            }
            try {
                String taskId = bridgeService.streamTaskSend(params);
                sendEvent(emitter, A2aBridgeService.EVT_TASK_STATUS,
                        Map.of("taskId", taskId, "status", "submitted"));
                bridgeService.subscribe(taskId,
                        evt -> sendEvent(emitter, evt.event(), evt.data()),
                        emitter::complete);
            } catch (A2aBridgeService.A2aRpcException e) {
                completeWithError(emitter, e.code, e.getMessage());
            }
        } else if ("task/resubscribe".equals(method)) {
            Object idObj = params.get("id");
            if (idObj == null) {
                completeWithError(emitter, -32602, "params.id (taskId) is required");
                return emitter;
            }
            String taskId = String.valueOf(idObj);
            if (!bridgeService.hasTask(taskId)) {
                completeWithError(emitter, -32004, "Task not found: " + taskId);
                return emitter;
            }
            bridgeService.subscribe(taskId,
                    evt -> sendEvent(emitter, evt.event(), evt.data()),
                    emitter::complete);
        } else {
            completeWithError(emitter, -32601, "Method not found for SSE transport: " + method
                    + " (use message/stream or task/resubscribe)");
        }
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.warn("A2A SSE send failed (client disconnected): {}", e.getMessage());
        }
    }

    private void completeWithError(SseEmitter emitter, int code, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("code", code, "message", message)));
        } catch (IOException e) {
            // ignore
        }
        emitter.complete();
    }

    // ===== Health =====

    @GetMapping(path = "${mcp.enterprise.a2a.base-path:/a2a}/health")
    @ResponseBody
    public Object health(HttpServletRequest request) {
        if (!authorized(request)) {
            return unauthorized();
        }
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("agent", properties.getAgentName());
        health.put("skills", bridgeService.listSkills().size());
        health.put("streaming", properties.isStreamingEnabled());
        return health;
    }

    // ===== 私有 =====

    private boolean authorized(HttpServletRequest request) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return true; // 未配置 api-key：不启用 A2A 网关层鉴权（可置于网关 API Key 之后）
        }
        String provided = request.getHeader("X-A2A-Key");
        boolean ok = apiKey.equals(provided);
        if (!ok) {
            log.warn("🚫 A2A 鉴权失败: missing/mismatched X-A2A-Key from {}", request.getRemoteAddr());
        }
        return ok;
    }

    private ResponseEntity<Object> invalidRequest(Object id, String message) {
        return ResponseEntity.badRequest().body(Map.of(
                "jsonrpc", "2.0", "id", id,
                "error", Map.of("code", -32600, "message", message)
        ));
    }

    private ResponseEntity<Object> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "jsonrpc", "2.0", "id", null,
                "error", Map.of("code", -32009, "message", "Authentication required (X-A2A-Key)")
        ));
    }
}