# 你的 Spring AI @Tool 工具，如何一夜之间变成企业级 MCP 资产？

> 掘金 / CSDN 发布稿 · 2026-09-12 · 作者：spring-ai-mcp-enterprise

## 先讲一个每天都在发生的场景

你所在的公司是 Spring 技术栈，最近 AI 化改造很激进。你带着团队用 Spring AI / Spring AI Alibaba 写了一批 `@Tool` 工具：查订单、算财务指标、查天气、调内部系统……模型调用得好好的，Agent 也能干活了。

然后 CTO 说：**「这些能力，能不能让 Claude / ChatGPT / 我们的自研 Agent 平台也能用？」**

于是你面临一个经典问题：工具是 Spring AI 的，消费方是 MCP 的。两者之间隔着一整层「企业级化」的活：

- MCP 端点（SSE / Streamable HTTP）谁来写？
- 工具注册表、发现、版本管理谁来管？
- API Key / OAuth2 鉴权谁来接？工具级权限怎么控制？
- 限流、熔断、审计日志——AI 调用可比人调用猛多了，没有这些根本不敢上生产。
- Token Scope 怎么映射到工具权限（RFC 6750）？

如果这些都要从零写，两个星期起步，而且大概率写得不如专业框架。

## 桥梁的两种方向

在 MCP 与 Spring AI 之间，其实有两条路，方向完全相反，很多人会搞混：

**方向 A：MCP Server 的工具 → 注册进 Spring AI（让千问/OpenAI 能调）**

这是「让 LLM 调用你的 MCP 工具」。Spring AI Alibaba 的集成模块干的就是这个：连上企业 MCP Server，自动发现工具，注册进 Tool Context，DashScope 千问模型在对话里就能直接调用。

**方向 B：Spring AI 的 @Tool 工具 → 注册进企业 MCP Server（让任何 MCP 客户端能调）**

这是「把你已有的工具资产暴露给 MCP 世界」。本文主角。

方向 A 解决「我的 Agent 需要更多工具」，方向 B 解决「我的工具需要更多消费方」。一个企业 AI 平台，两条路都得通。

## 方向 B 的零改造方案

核心思路：**扫描 Spring 容器里所有 Spring AI 工具 → 包装成企业 MCP 工具执行器 → 批量注册进企业注册中心**。之后发生的一切——RBAC、限流、审计、Scope、健康检查、MCP 端点——全部由框架接管。

三类工具源全覆盖：

1. **`@Tool` 注解方法 Bean**——Spring AI 最主流的写法。Spring 容器里一个 Bean 上有 `@Tool` 方法，自动转成 `MethodToolCallback`。
2. **`ToolCallback` Bean**——直接注册的工具回调。
3. **`ToolCallbackProvider` Bean**——工具回调提供者，连 MCP Client 动态发现的远端工具都能二次暴露。

同名按最终注册名去重，多来源冲突打 WARN 而不是静默覆盖——这在企业环境里很重要，工具重名导致的“静默失效”是生产事故高发点。

## 一段代码看懂适配器

Spring AI 的工具回调（`ToolCallback`）长这样：

```java
public interface ToolCallback extends FunctionCallback {
    ToolDefinition getToolDefinition();  // name / description / inputSchema(JSON)
    String call(String input);           // 入参是 JSON 字符串，出参也是 JSON 字符串
}
```

企业 MCP 工具执行器（`McpToolExecutor`）长这样：

```java
public interface McpToolExecutor {
    ToolDefinition getDefinition();     // 企业版：多出 category/roles/scopes/限流/超时
    Mono<Map<String, Object>> execute(Map<String, Object> params);
}
```

适配器做的事极其直白：

- **定义侧**：`ToolDefinition` → 企业 `ToolDefinition`，JSON Schema 字符串解析成结构，再补上分类、角色、Scope、超时、限流。
- **执行侧**：`Map` 参数序列化成 JSON → `callback.call(json)` → 返回的 JSON 字符串解析回结构。

真正有意思的坑在这里：**Spring AI 对 `String` 返回类型会做 JSON 双重编码**。你的工具方法返回 `{"city":"北京","temp":22}`，Spring AI 会把它当字符串再 JSON 编码一层，变成 `"{\"city\":\"北京\",\"temp\":22}"`。适配器老老实实 `readValue` 成 `Map` 就会拿到一个 String，然后 ClassCastException 教你做人。正确做法是 `readTree` 归一化：文本节点先尝试二次解析，Object/Array 节点直接转换，解析不动就原文透传。我们的第一个版本就是被自己的单测抓住这个 bug 的——**这就是为什么新模块必须带测试**。

## 为什么这件事值钱

看几组数据（2026 年 9 月）：

- MCP SDK 月下载量 **9700 万+**，注册服务器 **1 万+**，但**只有不到 5% 实现了商业化**；
- Upwork 上 MCP 开发者时薪 **$130–185**（资深档），MCP Server 单项目 **$1000–3000** 起；
- Gartner 预计 **2026 年 75% 的 API 网关厂商会集成 MCP 能力**；
- 沃尔玛中国在招「AI/MCP 网关」平台工程师，**¥30K–55K/月**，要求是鉴权、限流、熔断、灰度、Skill/Spec Registry——这是一个企业级 MCP 框架能力的完整画像。

也就是说：**「企业级 MCP 平台」已经从概念变成招聘 JD 和采购清单里的硬需求**，而绝大多数 Java 团队手里只有一堆孤立的 `@Tool` 方法，中间缺的正是这座桥。

## 一套工具，双入口

把两个方向的集成模块都引进来，你的工具资产就双向打通了：

```
                    ┌──────────────────────────┐
                    │   你的 Spring AI 工具      │
                    │   （@Tool / ToolCallback） │
                    └───────────┬──────────────┘
                                │
                ┌───────────────┴───────────────┐
                ▼                               ▼
    ┌───────────────────────┐      ┌───────────────────────┐
    │ mcp-springai-tools     │      │ mcp-alibaba            │
    │ @Tool → 企业 MCP 工具   │      │ 企业 MCP 工具 → 千问     │
    │ （本文，方向 B）         │      │ （方向 A）              │
    └───────────────────────┘      └───────────────────────┘
                │                               │
                ▼                               ▼
    任何 MCP 客户端（Claude/        DashScope 千问 / Spring AI
    ChatGPT/自研 Agent）             Agent 直接调用
```

业务代码一行不改。你写工具的方式不变，工具的安全与治理水平直接拉满。

## 总结

| 你关心的 | 答案 |
|---|---|
| 我的 `@Tool` 工具要改代码吗？ | 不改，扫描即注册 |
| 鉴权限流审计谁管？ | 企业框架全接管 |
| 多久能跑通？ | 加一个依赖，启动即可 |
| 和 Alibaba 集成冲突吗？ | 不冲突，互为补充 |
| 有没有测试？ | 15 个单测，含双重编码、缺参、去重等边界 |

> 开源项目：[HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise)
> 中文文档：`docs/springai-tools-integration-guide.md`