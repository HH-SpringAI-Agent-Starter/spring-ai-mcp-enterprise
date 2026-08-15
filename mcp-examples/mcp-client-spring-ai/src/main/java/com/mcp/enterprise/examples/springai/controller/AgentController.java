package com.mcp.enterprise.examples.springai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Spring AI Agent 示例控制器
 *
 * 通过 ChatClient + MCP 工具回调，让大模型自动调用 MCP Enterprise Server 的工具。
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final ChatClient chatClient;

    public AgentController(ChatClient.Builder chatClientBuilder,
                           List<ToolCallback> mcpTools) {
        this.chatClient = chatClientBuilder
                .defaultTools(mcpTools)
                .build();
    }

    /**
     * 自然语言调用 MCP 工具
     */
    @PostMapping("/ask")
    public String ask(@RequestBody String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}
