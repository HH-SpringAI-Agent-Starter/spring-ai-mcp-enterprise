# MCP 兼职/外包报价单（Freelance Offer Sheet）

> 用途：Upwork / 国内兼职平台（程序员客栈、猪八戒、联智）/ 直接客户报价。基于 2026-08-24 市场雷达校准（参见 docs/market-research-2026-08-24.md）。
> 定价逻辑：以内置企业级安全框架（本项目 spring-ai-mcp-enterprise）降低交付成本，报价锚定市场价中位，以"Java 存量系统原生集成"为差异化。

## 一、三档标准报价

### 档位 1：单连接器加固 / PoC（$8K–$15K 或 ¥6万–¥11万）
- **范围**：1 个数据源/系统的 MCP Server（如 Postgres / CRM / 内部 REST API / 企业微信）。
- **交付**：类型化工具定义（3–8 个 tools）、OAuth2 客户端凭证认证、审计日志、限流、SSRF 防护、测试用例、Docker 部署、中文文档。
- **周期**：2–3 周。
- **对标**：iMagic PoC $8-15K、RonasIT Full $6.4K+。

### 档位 2：企业 MCP 平台（$25K–$60K 或 ¥18万–¥44万）
- **范围**：多系统 MCP 网关（5–15 个工具），RBAC 工具权限矩阵、API Key + OAuth2 双通道、Refresh Token 轮换、网关 Bearer 强制校验、Prometheus 监控、健康检查。
- **交付**：全套 mcp-core/mcp-server/mcp-monitor 组装 + 生产部署（Docker/k8s）+ 运维手册 + 30 天支持。
- **周期**：4–8 周。
- **对标**：Inventiple MCP Server $25-40K；iMagic 生产级 $15-40K。

### 档位 3：企业 Agentic 平台（$50K–$90K 或 ¥36万–¥65万，可谈）
- **范围**：多 Agent + MCP 工具层 + 人工审批（HITL）+ 多租户隔离 + 审计合规脚手架。
- **周期**：8–12 周。
- **对标**：Inventiple Agentic System $50-90K。

### 补充：驻场/顾问（按市场价）
- **远程合同**：$50–$82/hr（对标 ZipRecruiter 2026 合同价）。
- **国内驻场**：对标上海 MCP 平台开发岗 80-120万年薪（按 12 薪折算 ¥6.7万–¥10万/月）。
- **Fractional 架构顾问**：¥3万–¥5万/月（对标 Julia Tech $6K/月，含路线图评审 + 增量交付）。

## 二、报价锚定依据（2026-08-24 雷达）

| 服务商 | 报价 | 我方定位 |
| --- | --- | --- |
| iMagic Solutions | PoC $8-15K / 生产 $15-40K / 多租户 $40-80K | 同档，我方主推 Java 生态 |
| Inventiple | $25-40K / $50-90K / $90-180K | 同档上限参考 |
| Julia Tech | sprint from $15K / fractional $6K/月 | **与我方开源框架+个人交付模式最接近** |
| RonasIT | $3.2K+ / $6.4K+ / $10K+ | 低价档（TS 系，非 Java 存量） |

## 三、Scope 模板（跟单时直接改）

```
项目名称：____企业 MCP Server 建设（档位：____）
一、目标：将 ____系统（数据源/API）暴露为标准 MCP 工具，供 ____AI 客户端调用
二、交付物：
  1. MCP Server（工具数：____，传输：Streamable HTTP / SSE）
  2. 认证：□ OAuth2 Client Credentials  □ API Key  □ 二者共存
  3. 安全：□ 审计日志  □ 限流  □ RBAC  □ SSRF 防护  □ Refresh Token 轮换
  4. 可观测：□ Prometheus 指标  □ 健康检查  □ 调用统计面板
  5. 部署：□ Docker  □ docker-compose  □ k8s
  6. 文档：□ 架构说明  □ API 文档  □ 运维手册  □ 客户端示例（Java/Python/Node/curl）
三、验收标准：
  - 工具调用成功率 ≥ 99%（测试工具集）
  - 审计日志完整可追溯（谁/何时/何工具/入参出参）
  - 未授权调用 100% 返回 401/403
四、周期：____周  |  报价：____  |  付款：40%/30%/30%
五、不在范围：模型训练、多租户 SaaS 托管（可另议）
```

## 四、销售话术（客户最常见疑问）

- **"为什么是 Java？"** → 你们的 CRM/ERP/数仓是 Java 存量系统，Java 原生 MCP 框架无需跨语言胶水；Spring AI Alibaba 技术栈直接兼容（阿里官方生态）。
- **"和 Claude/ChatGPT 兼容吗？"** → 是。MCP 是开放标准，任何 MCP 客户端（Claude、Cursor、Copilot、自研 Agent）都能调用；我们提供 4 种语言客户端示例。
- **"安全怎么保证？"** → 开箱即用：OAuth2 全家桶（凭证+轮换+吊销+内省）、RBAC、全量审计、限流、SSRF 防护、网关强制校验——对应企业 RFP 安全条目 90% 覆盖率（见 docs/enterprise-rfp-checklist.md）。
- **"凭什么信你？"** → 开源项目 spring-ai-mcp-enterprise（GitHub 可审计），V0.1→V1.9 每个版本有 release notes 与测试。

## 五、发布渠道 CheckList（行动）

- [ ] Upwork profile 更新：Java + Spring + MCP Enterprise（等 Upwork 官方 MCP Server 生态开放后经 MCP 直连发单）
- [ ] 程序员客栈 / 猪八戒 / 联智：上传本报价单 + RFP 对照表
- [ ] 掘金/CSDN：发布《企业 MCP Server 建设成本拆解》引流（docs/blog-* 已有素材）
- [ ] 领英/LinkedIn：挂"MCP Enterprise 开源作者"身份接企业私信