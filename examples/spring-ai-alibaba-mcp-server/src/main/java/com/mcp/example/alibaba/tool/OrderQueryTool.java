package com.mcp.example.alibaba.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 示例业务工具：用 Spring AI Alibaba 的 {@code @Tool} 把普通 Java 方法
 * 发布成可被 Agent 调用的 MCP 工具。
 *
 * <p>这是"存量 Spring Bean → MCP 工具"的零改造范式：业务代码不依赖 MCP，
 * 仅加注解即被框架自动发现并暴露。对齐 Spring AI Alibaba 1.0 GA 的
 * 零代码改造发布能力（Spring Cloud / Dubbo 接口同理）。</p>
 */
@Service
public class OrderQueryTool {

    /** 根据订单号查询订单详情。 */
    @Tool(description = "根据订单号查询订单详情（示例 MCP 工具）")
    public Order getOrder(@ToolParam(description = "订单号") String orderId) {
        // 真实场景：orderRepository.findById(orderId)
        return new Order(orderId, "示例订单-" + orderId, 299.0);
    }

    /** 列出最近 N 笔订单。 */
    @Tool(description = "列出最近 N 笔订单")
    public List<Order> listOrders(@ToolParam(description = "返回数量，1-50") int limit) {
        int n = Math.max(1, Math.min(50, limit));
        return List.of(
                new Order("ORD-1001", "键盘", 199.0),
                new Order("ORD-1002", "鼠标", 99.0)
        ).subList(0, Math.min(n, 2));
    }

    /** 简单的领域对象。 */
    public record Order(String orderId, String name, Double amount) {
    }
}
