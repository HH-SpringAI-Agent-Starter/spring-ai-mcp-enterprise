package com.mcp.integration.alibaba;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP Alibaba 集成核心 — 管理 DashScope MCP 客户端生命周期
 *
 * 功能：
 * 1. 连接 MCP Enterprise Server（自动发现工具）
 * 2. 将 MCP 工具注册到 Spring AI Tool Context
 * 3. 支持 DashScope 通义千问模型自动调用 MCP 工具
 * 4. 定期健康检查断线重连
 * 5. 提供工具调用桥接（通过 REST API 调用 MCP Server 暴露的工具）
 *
 * 架构：
 * ┌─────────────────┐     MCP Protocol      ┌──────────────────┐
 * │  DashScope AI   │ ◄──── (SSE/REST) ──── │ MCP Enterprise   │
 * │  (通义千问)      │                       │ Server            │
 * │  调用工具时      │                       │ (Spring Boot)     │
 * └────────┬────────┘                       └──────────────────┘
 *          │
 *          ▼
 * ┌─────────────────────────────────────┐
 * │ McpAlibabaIntegration               │
 * │ - 自动连接 MCP Server               │
 * │ - 工具发现 + 缓存                   │
 * │ - 调用桥接（REST 代理）             │
 * └─────────────────────────────────────┘
 */
public class McpAlibabaIntegration {

    private static final Logger log = LoggerFactory.getLogger(McpAlibabaIntegration.class);

    private final McpAlibabaProperties properties;
    private final RestTemplate restTemplate;

    /** 连接状态 */
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /** 已发现的工具缓存 */
    private List<Map<String, Object>> discoveredTools = List.of();

    /** 工具元数据（名称 -> 定义） */
    private final Map<String, Map<String, Object>> toolDefinitions = new ConcurrentHashMap<>();

    /** 连接尝试次数 */
    private int retryCount = 0;

    public McpAlibabaIntegration(McpAlibabaProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        log.info("☁️ Spring AI Alibaba 集成初始化完成");
        log.info("   - 聊天模型: {}", properties.getChatModel());
        log.info("   - Embedding: {}", properties.getEmbeddingModel());
        log.info("   - MCP Server: http://localhost:{}/api/mcp", properties.getServerPort());
        log.info("   - MCP 客户端自动连接: {}", properties.isMcpClientAutoConnect());
        log.info("   - 环境检测: {}", properties.isAutoDetectCloud() ? "已启用" : "已禁用");

        if (properties.isAutoDetectCloud()) {
            detectAlibabaCloud();
        }
    }

    // ===== 连接管理 =====

    /**
     * 连接到 MCP Enterprise Server
     */
    public boolean connect() {
        if (connected.get()) {
            log.debug("MCP 客户端已连接，跳过");
            return true;
        }

        String healthUrl = getBaseUrl() + "/api/mcp/health";
        try {
            var health = restTemplate.getForObject(healthUrl, Map.class);
            if (health != null && "UP".equals(health.get("status"))) {
                connected.set(true);
                retryCount = 0;
                log.info("✅ MCP 客户端已连接到 Server (端口: {})", properties.getServerPort());

                // 连接后立即发现工具
                discoverTools();

                if (properties.isAutoDetectCloud()) {
                    detectAlibabaCloud();
                }

                return true;
            }
        } catch (Exception e) {
            retryCount++;
            log.warn("❌ MCP 客户端连接失败 (尝试 {}): {}", retryCount, e.getMessage());
        }
        return false;
    }

    /**
     * 断开与 MCP Server 的连接
     */
    public void disconnect() {
        if (!connected.get()) {
            return;
        }
        connected.set(false);
        discoveredTools = List.of();
        toolDefinitions.clear();
        log.info("👋 MCP 客户端已断开连接");
    }

    /**
     * 重连
     */
    public boolean reconnect() {
        disconnect();
        return connect();
    }

    // ===== 工具发现 =====

