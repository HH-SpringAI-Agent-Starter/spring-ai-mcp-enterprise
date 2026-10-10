# 🛡️ MCP 安全事件简报与合规控制映射（2026-10）

> 用途：**投标合规证明 / 安全审计报告 / 客户尽职调查交付物**。
> 本简报汇总 2026-10 公开的 MCP 安全事件，并逐条映射到 `spring-ai-mcp-enterprise` 的治理控制（含模块 + 版本 + 测试证据）。可截图/节选直接进央企、军工、卫健、金融标书。
> 项目版本：V1.37（21 模块，governance 66 测试 / 全仓构建全绿）。

---

## 一、本月公开重大事件（来源见末尾）

| # | 事件 | 严重度 | 攻击手法 | 状态 |
|---|------|--------|----------|------|
| 1 | **LiteLLM MCP 端点接受任何无效 bearer token** | **CISA KEV（已被利用）** | 认证绕过 → 云接管 | CVE-2026-59822 |
| 2 | **Google mcp-toolbox SSRF** | CVSS **8.0 High** | 不受控出站 → 内网/元数据访问 | CVE-2026-14540，1.5.0 修复 |
| 3 | **Grafana MCP 会话伪造 + SSRF 代理** | 高 | 会话 ID 仅按格式校验，伪造调用服务账号工具 | CVE-2026-19516 |
| 4 | **JPMorgan / Weaviate / 法国 DINUM / 印尼 Tangerang 同款 SSRF** | 中–高 | agent 委派文本触发服务端取 URL | 均已修复 |
| 5 | **5 个美国政府 MCP server 仍处 triage 未修复** | 未知 | 同类信任边界缺陷 | 未修复（class-level 披露） |
| 6 | **LiteLLM 管理员代码执行 + 云元数据可达** | 高 | 自定义 guardrail 绕过 | CVE-2026-59821 |
| 7 | **2 个恶意 MCP server 干净通过 5 款扫描器** | 中 | 工具描述投毒 / 供应链 | 行业通病 |
| 8 | **约 82,000 份公开 MCP 配置含硬编码密钥** | 高 | 凭证泄露（多数形态逃过 secret scanner） | 行业通病 |
| 9 | **9.6% 暴露代理仍用默认 `sk-1234` 主密钥** | 高 | 预认证链 → 云接管 | 行业通病 |
| 10 | **Rapid7 Bulk Export MCP GraphQL 注入** | CVSS 2.7 | 注入 | CVE-2026-97228（已修） |

**攻击范式命名**：Mohiuddin 称 "**Protocol Pivoting**"（协议转向）——恶意文本经一个协议进入，利用协议间信任假设升级到另一协议才可达的能力。X41 D-Sec 归为间接提示注入（indirect prompt injection）子类。**本质共识：任何从模型/LM 流向工具的输入，都应视为"来自陌生人的输入"。**

---

## 二、事件 → 框架控制映射（核心交付物）

