package com.mcp.example.alibaba.config;

import com.mcp.example.alibaba.tool.OrderQueryTool;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 把 {@link OrderQueryTool} 注册为 MCP 工具回调。
 *
 * <p>{@code MethodToolCallback} 会扫描 bean 上所有 {@code @Tool} 标注的方法，
 * 由 {@code spring-ai-mcp-server-spring-boot-starter} 自动暴露为 MCP 工具。
 * 多个工具类时，往这个 List 追加即可。</p>
 */
@Configuration
public class ToolConfig {

    @Bean
    public List<MethodToolCallback> orderToolCallbacks(OrderQueryTool orderQueryTool) {
        return List.of(MethodToolCallback.builder()
                .toolObject(orderQueryTool)
                .build());
    }
}
