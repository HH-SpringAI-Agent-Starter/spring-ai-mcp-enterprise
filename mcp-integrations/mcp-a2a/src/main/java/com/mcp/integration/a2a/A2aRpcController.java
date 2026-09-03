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
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A2A JSON-RPC HTTP 端点
 *
 * 路由（basePath 默认 /a2a）：
 * - GET  {base}/agent-card        → Agent Card（技能列表派生自 MCP 工具注册中心；V1.18 起可返回签名信封）
 * - GET  {base}/agent-card/verify → V1.18: 自验证结果（demo / 巡检脚本输出）
 * - POST {base}/rpc               → A2A JSON-RPC 2.0 分派（message/send、task/send、task/get、task/cancel、agent/quote）
 * - POST {base}/rpc/stream        → V1.16: A2A SSE 流式（message/stream、task/resubscribe）
 * - GET  {base}/health            → 存活检查
 *
 * 鉴权模式（V1.17，按 resolvedAuthMode 自动推导）：
 * - none    : 不启用网关层鉴权
 * - api-key : 要求 X-A2A-Key 请求头（mcp.enterprise.a2a.api-key 非空）
 * - oauth2  : 要求 Authorization: Bearer &lt;JWT&gt;（mcp.enterprise.a2a.jwt-secret 非空，RFC 6750，与 mcp-auth 令牌互通）
 * 签名（V1.18）：card-signing-key 非空时，agent-card 返回 {agentCard, signature} 信封 + X-Agent-Card-Signature 头
 * （A2A v1.2 供应链安全基线：签名 Agent Card，防 DNS 劫持 / 中间人篡改能力发现）
 * 说明：本控制器以 @Bean 方式注册（AutoConfiguration），Spring MVC 可自动发现，
 * 无需应用方调整 @ComponentScan。
 */
@RestController
public class A2aRpcController {

    private static final Logger log = LoggerFactory.getLogger(A2aRpcController.class);

    private final A2aBridgeService bridgeService;
    private final McpA2aProperties properties;
    private final A2aJwtTokenValidator jwtValidator;
    private final A2aAgentCardSigner cardSigner;

    public A2aRpcController(A2aBridgeService bridgeService, McpA2aProperties properties) {
        this(bridgeService, properties, null, null);
    }

    public A2aRpcController(A2aBridgeService bridgeService, McpA2aProperties properties,
                            A2aJwtTokenValidator jwtValidator) {
        this(bridgeService, properties, jwtValidator, null);
    }

    public A2aRpcController(A2aBridgeService bridgeService, McpA2aProperties properties,
                            A2aJwtTokenValidator jwtValidator, A2aAgentCardSigner cardSigner) {
        this.bridgeService = bridgeService;
        this.properties = properties;
        this.jwtValidator = jwtValidator;
        this.cardSigner = cardSigner;
    }

    // ===== Agent Card =====

