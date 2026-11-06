# MCP 安全加固速查（5 步）— 用本框架把"裸奔 Server"变"合规 Server"

> 配套：[`earnings-report-2026-10-08.md`](earnings-report-2026-10-08.md) · [`mcp-governance-dengbao-compliance.md`](mcp-governance-dengbao-compliance.md)
> 痛点背景（2026）：仅 **8.5%** MCP server 实现 OAuth 2.1、**43%** 命令注入漏洞、**492** 个零认证裸奔公网、仅 **18%** 做工具权限 scoping。NSA+CISA 已联合公告：治理须由部署方自建。

本框架 `mcp-governance` 提供开箱即用的治理层，5 步把你的 MCP server（无论 Spring AI Alibaba / 官方 SDK / 自研）加固到企业级。

---

## 第 1 步：网关集中鉴权（解决 8.5% OAuth 缺口）
不要把 auth 分散到每个 server。前置 `mcp-gateway`，所有流量统一 Bearer 校验；接入企业 IdP 用 **EMA（企业托管授权）** 直接发令牌（等同 2026 稳定的 Enterprise-Managed Authorization / Cross App Access），无需每连接弹 consent 屏。

```yaml
mcp:
  gateway:
    auth:
      mode: bearer           # 网关强制 Bearer，拒绝无令牌请求
      ema:
        enabled: true        # IdP 直接签发 MCP 访问令牌
```

## 第 2 步：工具级 RBAC + Scope ACL（解决 18% scoping 缺口）
每个工具声明 `requiredScopes`，Token Scope → Tool 级 fail-closed 决策。最小权限工具绑定：客服 MCP 只暴露 read CRM / create ticket / send notify，**绝不**暴露 delete customer / modify pricing / billing history。

```yaml
mcp:
  governance:
    scope:
      enforce: true          # 缺 scope 直接拒绝（fail-closed）
    tool-policy:
      - tool: "db_query"
        requiredScopes: ["mcp:read:internal"]
      - tool: "db_write"
        requiredScopes: ["mcp:write:internal", "mcp:role:admin"]
```

## 第 3 步：高风险工具 HITL 审批（解决 43% 投毒/误用）
T3/T4 高风险工具先入审批队列，批准后发一次性令牌（X-MCP-Approval-Id）防重放、防工具/调用方替换；敏感数据自动脱敏（身份证/银行卡/手机号/密钥/邮箱）。

```yaml
mcp:
  governance:
    hitl:
      tiers: [T3, T4]        # 高风险分级（OWASP 对齐）
      deny-tiers: [T5]       # 硬闸门
      approval-store: jdbc   # 审批落库，跨副本/重启可见
```

## 第 4 步：审计落库 + SIEM 实时流（解决 492 零追溯）
治理判定 JDBC 落库（重启不丢、多实例全局可查），实时批量推送到 Splunk HEC / ES / Loki / Kafka；携带 W3C traceparent 做全链路回溯；支持 CSV 监管报送与 GDPR 保留 TTL。

```yaml
mcp:
  governance:
    audit:
      store: jdbc            # 方言无关（H2/MySQL/PG/SQL Server）
      sink: http             # 实时流导出到 SIEM
      http-endpoint: "https://siem.internal/hec"
```

## 第 5 步：限流隔离 + 零信任每调用授权
网关按工具 QPS 限流；长流程（分钟~小时）用**零信任每调用独立授权**（非会话级信任），策略/角色变化、紧急吊销即时生效。

```yaml
mcp:
  gateway:
    ratelimit:
      enabled: true
      per-tool-qps: 100
```

---

## 第 6 步：传输层与注册期硬约束（应对 11-03~11-06 攻击波）