| 事件 | 需要的控制 | 本框架模块 / 版本 | 证据 |
|------|-----------|------------------|------|
| #1 无效 bearer token（CISA KEV） | **Bearer 强制校验 + 令牌不可伪造 + 吊销** | `mcp-auth` OAuth2 Bearer 强制校验（V1.9，RFC 6750）+ `jti` 防碰撞 + RFC 7009 吊销端点 | V1.9 发布说明；OAuth2 指南 |
| #2 Google SSRF（CVSS 8.0） | **出站地址校验 + 私有网/元数据阻断** | `mcp-tools/tool-http` SSRF 防护（V1.1）+ `mcp-governance` 出站 allowlist 治理开关 | V1.1 发布说明；governance 指南第 12 章 |
| #3 Grafana 会话伪造 | **会话身份强绑定 + 工具级 ACL** | `mcp-governance` RBAC + Scope→Tool ACL（V1.19，fail-closed）+ Signed Agent Card（V1.18） | V1.19/V1.18 发布说明；26 新测试 |
| #4 同款 SSRF（JP/Weaviate/政府） | 同 #2 | 同上 | 同上 |
| #5 联邦 server 暴露 | **多租户隔离 + 集中鉴权网关** | `mcp-tenant` 实例级隔离（V1.13）+ `mcp-gateway` 集中鉴权联邦 | V1.13/V1.25 发布说明 |
| #6 管理员代码执行 | **高风险操作人工审批 + 最小权限** | `mcp-governance` HITL 人类在环（V1.26）+ deny-tiers 硬闸门 + fail-closed | V1.26 发布说明；35 测试 |
| #7 恶意 server 过扫描 | **服务端信任校验 + 工具描述扫描** | `mcp-governance` 工具描述校验（Unicode 规范化 + 启发式 + LLM judge）+ 默认拒绝审批 | governance-guide.md |
| #8 硬编码密钥 | **密钥占位 + 启动期校验** | `mcp-tenant` `${ENV}` 密钥占位 + 初始化 DDL 校验 | V1.13 发布说明 |
| #9 默认主密钥 | **强制密钥轮换 + 不允许默认值** | `mcp-auth` 密钥管理 + 网关集中鉴权（无 per-server 默认密钥） | OAuth2 指南 |
| #10 注入 | **输入校验 + 最小权限工具** | `mcp-governance` 敏感数据脱敏 + Scope 约束 + 网关集中校验 | V1.26/V1.19 发布说明 |

---

## 三、可以给客户的"合规就绪"话术（节选）

> "我们的企业级 MCP Server 框架在**认证、出站控制、会话绑定、审计追溯、人工审批、多租户隔离**六个维度默认开启生产级控制。本月 CISA KEV 首个 MCP 漏洞（LiteLLM 接受无效 bearer token）对应的缓解——Bearer 强制校验 + 令牌防碰撞 + 吊销——在本框架 `mcp-auth`（V1.9）默认生效；Google CVE-2026-14540（CVSS 8.0 SSRF）对应的出站地址校验，由 `mcp-tools/tool-http`（V1.1）+ `mcp-governance` 出站 allowlist 覆盖。所有控制均有单元/集成测试证据，可进等保 2.0 三级对标（见 `docs/mcp-governance-dengbao-compliance.md`）。"

---

## 四、给部署方的 5 步加固清单（引流接单用）

1. **Bearer 必须校验**：拒绝任何无效/过期/吊销令牌（本框架 `mcp-auth` 默认开）。
2. **出站加 allowlist**：MCP server 发起的请求只允许白名单主机，阻断私有网/169.254 元数据（本框架 `tool-http` SSRF 防护 + governance 出站开关）。
3. **工具按 Scope 收口**：可见工具 = 任务所需最小集，越权调用 fail-closed（本框架 Scope→Tool ACL）。
4. **高风险操作走 HITL**：破坏性/出站动作需人工一次性令牌审批（本框架 HITL + deny-tiers）。
5. **审计留痕且 SIEM 可达**：每条判定带 traceId，批量推 SIEM（本框架 JDBC 落库 + 实时流导出 V1.32）。

---

## 五、来源索引（2026-10）

- Ars Technica（10-05/06）：https://arstechnica.com/security/2026/10/vulnerability-in-agents-from-google-and-others-exposes-structural-flaw-in-mcp/
- ThreatCluster「Protocol Pivoting, four months later」：https://threatcluster.io/article/researcher-discloses-same-mcp-flaw-at-google-jpmorgan-two-go-bba90fcd
- Times of AI（10）：https://timesof.ai/2026/10/mcp-protocol-pivoting-cross-agent-ssrf
- Adversa AI「MCP security October 2026: LiteLLM on CISA KEV, Grafana MCP flaw」：https://adversa.ai/blog/top-mcp-security-resources-october-2026
- GitHub Advisory CVE-2026-14540（googleapis/mcp-toolbox）
- CoSAI MCP Security Model v2.0（2026-10 发布）

> 配套资产：等保对照表 `docs/mcp-governance-dengbao-compliance.md` ｜ 加固速查 `docs/mcp-security-hardening-quickstart.md` ｜ 每日情报 `docs/earnings-report-2026-10-10.md`
