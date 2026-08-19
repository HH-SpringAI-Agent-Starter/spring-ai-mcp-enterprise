package com.mcp.enterprise.examples.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.client.RestTemplate;

/**
 * Spring AI MCP Client 示例应用
 *
 * 演示如何通过 Spring AI MCP Client SDK 调用 MCP Enterprise Server。
 *
 * 启动前请确保 MCP Enterprise Server 已在 localhost:8081 运行。
 *
 * 启动方式:
 *   export MCP_API_KEY=default-admin-key
 *   mvn spring-boot:run -pl mcp-examples/mcp-client-spring-ai
 *
 * 或直接运行此类:
 *   java -jar mcp-client-spring-ai.jar
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.mcp.enterprise.examples.client",
        "com.mcp.enterprise.examples.springai"   // 工具配置 + Agent 控制器
})
public class McpSpringAiClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpSpringAiClientApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
