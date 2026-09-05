# 安全审查对照表（Security Review Checklist）

> 适用版本：V1.20+
> 一句话卖点：**"auth and tenant isolation that passes review"** —— 把企业安全审查的每个问题，映射到本项目可演示的实现位置。
> 对应市场信号：NTT DATA（"authorization checks / least-privilege / auth that survives a security review"）、WF Next（"token scoping / per-tenant quotas / audit trails"）、Commerzbank（OAuth 2.0/OIDC + 监控）。

---

## 使用方式

审查员/客户问「你们怎么保证 X」→ 查下表 → 打开对应类/配置/文档现场演示。每条都有：**实现位置**（代码或配置）+ **怎么演示**（curl/端点）。

## 一、认证（Authentication）

| # | 审查问题 | 实现位置 | 演示方式 |
| --- | --- | --- | --- |
| A1 | API 凭据怎么发？怎么轮换？ | API Key 管理（mcp-core）+ `mcp-server.api-key` 环境变量注入 | `MCP_API_KEY` 通过 env 覆盖，不硬编码；Key 挂失/吊销走管理端点 |
| A2 | 支持企业标准协议吗？ | OAuth2 Client Credentials + Authorization Code + Refresh Token（RFC 7009 吊销、jti 防重放、RFC 9700 轮换）| 见 [oauth2-guide.md](oauth2-guide.md) |
| A3 | Bearer token 校验严格吗？ | V1.17 A2A 网关 Bearer 强制校验（RFC 6750），A2aJwtTokenValidator 与 mcp-auth 同密钥派生 | `curl` 无 token / 坏 token → 401 |
| A4 | token 过期与撤销？ | Refresh Token 轮换 + `revoke` 端点 + jti 黑名单 | 调 `POST /oauth2/revoke` 后旧 token 立即失效 |

## 二、授权（Authorization）

| # | 审查问题 | 实现位置 | 演示方式 |
| --- | --- | --- | --- |
| B1 | 有 RBAC 吗？ | RBAC 安全模块（mcp-core）| 角色 → 权限矩阵配置 |
| B2 | 最小权限到工具级了吗？ | **V1.19 工具级 Scope ACL**：`ScopeMatcher`（精确/单段 `*`/多段 `**`/全匹配）+ `ToolScopePolicy`（requiredScopes > toolOverrides > categoryDefaults，fail-closed）| 见 [scope-authorization-guide.md](scope-authorization-guide.md)：`tools/` 列表暴露 requiredScopes；无 scope token 调 db 工具 → **403 insufficient_scope** |
| B3 | 授权检查在调用链哪一层？ | `invokeWithScope` 在工具注册中心执行期拦截（不是前端过滤）| Streamable HTTP 返回 `-32090`；REST 返回 RFC 6750 `insufficient_scope` |
| B4 | 多租户数据隔离？ | 三档：Row-level（V1.11）→ Schema 级（V1.12）→ 实例级独立连接池（V1.13，TenantInstanceRegistry，fail-closed）| 见 [multi-tenant-research-2026-08-25.md](multi-tenant-research-2026-08-25.md) |
| B5 | 租户生命周期可控？ | `/api/admin/tenants` 运行时开通/替换/挂起/恢复/销毁（V1.14）| 404/409 语义化错误 + 集成测试 |

## 三、传输与网关（Transport & Gateway）

| # | 审查问题 | 实现位置 | 演示方式 |
| --- | --- | --- | --- |
| C1 | 传输加密？ | HTTPS 由部署层保证（Docker/k8s ingress，见 [production-deployment.md](production-deployment.md)）；A2A/MCP 端点不降级明文 | 生产配置示例 |
| C2 | 限流与背压？ | 网关限流（V1.7 按操作 QPS 路由表 + 令牌桶）+ 按租户配额（V1.9）| 超限 → 429 |
| C3 | SSRF 防护？ | tool-http 模块内置 SSRF 防护（V1.1）| 对内网地址调用被拒 |
| C4 | 熔断/灰度？ | 网关治理（配合 Higress/APISIX/Kong 部署见 README）| 部署文档 |

## 四、审计与合规（Audit & Compliance）

| # | 审查问题 | 实现位置 | 演示方式 |
| --- | --- | --- | --- |
| D1 | 谁在什么时间调了什么工具？ | 全链路审计日志（mcp-core，含租户/scope/token 维度）| 审计端点查询 |
| D2 | 可观测性？ | Prometheus 指标（V1.7 网关指标）+ health 端点（含 Signed Agent Card 的 cardKeyId）| `/actuator` / `/health` |
| D3 | 合规证据链？ | 审计日志 + scope 收敛 + 租户隔离 = "能过安全审查"的证据链 | 审查演示脚本 |

## 五、供应链（Supply Chain / A2A）

| # | 审查问题 | 实现位置 | 演示方式 |
| --- | --- | --- | --- |
| E1 | Agent Card 防篡改？ | **V1.18 Signed Agent Card**：JWS HS256 签名 + 规范化 JSON + `X-Agent-Card-Signature` 头 + `agent-card/verify` 自验证端点 | `verify` 返回签名校验结果；防 `alg=none` 混淆 + 常量时间比较 |
| E2 | A2A 网关鉴权？ | V1.17 三模式（none/api-key/oauth2）+ resolvedAuthMode 显式声明 | 见 [a2a-integration-guide.md](a2a-integration-guide.md) |

## 六、密钥与配置管理

| # | 审查问题 | 实现位置 | 演示方式 |
| --- | --- | --- | --- |
| F1 | 密钥不进代码库？ | 全部 `${ENV}` 占位（api-key / signing-key / datasource）| `.gitignore` + 配置模板 |
| F2 | 签名密钥派生规则一致？ | mcp-auth 与 A2A 共用 `<32 字节补齐 + SHA-256>` 派生（V1.17/V1.18）| 单测覆盖 |

---

## 对外话术（30 秒版）

> "我们从 V0.1 到 V1.20 用 20 个版本把 MCP Server 的企业安全闭环做完：RBAC → 限流 → 审计 → OAuth2 全生命周期（签发/刷新/吊销/jti）→ 多租户三档隔离 → 工具级 Scope ACL（最小权限到工具）→ A2A 供应链签名。每一个审查问题都能用 curl 现场演示，包括 403 insufficient_scope 和 Agent Card 验签。"