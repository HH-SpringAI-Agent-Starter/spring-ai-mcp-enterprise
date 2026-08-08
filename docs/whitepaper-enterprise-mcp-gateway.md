# 企业级 MCP 网关白皮书

> 版本：V1.0 | 日期：2026-08-08 | 项目：Spring AI MCP Enterprise
> 定位：面向 CTO/架构师/企业 AI 负责人的技术决策文档，可直接用于售前/咨询/外包敲门砖

---

## 一、为什么企业需要 MCP 网关

### 1.1 背景：AI Agent 进入企业后端的必经之路

2026 年，主流 AI 产品（Claude、ChatGPT、Cursor、Gemini、微软 Copilot）已全部接入 MCP（Model Context Protocol）。企业采购 AI 的能力从「问答工具」跃迁到「任务交付」——AI 不再只是聊天，而是要**直接调用企业系统**：查订单、查库存、写工单、生成报表。

但直接让 AI 连数据库/内部系统是灾难：

| 风险 | 后果 |
|------|------|
| SQL 注入 / 越权查询 | 数据泄露，审计不过 |
| 无频率限制 | 一个循环调用打垮内部服务 |
| 工具权限不可控 | Agent 能删数据、改配置 |
| 无审计日志 | 出了事不知道谁调的、调的什么 |
| 无统一接入点 | 每个系统单独对接，重复造轮子 |

### 1.2 MCP Gateway 的定义

**MCP Gateway（MCP 网关）= 企业 AI 工具的统一安全接入层**。AI Agent 只跟网关说话，网关负责：认证 → 鉴权 → 限流 → 路由 → 审计 → 监控。

```
┌─────────┐   MCP/JSON-RPC   ┌──────────────────┐   HTTP/内部协议   ┌──────────┐
│ AI Agent │ ───────────────► │  MCP Gateway     │ ───────────────► │ 业务系统  │
│ (Claude) │                  │  认证/鉴权/限流   │                  │ 订单/库存 │
└─────────┘                  │  审计/监控/路由   │                  └──────────┘
                             └──────────────────┘
```

---

## 二、MCP Gateway 必须具备的 8 项企业能力

### 2.1 认证与授权（Auth）

- **API Key**：最简单的服务认证，X-API-Key 头传递
- **OAuth2 / OIDC**：对接企业已有 IdP（Keycloak / Okta / Azure AD），单点登录
- **Client Credentials**：机器对机器（service-to-service）授权，AI 服务账户换取令牌
- **RBAC 角色权限**：admin / user / viewer 三级角色，按角色控制工具可调用性

> ✅ Spring AI MCP Enterprise 已全部支持（mcp-auth 模块）

### 2.2 安全防护（Security）

- **SQL 注入防护**：数据库工具仅允许 SELECT/WITH 只读查询
- **SSRF 防护**：HTTP 工具域名白名单，未配置默认仅 localhost
- **IP 白名单** + 请求头白名单透传
- **审计日志**：谁、何时、调用了什么工具、参数、结果、耗时，全部留痕

### 2.3 限流与稳定性（RateLimit）

- 每工具每秒调用频率限制
- 超时控制（连接超时 / 读取超时）
- 响应体大小上限（防内存耗尽）

### 2.4 工具注册与扩展（Registry + SPI）

- SPI 扩展：实现 `McpToolExecutor` 接口 + `@Component` 即自动注册
- 工具元数据：名称/分类/权限/超时/频率/参数 Schema
- 启停控制：按工具维度启用/禁用

### 2.5 可观测性（Monitor）

- 调用指标采集：调用次数/成功率/延迟/错误率
- 审计日志落库
- 告警服务：异常时主动通知
- Prometheus 对接（docker-compose 已内置）

### 2.6 多传输协议兼容

- **Streamable HTTP**（2026-07-28 规范新默认）：无状态调用，可直接挂负载均衡
- **SSE 流式**（2025-03-26 协议）：兼容旧客户端，流式接收结果
- 未来：WebSocket 传输层

