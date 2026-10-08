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
