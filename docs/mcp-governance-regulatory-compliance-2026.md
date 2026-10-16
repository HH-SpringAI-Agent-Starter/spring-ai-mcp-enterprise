# 🛡️ MCP 治理 × 全球监管合规映射（2026）

> 本文把 **2026 年正在成形的 MCP / Agentic AI 监管与基准要求**，逐条映射到本框架 `mcp-governance` / `mcp-auth` / `mcp-tenant` / `mcp-gateway` / `mcp-monitor` 的已有控制能力。
> 用途：**直接进央企/军工/卫健标书作"合规就绪"证明**，或作企业安全审计交付物。配合 [等保 2.0 三级对照表](mcp-governance-dengbao-compliance.md) 与 [安全事件简报](mcp-security-incident-brief-2026-10.md) 使用。

---

## 0. 一句话护城河

> **"Without technical enforcement at the MCP layer, a policy requirement is not a control."**
> —— AI Governance Institute（2026-10），直指核心：政策若不在 MCP 协议层做**技术强制**，就不算控制。本框架就是把"合规政策"变成"技术控制"的那一层。

---

## 1. 监管 / 基准来源（2026 已公开）

| 来源 | 性质 | 与本框架关系 |
|---|---|---|
| **CIS MCP Benchmark** | 55 点 agent tool governance 审计基线 | 逐点可映射到 Scope/ACL/HITL/审计 |
| **Five Eyes《Agentic AI Security Guidance》** | 多国情报联盟联合指引 | least-privilege、工具层控制作基线 |
| **NIST AI Risk Management Framework (AI RMF)** | 美国 AI 风险框架 | 把 tool-layer 访问控制作控制项 |
| **EU AI Act (Regulation (EU) 2024/1689)** | 欧盟强制法（高风险 AI） | 高风险的 agent 工具访问将受审 |
| **MAS Guidelines on AI Risk Management**（新加坡） | 金管局指引 | 金融业 agentic tool access 预期 |
| **韩国 Agentic AI Security Rules（起草中）** | 国家层面强制规则草案 | MCP 治理走向绑定要求 |
| **Agentics《Enterprise MCP Guide 2026》** | 六类攻击 taxonomy + 68 CVE/月 | 给出"该防什么"的清单 |

---

## 2. 六类攻击 → 模块映射（Agentics 2026）

| 攻击类 | 风险 | 本框架控制（模块·能力） |
|---|---|---|
| **Tool Poisoning**（恶意工具描述诱导 Agent） | 诱导有害操作 | `mcp-governance` 工具注册审批 + 工具描述校验 + 审计留痕 |
| **Schema Poisoning**（篡改可用工具清单） | 清单被替换 | `mcp-registry` 注册中心唯一真相源 + 签名/校验 + `mcp-governance` 白名单 |
| **Tool Shadowing**（恶意工具悄悄覆盖合法工具） | 同名覆盖 | `mcp-gateway` `<prefix>__<tool>` 命名空间隔离 + Scope 绑定 |
| **Command Injection**（在内容中嵌指令） | 注入执行 | `mcp-governance` 输入校验/参数脱敏 + 限流 + 资源隔离 |
| **Shadow Servers**（未授权 MCP server 入环境） | 私接服务 | `mcp-registry` 集中注册审批 + `mcp-tenant` 租户边界 + 网关集中鉴权 |
| **Context Oversharing**（Agent 暴露超范围数据） | 数据越权泄露 | `mcp-governance` 工具级 Scope/ACL + `mcp-tenant` 数据隔离 + 审计 |

> 六类攻击的**共同根因** = "缺少 MCP 层的工具级强制控制"。本框架 `mcp-governance`（RBAC/Scope/ACL/HITL/审计）+ `mcp-gateway`（集中鉴权/命名空间）+ `mcp-tenant`（租户/数据隔离）正是为这六类而生。

---

## 3. 监管控制项 → 模块映射（可直接抄进标书）

| 监管控制项（通用表述） | 刚性要求 | 本框架对应能力 | 证据/交付物 |
|---|---|---|---|
| **Per-agent tool allowlist**（每 Agent 工具白名单） | 每个 Agent 仅能触达其任务所需工具 | `mcp-governance` Scope/ACL（工具级授权） | 等保对照表 A.1 |
| **Identity binding**（身份绑定） | Agent 行为绑定到已验证身份 | `mcp-auth` Bearer 强制校验 + `mcp-tenant` 租户身份 | 安全事件简报 M1 |
| **Centralized gateway logging**（集中式网关日志） | 所有工具调用集中记录 | `mcp-gateway` 集中鉴权 + JDBC 审计（`mcp_governance_audit` 单表） | 等保对照表 A.3 |
| **HITL for irreversible actions**（不可逆操作人工审批） | 删数据/发消息等需人工签核 | `mcp-governance` HITL T0–T4 分级审批 | 等保对照表 A.7 |
| **Least-privilege access**（最小权限） | 默认拒绝 + 显式授权 | `mcp-governance` 默认拒绝 + Scope 最小授予 | CIS 55 点对应 |
| **Audit trail / SIEM**（审计轨迹 / 对接 SIEM） | 可追溯 + 对外投递 | JDBC 审计 + `audit.store=http` → SIEM/Kafka/webhook | 等保对照表 A.3 |
| **Multi-agent trust hierarchy**（多 Agent 信任层级） | Agent 间调用需显式授权 | `mcp-gateway` 联邦 + `mcp-auth` 跨 Agent 授权 | 安全事件简报 M2（Protocol Pivoting） |
| **Outbound destination validation**（出站目的校验） | Agent 出站请求校验目的地 | `mcp-governance` SSRF 防护 + 输入校验 | 安全事件简报 M3（CVE-2026-104120） |
| **Secrets management**（密钥管理） | 不硬编码密钥 | 配置外置 + `mcp-auth` 凭据托管（非 sk-1234 默认） | 安全事件简报 M4 |

---

## 4. 与"官方方案"的差异化定位

Spring AI Alibaba（Nacos MCP Registry + Higress AI 网关）提供**注册 + 路由 + 代理**，但：
- Nacos/Higress **不提供**工具级 Scope/ACL、HITL、JDBC 审计、租户数据隔离、CVE 级 SSRF 防护。
- 评测机构 FollowAgents 直指其 **"1 safety controls not found"**。
- 社区新构件已开始把 **"governed"** 写进命名（如 TAF MCP STDIO "governed workflows"）——治理成为默认期待。

→ 本框架定位 = **官方方案的"安全治理增强层"**：在 Nacos/Higress 之上叠加 RBAC/审计/HITL/Scope/多租户，把"能跑"变成"合规可审计地跑"。

---

## 5. 标书 / 审计使用方式

1. 投标"AI 网关 + MCP 注册中心"类标（如 ZC26G280436）：把 §2、§3 表格作为"合规控制对照"章节。
2. 投标等保三级类标（如湖北疾控 150 万）：叠加 [等保 2.0 三级对照表](mcp-governance-dengbao-compliance.md)。
3. 安全审计交付物：叠加 [安全事件简报](mcp-security-incident-brief-2026-10.md)（10 起真实 CVE → 本框架缓解项映射）。
4. 对外宣讲：引用 §0 金句 + CIS 55 点 + 五辖区监管引用，证明"治理不是可选项"。

---

*最后更新：2026-10-16 · 随 V1.44 发布。*
