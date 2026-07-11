package com.mcp.enterprise.core.tool;

import com.mcp.enterprise.core.model.ToolDefinition;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * MCP 工具执行器 SPI 接口
 *
 * 所有 MCP 工具（数据库/搜索/系统/自定义）都实现此接口。
 * 框架通过 SPI 自动发现并注册所有实现了此接口的 Bean。
 *
 * @param <T> 工具返回结果类型
 */
public interface McpToolExecutor {

    /**
     * 获取工具定义（名称、描述、权限、参数 Schema 等）
     */
    ToolDefinition getDefinition();

    /**
     * 执行工具调用
     *
     * @param params 调用参数
     * @return 执行结果
     */
    Mono<Map<String, Object>> execute(Map<String, Object> params);

    /**
     * 健康检查
     */
    default Mono<Boolean> healthCheck() {
        return Mono.just(true);
    }

    /**
     * 获取执行统计
     */
    default Map<String, Object> getStats() {
        return Map.of("executor", getDefinition().getName(), "status", "ready");
    }
}
