package com.mcp.integration.alibaba;

/**
 * Spring AI Alibaba MCP 集成自动配置
 *
 * 本模块提供 MCP Enterprise Server 与 Spring AI Alibaba (DashScope/通义千问) 的原生集成。
 *
 * 自动注册以下功能:
 * 1. Alibaba Auto-Configuration — 自动配置 DashScope 客户端（从环境变量/配置读取 API Key）
 * 2. MCP Server Endpoint — 暴露 MCP 端点让 DashScope 通义千问 Agent 调用
 * 3. ChatClient 自动集成 — 让 Spring AI ChatClient 自动发现 MCP 工具
 * 4. Alibaba Cloud 部署探测 — 自动检测是否在阿里云 ECS/ACK 环境
 */
public class AlibabaIntegrationPackage {
    // marker class for package scanning
}
