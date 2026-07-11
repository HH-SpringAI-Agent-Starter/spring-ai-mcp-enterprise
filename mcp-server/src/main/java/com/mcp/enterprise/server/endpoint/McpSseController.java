package com.mcp.enterprise.server.endpoint;

import com.mcp.enterprise.core.endpoint.McpSseEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP SSE (Server-Sent Events) Web 控制器
 *
 * 符合标准 MCP 协议，支持 SSE 传输。
 * 核心消息处理委托给 McpSseEndpoint 核心类。
 */
@RestController
@RequestMapping("/api/mcp")
public class McpSseController {

    private static final Logger log = LoggerFactory.getLogger(McpSseController.class);

    private final McpSseEndpoint mcpSseEndpoint;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public McpSseController(McpSseEndpoint mcpSseEndpoint) {
        this.mcpSseEndpoint = mcpSseEndpoint;
    }

    /**
     * MCP 协议能力声明
     */
    @GetMapping("")
    public Map<String, Object> getCapabilities() {
        return McpSseEndpoint.SERVER_CAPABILITIES;
    }

    /**
     * SSE 连接端点
     */
    @GetMapping("/sse")
    public SseEmitter connectSse() {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L);

        emitters.put(sessionId, emitter);

        try {
            emitter.send(SseEmitter.event()
                    .id(sessionId)
                    .name("endpoint")
                    .data("/api/mcp/messages?sessionId=" + sessionId, MediaType.TEXT_PLAIN));

            emitter.send(SseEmitter.event()
                    .id(sessionId + "-cap")
                    .name("capabilities")
                    .data(McpSseEndpoint.SERVER_CAPABILITIES, MediaType.APPLICATION_JSON));

            emitter.onCompletion(() -> {
                emitters.remove(sessionId);
                log.debug("SSE 连接关闭: {}", sessionId);
            });
            emitter.onTimeout(() -> {
                emitters.remove(sessionId);
                log.debug("SSE 连接超时: {}", sessionId);
            });

            log.info("SSE 连接建立: {} (当前活跃: {})", sessionId, emitters.size());
        } catch (IOException e) {
            log.error("SSE 初始事件发送失败: {}", e.getMessage());
            emitters.remove(sessionId);
            return null;
        }

        return emitter;
    }

    /**
     * MCP JSON-RPC 消息端点
     */
    @PostMapping("/messages")
    public Map<String, Object> handleMessage(@RequestParam(required = false) String sessionId,
                                              @RequestBody Map<String, Object> message) {
        return mcpSseEndpoint.handleMessage(message);
    }
}
