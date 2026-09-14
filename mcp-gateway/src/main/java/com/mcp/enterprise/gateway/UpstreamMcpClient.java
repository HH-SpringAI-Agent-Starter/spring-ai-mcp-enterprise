package com.mcp.enterprise.gateway;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * V1.25 上游 MCP Server 客户端 SPI。
 *
 * <p>每个上游对应一个客户端实例。实现应遵循 MCP 2026-07-28 Streamable HTTP
 * 语义：{@code ping()} 做 initialize 握手；{@code listTools()}/{@code callTool()}
 * 在无状态传输上先 initialize 再发目标请求（兼容需要会话初始化的下游）。</p>
 */
public interface UpstreamMcpClient {

    /** 上游唯一名（与配置一致） */
    String name();

    /**
     * initialize 握手探测。
     *
     * @return true = 协议握手成功，上游可服务
     */
    Mono<Boolean> ping();

    /** 拉取上游全部工具（tools/list → 裁剪描述列表） */
    Mono<List<RemoteToolDescriptor>> listTools();

    /**
     * 调用上游工具（tools/call）。
     *
     * <p>返回的 Map 至少包含：{@code success}（布尔）、{@code result}（成功负载）、
     * {@code error}（失败原因）、{@code raw}（原始 content 数组）。</p>
     *
     * @param toolName  远程工具名（不加前缀）
     * @param arguments 调用参数
     */
    Mono<Map<String, Object>> callTool(String toolName, Map<String, Object> arguments);
}