    @GetMapping(path = "${mcp.enterprise.a2a.base-path:/a2a}/agent-card", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Object agentCard(HttpServletRequest request, HttpServletResponse response) {
        if (!authorized(request)) {
            return unauthorized();
        }
        String baseUrl = request.getRequestURL().toString().replace("/agent-card", "");
        return maybeSign(bridgeService.buildAgentCard(baseUrl), response);
    }

    /**
     * A2A 协议标准发现路径别名：允许 A2A 客户端默认请求 /.well-known/agent-card.json
     */
    @GetMapping(path = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Object agentCardWellKnown(HttpServletRequest request, HttpServletResponse response) {
        if (!authorized(request)) {
            return unauthorized();
        }
        String baseUrl = request.getRequestURL().toString().replace("/.well-known/agent-card.json", "");
        return maybeSign(bridgeService.buildAgentCard(baseUrl), response);
    }

    /**
     * V1.18: 自验证 Agent Card 签名（demo + 巡检脚本输出）。
     * 对当前生成的 Agent Card 签名后再验证，输出结果（valid / algorithm / keyId / signedAt）。
     */
    @GetMapping(path = "${mcp.enterprise.a2a.base-path:/a2a}/agent-card/verify", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Object agentCardVerify(HttpServletRequest request) {
        if (!authorized(request)) {
            return unauthorized();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (cardSigner == null || !properties.isSignedCardEnabled()) {
            result.put("valid", false);
            result.put("error", "Signed Agent Card disabled (set mcp.enterprise.a2a.card-signing-key)");
            return result;
        }
        try {
            String baseUrl = request.getRequestURL().toString().replace("/agent-card/verify", "");
            Object card = bridgeService.buildAgentCard(baseUrl);
            SignedAgentCard signed = cardSigner.sign((A2aAgentCard) card);
            A2aAgentCardSigner.VerificationResult verified = cardSigner.verify(signed.signature());
            result.put("valid", verified.valid());
            result.put("algorithm", signed.algorithm());
            result.put("keyId", signed.keyId());
            result.put("signedAt", signed.signedAt());
            if (!verified.valid()) {
                result.put("error", verified.error());
            }
        } catch (Exception e) {
            log.warn("Agent Card 自验证失败: {}", e.getMessage());
            result.put("valid", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * V1.18: 签名后响应升级——
     * - 未启用签名（cardSigner 为 null）：直接返回原始 Agent Card
     * - 启用签名：返回 SignedAgentCard 信封 + X-Agent-Card-Signature 响应头
     * - 签名异常（防御性）：退回原始卡片，不影响服务可用性
     */
    private Object maybeSign(Object card, HttpServletResponse response) {
        if (cardSigner == null || !(card instanceof A2aAgentCard agentCard)) {
            return card;
        }
        try {
            SignedAgentCard signed = cardSigner.sign(agentCard);
            response.setHeader("X-Agent-Card-Signature", signed.signature());
            return signed;
        } catch (Exception e) {
            log.warn("Agent Card 签名失败，退回原始卡片: {}", e.getMessage());
            return card;
        }
    }

    // ===== JSON-RPC =====

    @PostMapping(path = "${mcp.enterprise.a2a.base-path:/a2a}/rpc", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> rpc(@RequestBody(required = false) Map<String, Object> body,
                                      HttpServletRequest request) {
        if (!authorized(request)) {
            Map<String, Object> errBody = new LinkedHashMap<>();
            errBody.put("jsonrpc", "2.0");
            errBody.put("id", null);
            errBody.put("error", Map.of("code", -32009, "message", authErrorMessage()));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errBody);
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
                        .data(Map.of("code", -32009, "message", authErrorMessage())));
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
        health.put("authMode", properties.resolvedAuthMode());
        health.put("signedCard", properties.isSignedCardEnabled());
        if (properties.isSignedCardEnabled()) {
            health.put("cardKeyId", properties.getCardKeyId());
        }
        return health;
    }

    // ===== 私有 =====

    /**
     * V1.17: 按推导的鉴权模式校验请求：
     * - none    : 放行（可置于网关 API Key 之后）
     * - api-key : 校验 X-A2A-Key 请求头
     * - oauth2  : 校验 Authorization: Bearer &lt;JWT&gt;（RFC 6750，与 mcp-auth 令牌互通）
     */
    private boolean authorized(HttpServletRequest request) {
        String mode = properties.resolvedAuthMode();
        if ("none".equals(mode)) {
            return true;
        }
        if ("oauth2".equals(mode)) {
            if (jwtValidator == null) {
                log.warn("🚫 A2A authMode=oauth2 但未注入 A2aJwtTokenValidator（需配置 mcp.enterprise.a2a.jwt-secret）");
                return false;
            }
            String token = A2aJwtTokenValidator.extractBearerToken(request.getHeader("Authorization"));
            if (token == null) {
                log.warn("🚫 A2A OAuth2 鉴权失败: 缺少 Authorization: Bearer 头 from {}", request.getRemoteAddr());
                return false;
            }
            String subject = jwtValidator.validate(token);
            if (subject == null) {
                return false;
            }
            request.setAttribute("a2a.subject", subject);
            return true;
        }
        // api-key 模式（默认）
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

    /** 按当前鉴权模式给出错误提示（401 响应体 / SSE error 事件共用） */
    private String authErrorMessage() {
        return "oauth2".equals(properties.resolvedAuthMode())
                ? "Authentication required (Authorization: Bearer <JWT> - RFC 6750)"
                : "Authentication required (X-A2A-Key)";
    }

    private ResponseEntity<Object> invalidRequest(Object id, String message) {
        return ResponseEntity.badRequest().body(Map.of(
                "jsonrpc", "2.0", "id", id,
                "error", Map.of("code", -32600, "message", message)
        ));
    }

    private ResponseEntity<Object> unauthorized() {
        Map<String, Object> errBody = new LinkedHashMap<>();
        errBody.put("jsonrpc", "2.0");
        errBody.put("id", null);
        errBody.put("error", Map.of("code", -32009, "message", authErrorMessage()));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errBody);
    }
}