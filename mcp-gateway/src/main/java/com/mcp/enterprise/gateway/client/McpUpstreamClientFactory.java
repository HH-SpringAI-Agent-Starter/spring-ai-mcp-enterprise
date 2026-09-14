package com.mcp.enterprise.gateway.client;

import com.mcp.enterprise.gateway.McpGatewayProperties;
import com.mcp.enterprise.gateway.UpstreamMcpClient;

/**
 * V1.25 上游客户端工厂 SPI。
 *
 * <p>默认为 {@link StreamableHttpUpstreamClient.Factory}（Streamable HTTP / JSON-RPC）；
 * 消费方可提供自定义实现（如私有协议、SSE 旧传输、内部 Kafka 桥）替换。</p>
 */
public interface McpUpstreamClientFactory {

    /** 按上游配置创建客户端实例（每个上游一个） */
    UpstreamMcpClient create(McpGatewayProperties.Upstream config);
}