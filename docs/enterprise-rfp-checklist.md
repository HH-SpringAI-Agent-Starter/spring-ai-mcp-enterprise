# 企业采购对照表：MCP Server 框架 RFP 检查清单（V1.10）

> 用途：投标 / 售前 / 面试话术。将项目 V1.0-V1.9 的已落地能力逐项映射到企业 RFP（需求建议书）的检查项，做到"打开文档即可对照打分"。
> 生成日期：2026-08-24 | 对应代码版本：V1.9（稳定）

## 使用方式

1. 拿到企业 MCP/AI 集成 RFP 后，按本表 Section 逐项核对；
2. 命中项 → 填写"我方证据"列（模块名 / 文档链接 / 已提交 commit）；
3. 未命中项 → 标记为 V1.11+ 路线图缺口，并在报价中注明"定制开发"。

---

## A. 身份与认证（Identity & Authentication）

| # | RFP 检查项 | 企业典型表述 | 本项目落地 | 证据 |
| --- | --- | --- | --- | --- |
| A1 | 客户端凭证认证 | "支持 M2M 服务间调用，禁止长期密钥硬编码" | OAuth2 Client Credentials 签发短期 access_token | mcp-core `McpOAuth2Manager.issueClientCredentialsToken` |
| A2 | Token 轮换 | "长期驻留 Agent 需可安全续期" | Refresh Token 轮换（RFC 9700 / OAuth2 Security BCP） | V1.9 `refreshClientCredentialsToken` |
| A3 | 重用检测 / 泄露熔断 | "检测凭证泄露并快速失效" | 已轮换 refresh token 复用 → 判定泄露 → 家族吊销 | V1.9 重用检测 + 整族吊销 |
| A4 | Token 吊销 | "支持主动吊销会话" | RFC 7009 `/oauth2/revoke`（access+refresh，恒 200 防探测） | V1.9 |
| A5 | Token 内省 | "网关可验证任意 token 有效性" | RFC 7662 `/oauth2/introspect` | V1.8 |
| A6 | 企业集中授权（EMA） | "统一由企业 IAM 授权，而非散落各系统" | EMA 流程：admin 端签发 client 凭证，集中管控 | V1.8 |
| A7 | API Key 兼容 | "存量调用方平滑迁移" | X-API-Key 存量校验保留，Bearer 校验失败才 fail-closed | V1.9 平滑迁移设计 |
| A8 | jti 防碰撞 | "token 全局唯一，防重放" | access_token 携带全局唯一 jti | V1.9 |

## B. 授权与隔离（Authorization & Isolation）

| # | RFP 检查项 | 本项目落地 | 证据 |
| --- | --- | --- | --- |
| B1 | RBAC 角色权限 | mcp-core RBAC（角色-工具权限矩阵） | V1.0+ |
| B2 | Scope 级二次鉴权 | `mcp.tokenInfo` 请求属性携带 scopes/roles，下游可做 scope 鉴权 | V1.9 Bearer 过滤器 |
| B3 | 租户隔离 | （多租户为 V1.11+ 候选；当前单实例隔离可用） | 路线图 |
| B4 | 工具级最小权限 | 按工具注册中心粒度授权 | mcp-tools 注册中心 |

## C. 安全治理（Security Governance）

| # | RFP 检查项 | 本项目落地 | 证据 |
| --- | --- | --- | --- |
| C1 | 审计日志 | 全量调用审计（谁/何时/调了哪个工具/入参出参） | mcp-core AuditLogger |
| C2 | 速率限制 | 按客户端/按操作 QPS 限流 + 运行期管理 | V1.7 限流路由表 |
| C3 | 网关强制校验 | fail-closed Bearer 强制校验（非公开路径 401 + WWW-Authenticate） | V1.9 `McpBearerAuthFilter` |
| C4 | 敏感数据 | refresh token 仅存 SHA-256 散列，库泄露不可逆 | V1.9 |
| C5 | SSRF 防护 | tool-http 内置 SSRF 防护 | V1.1 |

## D. 协议与互操作（Protocol & Interop）

| # | RFP 检查项 | 本项目落地 | 证据 |
| --- | --- | --- | --- |
| D1 | MCP 2026-07-28 规范适配 | tools/resources/prompts 三原语；Streamable HTTP | V0.11/V0.15/V1.5 |
| D2 | Streamable HTTP 传输 | mcp-server REST/Streamable 端点 | V1.0+ |
| D3 | 客户端生态 | 官方 SDK 兼容（Claude/Cursor/Desktop） | examples/client-* |
| D4 | Spring AI Alibaba 兼容 | 用户技术栈 Spring AI Alibaba，DashScope 模型接入 | mcp-integrations/mcp-alibaba |

## E. 可观测性与运维（Observability & Ops）

| # | RFP 检查项 | 本项目落地 | 证据 |
| --- | --- | --- | --- |
| E1 | 健康检查 | `/api/mcp/health`（Bearer 过滤器自动放行） | V1.9 |
| E2 | 指标导出 | Prometheus 指标（工具调用统计） | V0.9/V1.7 |
| E3 | 容器化部署 | Dockerfile + docker-compose + k8s/ 清单 | 仓库根目录 |
| E4 | CI/CD | GitHub Actions（JDK 17/21 矩阵，自动构建+测试+镜像推送） | .github/workflows/maven-ci.yml |
| E5 | 监控面板 | mcp-monitor 模块 | mcp-monitor |

## F. 交付与文档（Delivery & Docs）

| # | RFP 检查项 | 本项目落地 | 证据 |
| --- | --- | --- | --- |
| F1 | 快速上手 | docs/quickstart.md | ✓ |
| F2 | 架构说明 | docs/architecture.md | ✓ |
| F3 | API 文档 | docs/api-docs.md + REST 端点 | ✓ |
| F4 | 生产部署 | docs/production-deployment.md + operations.md | ✓ |
| F5 | OAuth2 指南 | docs/oauth2-guide.md | ✓ |
| F6 | 多语言客户端示例 | Java / Python / Node.js / curl | examples/ |

## 差距清单（诚实标注，V1.11+ 候选）

| 缺口 | 说明 | 计划 |
| --- | --- | --- |
| 多租户隔离 | SaaS 型多租户 MCP 需 tenant 维度隔离 | V1.11 候选 |
| SOC2/HIPAA 合规脚手架 | 需合规报告模板 + 数据驻留开关 | V1.12 候选 |
| HITL（人工审批） | 高风险工具调用需人工确认流 | V1.11 候选 |
| 官方 Registry 收录 | agentmarketcap / mcp.so / smithery 提交 | 本周候选 |

---

## 一句话投标话术

> "我们不是又一个 MCP 连接器，而是 **Java 生态的企业级 MCP Server 框架**：OAuth2 全家桶（Client Credentials + Refresh Token 轮换 RFC 9700 + 重用检测 + 吊销 + 内省 + EMA 集中授权）、RBAC、全量审计、限流、网关 Bearer 强制校验、Prometheus 可观测——RFP 里安全治理类条目 90% 开箱即得，剩余 10% 按定制清单报价。"