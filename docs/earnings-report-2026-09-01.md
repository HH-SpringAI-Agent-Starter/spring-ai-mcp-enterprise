# Earnings Report 2026-09-01（V1.16）

## 今日做了什么（产出）

1. **V1.16 功能落地**：mcp-a2a 模块新增 A2A SSE 流式（`message/stream` + `task/resubscribe`）+ Agent Card `securitySchemes` 声明（mcp-auth 打通第一步）。
   - 新类：`A2aStreamEvent`；改造：`A2aBridgeService`（异步流式 + 订阅重放）、`A2aRpcController`（SSE 端点）、`A2aAgentCard`（securitySchemes）、`McpA2aProperties`（streaming/security 配置）。
   - 测试：A2a 模块 15 用例全绿；全仓 `mvn test` BUILD SUCCESS。
2. **配置与文档**：mcp-server application.yml 注入流式/安全配置；`a2a-integration-guide.md` 新增 V1.16 流式章节（端点、事件表、curl 示例、securitySchemes）。
3. **博客 SEO**：`docs/blog-java-mcp-a2a-streaming-2026-09-01.md`（掘金/CSDN 稿件）。
4. **市场雷达**：`docs/market-research-2026-09-01.md`。

## 为什么做

- V1.15 已让同一网关同时讲 MCP + A2A，但 A2A 侧缺"流式调度"与"鉴权声明"两块硬能力；不补则无法被真正的 A2A 编排器正确接入。
- 本周招聘信号的高频词（SSE / Streamable HTTP / token 管理）与本次改动精准对齐。

## 市场价值 / 挣钱信号（本日新增）

- **OneSeven Tech**：远程 Java+Spring+WebFlux MCP 基建，$4000–5000/月（长期 Contract）——可直接对接。
- **Sumo Logic**：Staff，$207K–243K/yr，SSE 流式 + 多租户 + 可观测。
- **Bitrock**：远程，$120K–150K/yr，Java/Spring + MCP。
- **华沙保险业 MCP Server B2B 外包**：Java 17+ / Spring Boot / SSE。
- **国内**：火石创造（重庆）Spring AI + MCP 高级 Java。

## 下一步（V1.17 / 明日）

1. mcp-auth 深度打通：OAuth2 Client Credentials 实际令牌交换 + 网关强制校验（不止声明）。
2. Signed Agent Card（防伪造卡片攻击）。
3. A2A Push Notifications（task/notify 回调）。
4. 把 A2A + 流式能力提交 mcp.so / smithery 注册表标注 + 一页 pitch 对接 OneSeven / Sumo Logic 类需求。
