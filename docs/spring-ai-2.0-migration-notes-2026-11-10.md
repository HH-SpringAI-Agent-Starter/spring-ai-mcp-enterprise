# Spring AI 2.0 GA · MCP 兼容性迁移对照（2026-11-10）

> **定位**：回应任务清单第 1 项（Spring AI / Spring AI Alibaba 兼容），把第三方实测到的 GA breaking changes 固化成本框架的兼容声明与验收清单。
> **依据**：[dev.to AWS Heroes Part 4](https://bizarro.dev.to/aws-heroes/building-ai-agents-with-spring-ai-and-amazon-bedrock-agentcore-part-4-provide-mcp-tools-for-2odf) / [Part 6](https://dev.to/aws-heroes/building-ai-agents-with-spring-ai-and-amazon-bedrock-agentcore-part-6-deploy-mcp-client-on-d4d)（示例已升级到 Spring AI 2.0 + Spring Boot 4.1 GA）、[Spring AI vs LangChain4j 2026 横评](https://dev.to/jamilxt/spring-ai-vs-langchain4j-which-java-ai-framework-should-you-choose-in-2026-4mop)、[Spring 生态 22 周年综述](http://www.lpqz.cn/news/61814)。
> ⚠️ **本文是迁移对照与影响面分析，不是「已完成升级」的声明**：本框架尚未在本机执行 Spring AI 2.0 依赖升级与全量构建，升级须按第 4 节验收后另行发布。

---

## 0. 为什么现在要写这个

11-10 的第三方实测确认 **Spring AI 2.0 已 GA**（配套 Spring Boot 4.1）。Spring AI 2.0 把 MCP 当作**一等集成点**：官方 MCP Java SDK 2.0.0、`@McpServer` / `@McpTool` / `@McpParam` 并入核心、Streamable HTTP 为默认 HTTP 传输、Micrometer + Actuator 内建可观测；横评结论是「若 MCP 是核心集成模式，Spring AI 2.0 在 Java 生态明显领先，LangChain4j 需手工接线」。

这直接支撑本框架从 11-01 起固化的定位：

> **核心治理层基于官方 SDK；Spring AI Alibaba（`mcp-integrations/mcp-alibaba`）只是【可选兼容层】，不进主 reactor。**

好处在 GA 这天体现得很清楚：上游的 MCP **传输层 / 客户端 API** breaking changes 不会把 21 个治理模块一起掀翻。但**兼容声明必须跟着更新**，否则对用户就是误导。

---

## 1. 五个 breaking changes（MCP 客户端侧）

| # | 项 | Spring AI 1.x | **Spring AI 2.0 GA** | 影响面 |
| --- | --- | --- | --- | --- |
| 1 | HTTP Streamable 传输类 | `WebClientStreamableHttpTransport`（Spring WebFlux `WebClient`） | **`HttpClientStreamableHttpTransport`**，基于 `java.net.http.HttpRequest.Builder` | `mcp-integrations`、[`examples/client-java/`](../examples/client-java) |
| 2 | MCP endpoint 配置 | 单个 URL | **必须拆 `base.url` + `endpoint`（如 `/mcp`）两段** | 所有示例 `application.yml` / `application.properties` |
| 3 | ChatClient 默认选项 | `defaultOptions(ChatOptions)` | **`defaultOptions(ChatOptions.Builder)`**（传 builder，不传实例） | `examples/client-java`、`examples/spring-ai-alibaba-mcp-server` |
| 4 | 工具挂载 | `.tools(new DateTimeTools())` + `.toolCallbacks(provider.getToolCallbacks())` | **`.tools(localTools, callbacks)` 一次传入** | 同上 |
| 5 | 额外 Bean | 不需要 | **须显式定义 `ToolCallingManager` bean** | 同上 |

### 迁移前后最小对照（摘自第三方实测）

```java
// Spring AI 1.x
var transport = WebClientStreamableHttpTransport.builder(mcpUrl).build();
this.chatClient = builder.defaultOptions(chatOptions).build();
return chatClient.prompt().user(prompt)
        .tools(new DateTimeTools())
        .toolCallbacks(asyncMcpToolCallbackProvider.getToolCallbacks())
        .stream().content();

// Spring AI 2.0 GA
var httpRequestBuilder = HttpRequest.newBuilder().header("Authorization", "Bearer " + token);
var transport = HttpClientStreamableHttpTransport.builder(baseUrl)
        .connectTimeout(Duration.ofMinutes(3))
        .endpoint("/mcp")
        .requestBuilder(httpRequestBuilder)
        .build();
var options = ToolCallingChatOptions.builder().model("...").maxTokens(2000);
this.chatClient = builder.defaultOptions(options).build();   // Builder，不是实例
return chatClient.prompt().user(prompt)
        .tools(new DateTimeTools(), syncMcpToolCallbackProvider.getToolCallbacks())
        .stream().content();

@Bean ToolCallingManager toolCallingManager() { return ToolCallingManager.builder().build(); }
```

配置项拆分：

```properties
# Spring AI 2.0：base URL 与 endpoint 必须分开
mcp.gateway.base.url=https://<gateway-host>
mcp.gateway.endpoint=/mcp
```

---

## 2. 安全侧变化（对本框架是利好）

| 变化 | 内容 | 对治理层的意义 |
| --- | --- | --- |
| Spring Security 7.1 / Boot 4.1 | 新增 `InetAddressMatcher` / `InetAddressFilter`，框架底层自动拦截恶意内网请求（SSRF 防护） | 与本框架 [`examples/connect-path-guard/McpResolvedIpGuard.java`](../examples/connect-path-guard/McpResolvedIpGuard.java) 的「解析后真实 IP 校验 + IP 固定」**同向**，但**不能相互替代**：框架级 SSRF 过滤不覆盖 MCP 连接路径里的 DNS rebinding TOCTOU |
| 官方紧急修复 | 「**MCP 会话耗尽**」高危漏洞 + Spring Data REST JSON Patch 越权 | 再次印证「升级官方 SDK ≠ 安全」；治理层须保留按用户绑定会话、会话上限与 fail-closed |
| Streamable HTTP 默认 | SSE 退居次要 | 过滤器须覆盖无状态模式的每次请求鉴权（不能在握手时验一次就放行） |
| Micrometer / Actuator 内建 | token 用量、延迟、错误率指标开箱 | 与本框架 `mcp_security_*` 10 指标互补；跨期待办 `exportPrometheus()` 挂 actuator 可复用这条通路 |

---

## 3. 对本框架 21 模块的影响面评估

| 模块组 | 是否受 breaking changes 影响 | 说明 |
| --- | --- | --- |
| `mcp-core`、`mcp-auth`、`mcp-tenant`、`mcp-governance`、`mcp-audit`、`mcp-registry`、`mcp-monitor`、`mcp-gateway`、`mcp-server` | ✅ **基本不受影响** | 治理层不依赖 Spring AI 的 MCP 客户端 API；这正是「不被上游绑架」的兑现 |
| `mcp-integrations/mcp-alibaba`、`mcp-spring-ai-tools` | ⚠️ **需复核** | 若使用 `ChatClient` 或 MCP 客户端传输类，按第 1 节 5 项逐一对位 |
| `examples/client-java`、`examples/spring-ai-alibaba-mcp-server`、`examples/client-*` | ⚠️ **需更新示例配置** | endpoint 拆分、Builder 传参、`.tools(...)` 合并、`ToolCallingManager` bean |
| `pom.xml` 依赖版本 | ⚠️ **升级须整体回归** | Spring AI 2.0 要求 Spring Boot 4.1 / JDK 17+，属**破坏性升级**，不可只改示例 |

> **Spring AI Alibaba 定位不变**（延续 [`spring-ai-alibaba-compatibility-note-2026-11-01.md`](spring-ai-alibaba-compatibility-note-2026-11-01.md)）：最新稳定版仍停留在较早版本、2.0 预览长期未更新，故 **Alibaba 仅作可选兼容层、不进主 reactor**；本框架的 MCP 能力以官方 Spring AI / MCP Java SDK 为准。用户技术栈是 Spring AI Alibaba 时，接入路径是「Alibaba 负责模型侧，本框架负责 MCP Server 侧治理」，两者不互相硬依赖。

---

## 4. 升级验收清单（执行升级前逐项打勾）

- [ ] 在独立分支把 `pom.xml` 的 Spring AI / Spring Boot 版本升到 2.0 / 4.1，确认 JDK ≥17；
- [ ] 全局搜索 `WebClientStreamableHttpTransport` / `defaultOptions(` / `.toolCallbacks(` / `mcp.*url`，逐处替换为第 1 节写法（**注意 `.tools()` 合并后不要重复注册本地工具**）；
- [ ] 定义 `ToolCallingManager` bean，验证 `ChatClient` 能正常拿到 MCP 工具回调；
- [ ] `base.url` 与 `endpoint` 拆分后，用真实 Streamable HTTP 端点跑通一次 `tools/list` + `tools/call`（不是只看启动日志）；
- [ ] **回归治理层**：4 MiB 请求体上限、fail-closed、工具默认 disabled、按 caller 授权、HITL、审计留痕、`mcp_security_*` 埋点在 2.0 传输下仍逐条生效；
- [ ] 回归 `examples/` 下各组纯 JDK 自检（本期 4 组），确认与 Spring 版本解耦；
- [ ] CI（[`.github/workflows/maven-ci.yml`](../.github/workflows/maven-ci.yml)）在全量 21 模块上跑通后再合入主干；
- [ ] 更新 README / 兼容矩阵中的版本声明，并保留 1.x 兼容分支说明。

> ⚠️ **不要为了「追新」在主干直接升 2.0**：本框架当前 21 模块 CI 通过的是既有版本组合；升级属于独立工程，须按上表完整回归。本文只固化「差异与影响面」，**不宣称已完成升级**。

---

## 5. 售前可用的一句话

> 「Spring AI 2.0 GA 改了 MCP 客户端的传输类、URL 配置、ChatClient 选项和工具挂载方式 —— 我们的治理层一个都没跟着改，因为它**不依赖上游客户端 API**。上游可以换，权限、审批、审计这些 responsibility 得放在我们能治理的那一层。」

（对应买方问题「三大云已有 MCP 网关，为什么还要你」，可与 [`enterprise-cloud-gateway-comparison-2026-11-08.md`](enterprise-cloud-gateway-comparison-2026-11-08.md) 的 11 维对照一起用。）

---

## 诚实边界

- 本文全部 breaking changes 来自**第三方开发者实测文章**，未在本机执行 Spring AI 2.0 依赖升级与 `mvn` 构建验证；类名与 API 以官方发布文档为最终依据。
- 「21 模块治理层不受影响」是**基于架构解耦的评估**，不是实测结论；升级后仍须按第 4 节回归。
- Spring AI Alibaba 停更判断延续 11-01 记录（当时最新稳定版与预览版状态），本次未重新核验其仓库提交频率。