    /**
     * 从 MCP Server 发现并缓存工具列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> discoverTools() {
        if (!connected.get()) {
            log.warn("MCP 客户端未连接，无法发现工具");
            return List.of();
        }

        try {
            String toolsUrl = getBaseUrl() + "/api/mcp/tools";
            var response = restTemplate.getForObject(toolsUrl, Map.class);
            if (response != null) {
                Object toolsObj = response.get("tools");
                if (toolsObj instanceof List) {
                    discoveredTools = (List<Map<String, Object>>) toolsObj;
                    // 构建名称 -> 定义映射
                    toolDefinitions.clear();
                    for (var tool : discoveredTools) {
                        String name = (String) tool.get("name");
                        if (name != null) {
                            toolDefinitions.put(name, tool);
                        }
                    }
                    log.info("🔧 已发现 {} 个 MCP 工具", discoveredTools.size());
                    for (var tool : discoveredTools) {
                        log.info("   - {}: {}", tool.get("name"), tool.get("description"));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("工具发现失败: {}", e.getMessage());
        }

        return discoveredTools;
    }

    /**
     * 获取已发现的工具列表
     */
    public List<Map<String, Object>> getDiscoveredTools() {
        return discoveredTools;
    }

    // ===== 工具调用桥接 =====

    /**
     * 通过 REST API 调用 MCP Server 上的工具
     * 这是 DashScope AI 调用 MCP 工具的桥梁
     *
     * @param toolName 工具名称
     * @param params   参数
     * @return 调用结果
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> invokeTool(String toolName, Map<String, Object> params) {
        // 检查工具是否存在
        if (!toolDefinitions.containsKey(toolName)) {
            return Map.of("success", false, "error", "未知工具: " + toolName);
        }

        // 检查是否已连接
        if (!connected.get() && !connect()) {
            return Map.of("success", false, "error", "MCP Server 未连接");
        }

        try {
            String invokeUrl = getBaseUrl() + "/api/mcp/tools/" + toolName + "/invoke";
            var result = restTemplate.postForObject(invokeUrl, params, Map.class);
            return result != null ? result : Map.of("success", false, "error", "空响应");
        } catch (Exception e) {
            log.error("工具调用失败: {} - {}", toolName, e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 批量调用多个工具
     */
    public List<Map<String, Object>> invokeTools(Map<String, Map<String, Object>> toolCalls) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (var entry : toolCalls.entrySet()) {
            Map<String, Object> result = invokeTool(entry.getKey(), entry.getValue());
            result.put("_toolName", entry.getKey());
            results.add(result);
        }
        return results;
    }

    // ===== 工具适配 =====

    /**
     * 将已发现工具转换为 Spring AI Tool 格式
     * 供 DashScope ChatClient 的 Tool Context 使用
     */
    public List<Map<String, Object>> getToolDefinitionsForSpringAi() {
        return discoveredTools.stream()
                .map(tool -> {
                    Map<String, Object> springAiTool = new LinkedHashMap<>();
                    springAiTool.put("name", tool.get("name"));
                    springAiTool.put("description", tool.get("description"));

                    // 转换参数 schema
                    Map<String, Object> inputSchema = new LinkedHashMap<>();
                    inputSchema.put("type", "object");
                    Object props = tool.get("inputSchema");
                    if (props instanceof Map) {
                        inputSchema.put("properties", props);
                    } else {
                        inputSchema.put("properties", Map.of());
                    }
                    springAiTool.put("inputSchema", inputSchema);

                    return springAiTool;
                })
                .toList();
    }

    // ===== 状态检查 =====

    public boolean isConnected() {
        return connected.get();
    }

    public int getToolCount() {
        return discoveredTools.size();
    }

    public McpAlibabaProperties getProperties() {
        return properties;
    }

    public int getRetryCount() {
        return retryCount;
    }

    // ===== 私有方法 =====

    private String getBaseUrl() {
        return "http://localhost:" + properties.getServerPort();
    }

    private void detectAlibabaCloud() {
        // 检测是否在阿里云 ECS 环境
        String region = System.getenv("ALIBABA_CLOUD_REGION_ID");
        if (region != null && !region.isEmpty()) {
            log.info("🏗️ 检测到阿里云环境: region={}", region);
        }

        // 检测 K8s 环境
        String k8sSvc = System.getenv("KUBERNETES_SERVICE_HOST");
        if (k8sSvc != null && !k8sSvc.isEmpty()) {
            log.info("☸️ 检测到 Kubernetes 环境: {}", k8sSvc);
        }
    }
}
