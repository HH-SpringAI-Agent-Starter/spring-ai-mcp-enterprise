package com.mcp.enterprise.examples.springai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI MCP Client 示例启动类
 *
 * 演示基于 Spring AI Alibaba (DashScope) 调用 MCP Enterprise Server 的工具能力。
 */
@SpringBootApplication
public class McpSpringAiClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpSpringAiClientApplication.class, args);
    }
}