> 前 5 步解决「谁能调、调什么、怎么留痕」；这一步解决「**请求本身能不能被信任**」。
> 触发背景（2026-11-06 复核）：Wiz.io 90 天蜜罐实录 LiteLLM MCP Gateway 接受**一个字符** bearer token（`CVE-2026-59822`）、伪造 MCP server 配置的 `command` 字段在「连通性测试」里启动矿机且**握手正常**（`CVE-2026-42271`）、host-header 绕过串成未认证 RCE（`CVE-2026-48710`）；同期 NVD 新增 `CVE-2026-123272/123271`（OAuth resource confusion，RFC 9728 §3.3）、`CVE-2026-111769`（官方 TS SDK 跨源重定向泄漏 refresh_token）、`CVE-2026-121419`（会话指纹按方法名→劫持）、`CVE-2026-121458`（MCP 工具跳过对象级授权）。

五条硬约束，逐条对应攻击：

| 约束 | 拦住什么 | 模块 |
| --- | --- | --- |
| 请求体 **4 MiB** 上限（声明值 + 流式读取双校验，超限 413） | 全 SDK 无界请求体 DoS（`CVE-2026-108395`，官方 Java SDK `mcp-core` 亦中招） | `mcp-gateway` |
| 鉴权 **fail-closed**：签名 / aud / iss / exp / scope 全校验，异常一律 401，绝不降级 | 一个字符 token 过网关；元数据 resource 与所访问 server 不符 | `mcp-auth` |
| **同源 + 禁跟随跨源 307/308**（Host / X-Forwarded-Host 一致性） | host-header 绕过、SDK 重定向带出 refresh_token / client_secret | `mcp-gateway` |
| 会话指纹 = **租户 + 用户主体 + clientId**，禁用 access method 名 | 同方法不同用户会话互相劫持 | `mcp-tenant` |
| 工具注册 **默认 disabled** + 配置字段校验（拒绝 command/shell）+ 工具级 ACL 复用主 API 对象授权 | 注册期命令注入、MCP 侧门越权读 | `mcp-registry` + `mcp-governance` |

```yaml
mcp:
  gateway:
    auth:
      fail-closed: true
      verify: [signature, audience, issuer, expiry, scope]
      same-origin-only: true
      follow-cross-origin-redirect: false
    limits:
      max-request-body: 4MiB
  registry:
    default-enabled: false       # 对标 Uber 800+ servers：审核后显式启用
    validate-config: true
    reject-command-fields: true
  governance:
    tool-policy:
      reuse-business-authorization: true   # 不另写一套宽松逻辑
      write-requires-approval: true
    session:
      fingerprint: tenant+subject+clientId
  monitor:
    watch: [shell-spawn, child-process, outbound-egress, credential-access]
```

> 📌 **最容易被忽略的一条**：Wiz 观察到攻击者从**运行中的 Python 进程内存**里直接取回代理主密钥（磁盘上根本没有这个文件），并把矿机藏进 `.claude/` 目录伪装 AI 开发工具。所以 `mcp-monitor` 必须覆盖 **shell 派生 / 异常子进程 / 出站连接白名单 / 凭据访问**，而不能只看 HTTP 状态码 —— **握手正常不代表没被打**。

可直接参考的过滤器骨架 + 流式限流 Wrapper + 完整配置 + 自测清单：
[`examples/governance-hardening/`](../examples/governance-hardening/README.md)（参考片段，不进 Maven reactor，零构建风险）。

---

## 合规加分：等保 2.0 三级对照表
上述 5 步逐条映射到等保三级"访问控制/安全审计/入侵防范/数据保密/集中管控/高风险审批/限流隔离"，每条标注模块 + 测试证据 → [`mcp-governance-dengbao-compliance.md`](mcp-governance-dengbao-compliance.md)，**可直接进央企/军工/卫健标书作"合规就绪"证明**。

## 一键验证
```bash
# 启动 server + monitor
docker compose --profile monitoring up -d
# 健康检查 / 审批 / 审计检索 / 联邦同步 REST API 见 governance-guide.md
```

---

*这 5 步不是理论。在 78% 企业已用 MCP、却 91.5% 裸奔的当下，谁能把 server 加固到合规，谁就吃到这波存量加固红利。*
