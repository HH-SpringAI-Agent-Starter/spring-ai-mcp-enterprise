package com.mcp.example.alibaba;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI Alibaba 1.0 GA MCP Server 示例入口。
 *
 * <p>启动后，本应用会把 {@code OrderQueryTool} 中用 {@code @Tool} 标注的方法
 * 自动发布为 MCP 工具，并通过 Streamable HTTP 端点暴露（默认 /mcp）。
 * 把这个端点挂到 Spring AI MCP Enterprise 的 {@code mcp-gateway} 后即可获得
 * RBAC / 审计 / HITL / 限流等全部企业治理能力。</p>
 */
@SpringBootApplication
public class SpringAiAlibabaMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiAlibabaMcpServerApplication.class, args);
    }
}
