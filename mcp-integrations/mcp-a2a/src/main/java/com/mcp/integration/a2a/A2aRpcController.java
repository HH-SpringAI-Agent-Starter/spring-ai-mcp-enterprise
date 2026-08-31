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

import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A2A JSON-RPC HTTP 端点
 *
 * 路由（basePath 默认 /a2a）：
 * - GET  {base}/agent-card  → Agent Card（技能列表派生自 MCP 工具注册中心）
 * - POST {base}/rpc         → A2A JSON-RPC 2.0 分派（message/send、task/send、task/get、task/cancel、agent/quote）
 * - GET  {base}/health      → 存活检查
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
     * A2A 协议标准发现路径别名：许多 A2A 客户端默认请求 /.well-known/agent-card.json
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
        return health;
    }

    // ===== 私有 =====

    private boolean authorized(HttpServletRequest request) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return true; // 未配置 api-key：不启用 A2A 网关层鉴权（可置于网关/API Key 之后）
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