### 2.7 容器化与 K8s 部署

- Docker / Docker Compose 一键启动
- K8s 全套清单：Deployment + HPA + Ingress + ConfigMap + Service

### 2.8 AI 生态集成

- **Spring AI 1.0.0-M6 原生支持**：官方 MCP client/server 实现
- **Spring AI Alibaba 集成**：DashScope / 通义千问 / 百炼平台，国内企业零成本接入
- 多模型支持：Claude / 通义千问 / DeepSeek / Qwen

---

## 三、架构设计（参考 Spring AI MCP Enterprise）

### 3.1 模块划分

| 模块 | 职责 |
|------|------|
| mcp-core | 核心：工具注册中心 / 安全管理器 / 执行器 / 端点 |
| mcp-spring-boot-starter | 自动装配，一行依赖接入 |
| mcp-auth | 认证授权：API Key / OAuth2 / OIDC / RBAC |
| mcp-monitor | 可观测性：指标 / 审计 / 告警 |
| mcp-tools/* | 内置工具：database / search / system / weather / calculator / http |
| mcp-integrations/mcp-alibaba | Spring AI Alibaba（DashScope）集成 |
| mcp-server | 应用入口 + REST API + Admin API |

### 3.2 关键设计决策

1. **BOM 唯一约束**：Spring AI 1.0 BOM 在父 pom 统一管理，2.0 兼容由模块内 profile 自管，避免 Maven BOM 冲突
2. **自动装配双保险**：AutoConfiguration.imports + spring.factories 兼容 Spring Boot 3.4
3. **工具扫描**：`scan-packages: com.mcp.tool` 自动发现所有 `McpToolExecutor` Bean
4. **无状态优先**：Streamable HTTP 无 session 设计，天然支持水平扩展

---

## 四、落地路径（企业实施建议）

### 4.1 场景矩阵

| 场景 | 推荐工具 | 安全要求 |
|------|---------|---------|
| AI 查订单/库存 | database 工具 | 只读 SQL + 行数限制 |
| AI 对接内部 REST API | http 工具 | 域名白名单 + 请求头白名单 |
| AI 联网搜索 | search 工具 | 无敏感数据 |
| AI 系统运维 | system 工具 | 仅 admin 角色 |

### 4.2 分阶段实施

- **阶段 1（1-2 周）**：部署 MCP Server，接入 2-3 个只读工具（数据库 + 内部 API），API Key 认证
- **阶段 2（2-4 周）**：对接企业 IdP（OAuth2/OIDC），RBAC 角色落地，审计日志接入 SIEM
- **阶段 3（1-2 月）**：工具数量扩到 10+，接入监控告警，K8s 部署，灰度发布

### 4.3 成本估算参考

基于 2026 年 8 月市场调研：
- 智能体开发费用：MVP ¥5-15 万 / 中级 ¥20-60 万 / 企业级 ¥100-300 万
- MCP 协议适配占项目成本 **20-25%**
- 一个 ¥50 万的项目，MCP 部分价值 ¥10-12.5 万

---

## 五、为什么选择 Java/Spring 技术栈

| 维度 | Python 方案 | Java/Spring 方案 |
|------|------------|-----------------|
| 市场供给 | 80%+ 的 MCP 项目 | **几乎空白**（2026-07 数据） |
| 企业后端契合 | 需中间层适配 | **90% 中国企业后端是 Java** |
| 安全框架 | 需自研 | Spring Security 成熟生态 |
| 稳定性 | 一般 | JVM 企业级稳定 |
| 维护成本 | 脚本化难治理 | Spring Boot 标准工程化 |

---

## 六、参考资料

- [MCP 官方规范](https://modelcontextprotocol.io)
- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba)
- [本项目 GitHub](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise)

---

*本文档由 Spring AI MCP Enterprise 项目编写，可自由转载，注明出处即可。*
