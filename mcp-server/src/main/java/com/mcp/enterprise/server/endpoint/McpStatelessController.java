package com.mcp.enterprise.server.endpoint;

import com.mcp.enterprise.core.endpoint.McpStatelessEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MCP 无状态 (Stateless) Web 控制器
 * <p>
 * 实现 2026-07-28 无状态核心协议，适用于 Kubernetes/Cloud Run 弹性伸缩。
 * 无需 SSE 长连接，每次请求独立处理。
 * <p>
 * 兼容模式：自动检测客户端协议版本，降级到 2025-03-26 SSE 端点。
 */
@RestController
@RequestMapping("/api/mcp/v2")
public class McpStatelessController {

    private static final Logger log = LoggerFactory.getLogger(McpStatelessController.class);

    private final McpStatelessEndpoint statelessEndpoint;

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
     */
    @PostMapping("/message")
    public Map<String, Object> handleStatelessMessage(
            @RequestBody Map<String, Object> message,
            @RequestHeader(value = "X-MCP-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        log.debug("MCP Stateless message: method={}, traceId={}",
                message != null ? message.get("method") : null, traceId);

        return statelessEndpoint.handleStatelessMessage(message, traceId);
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
     * 健康检查端点 (无状态模式)
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "version", "0.0.2",
                "protocol", "2026-07-28",
                "mode", "stateless"
        );
    }
}
