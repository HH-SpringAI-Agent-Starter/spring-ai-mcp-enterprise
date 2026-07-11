package com.mcp.enterprise.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * MCP Enterprise Server
 *
 * 企业级 MCP (Model Context Protocol) Server
 * 基于 Spring Boot 3.4 + Spring AI MCP
 *
 * 一键启动 MCP 服务平台，提供工具注册、安全管理、监控审计
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.mcp.enterprise.server",
    "com.mcp.enterprise.autoconfigure",
    "com.mcp.enterprise.core",
    "com.mcp.tool"
})
public class McpEnterpriseApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpEnterpriseApplication.class, args);
    }
}
