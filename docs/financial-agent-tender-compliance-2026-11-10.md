# 金融智能体招标 · 逐条应答与合规映射（2026-11-10）

> **定位**：可直接进标书 / 售前一页纸 / 联合体技术分包附件。
> **本期新增依据**：11-10 零壹智库《10 月金融智能体招标》全景数据 + 浙商证券 7 项强制测评依据清单 + 广发三份征集官方原文核实。
> **与既有文档分工**：[`gf-securities-tender-response-2026-11-07.md`](gf-securities-tender-response-2026-11-07.md) 管广发单标 12 条资质；[`bank-agent-platform-tender-response-2026-11-09.md`](bank-agent-platform-tender-response-2026-11-09.md) 管银行买底座 10 条应答；**本文管「拆单后的细分件怎么接」+「强制法规怎么逐条验收」**。

---

## 0. 一句话判读

10 月金融智能体招标 **23 条（环比 −1/3）**、中标 **15 条 / 披露金额合计 1652.8 万元（下半年新低）**，但**中标最大单不是智能体本体，是数据底座 350 万**；国投证券把一个需求拆成**四单**分别定标；12 家具名中标方**只有 2 家拿到两单**。

> **整包在收缩，件在变细，格局未定。** 对没有金融案例、没有 500 万注册资金、没有广州本地团队的开源框架，正确打法**不是投整包**，而是切「安全评测 / 治理模块 / 合规工件 / 持续供数」这些**独立编号、独立比价、可单独换供应商**的细分件，作为集成商的**技术分包**。

---

## 1. 拆单机会矩阵：8 个可独立比价的细分件

| # | 10 月真实标的 | 披露金额 | 买方逐字要求 | 本框架可交付工件（仓库真实路径） | 进入方式 |
| --- | --- | --- | --- | --- | --- |
| 1 | 浙江农商联合银行数据分析建模平台永久授权（中电金信 350 万，**本期最大单**） | **350 万** | 国产化适配、**动态脱敏**、数据运营管理、**行内智能体对接** | `mcp-tenant`（租户级数据源/RLS）、`mcp-gateway`（联邦接入）、`mcp-audit`（脱敏后参数留痕） | 数据底座厂商的 MCP 接入层分包 |
| 2 | 国投证券「大师诊股」智能体本体（今日投资 18.02 万） | 18.02 万 | 对客智能问答 / 个股诊断 | `mcp-server` REST + `mcp-tools` 工具注册 + `mcp-governance` 参数校验 | 小型定制件可直投或分包 |
| 3 | 国投证券诊股数据服务（今日投资 20.14 万） | 20.14 万 | **持续供数**、按服务期订阅 | `mcp-registry` 工具目录 + `mcp-gateway` 第三方数据源代理 | 数据服务分包（按年续约，长尾） |
| 4 | 国投瑞银基金「智能体外部资讯数据服务」 | **≤10 万/年**、不多于 3 家中标 | ≥80 人使用、行情/基金/工商/外部研报 | `mcp-gateway`（多源聚合）+ `mcp-auth`（按人授权） | 三家中标之一是低门槛切入口 |
| 5 | **浙商证券已部署智能体合规安全测评** | 未披露 | **附强制依据清单**（见第 2 节） | [`enterprise-rfp-checklist.md`](enterprise-rfp-checklist.md)（22 类攻击面 + 逐条验收方式）+ [`examples/monitor-alert-rules/`](../examples/monitor-alert-rules/README.md) + [`examples/audit-ropa-export/`](../examples/audit-ropa-export/README.md) | **★ 最适合本框架**：交付「测评依据→控制点→证据」映射表与可演示用例 |
| 6 | 中信建投订阅智能体安全检测工具 | 未披露 | 安全检测工具订阅 | `examples/governance-hardening/`（4MiB/fail-closed/同源重定向/会话按用户/工具默认禁用/按 caller 授权）+ `McpSecurityMetrics.java` 10 指标 | 工具订阅的技术组件供应 |
| 7 | 民生银行 AI 邮件安全智能体软件 / 上海银行大模型安全平台模块 | 未披露 | 安全平台**单列模块** | `mcp-governance` HITL + `mcp-audit` 不可篡改留痕 + `mcp-auth` | 模块增补，避开整包资质 |
| 8 | 常熟农商 / 中原 / 江苏农商联合银行新建平台管控条款 | 平台整包 | **A2A·MCP·CLI 对接内部系统** / **安全沙箱受控执行** / **大模型安全围栏 + 辅导完成算法备案** | `mcp-registry`（A2A+MCP 双协议）、`mcp-governance`（沙箱/围栏）、[`mcp-governance-dengbao-compliance.md`](mcp-governance-dengbao-compliance.md)（等保/备案） | 平台中标方的治理子模块分包 |

> **结论**：第 5 / 6 / 7 项与开源框架能力**重合度最高、资质门槛最低**，优先准备；第 1 / 8 项金额大但须绑集成商。

---

## 2. 浙商证券 7 项强制测评依据 → 逐条映射（可直接抄进应答表）

> 原文：招标文件**附了一份强制依据清单**，请第三方机构对**已部署**的智能体做合规安全测评。这是本期最重要的信号：**测评依据从「建议参考」变成「强制清单」**，治理工件第一次有了法定验收口径。

| # | 强制依据 | 本框架控制点 | 可验收证据（仓库真实文件） | 验收方式 |
| --- | --- | --- | --- | --- |
| 1 | `GB/T 45654-2025《网络安全技术 生成式人工智能服务安全基本要求》` | 请求体 4 MiB 上限、fail-closed、工具入参 Schema 校验 | [`examples/governance-hardening/BoundedRequestWrapper.java`](../examples/governance-hardening/BoundedRequestWrapper.java)、[`McpGatewayGuardFilter.java`](../examples/governance-hardening/McpGatewayGuardFilter.java) | 超 4 MiB 请求返回 413；非法 JSON 拒绝且不落业务 |
| 2 | `《人工智能安全治理框架 2.0》` | 工具**默认 disabled**、按 caller 差异化授权、HITL 写操作审批 | [`examples/governance-hardening/application-hardening.yml`](../examples/governance-hardening/application-hardening.yml)、`mcp-governance` HITL | 新注册工具默认不可调用；写工具无审批不执行（对位 `CVE-2026-123812`） |
| 3 | `《网络安全法》` | 鉴权全覆盖、Host/Origin allowlist、连接路径准入 | [`examples/connect-path-guard/McpConnectPathGuard.java`](../examples/connect-path-guard/McpConnectPathGuard.java)、[`McpResolvedIpGuard.java`](../examples/connect-path-guard/McpResolvedIpGuard.java) | 解析后真实 IP 校验 + IP 固定防 DNS rebinding（对位 `CVE-2026-123811`） |
| 4 | `《数据安全法》` | 最小 scope、token 不转发下游、读写分离 | [`docs/mcp-governance-regulatory-compliance-2026.md`](mcp-governance-regulatory-compliance-2026.md)、`mcp-auth` | 每请求校验 issuer/aud；下游换独立凭据；只读 token 不能触发写工具 |
| 5 | `《个人信息保护法》` | PII 脱敏、审计只存字段名、GDPR ROPA 七字段导出 | [`examples/audit-ropa-export/RopaExportService.java`](../examples/audit-ropa-export/RopaExportService.java) | JSON/CSV 导出 + 完整性自检；审计事件参数脱敏 |
| 6 | `《生成式人工智能服务管理暂行办法》` | 全量审计留痕、只写不删防篡改、traceparent 链路 | `mcp-audit`（`audit.store=jdbc` 单表 `mcp_governance_audit`）、`mcp-monitor` | 按 traceId 回溯一次调用链全部治理判定；重启不丢 |
| 7 | `《证券期货业网络和信息安全管理办法》` | 逐工具语句文法、启动期命令 allowlist、告警规则 | [`examples/tool-input-grammar/McpToolInputGrammar.java`](../examples/tool-input-grammar/McpToolInputGrammar.java)、🆕 [`examples/stdio-command-allowlist/McpStdioCommandAllowlist.java`](../examples/stdio-command-allowlist/McpStdioCommandAllowlist.java)、[`examples/monitor-alert-rules/mcp-security-alerts.yml`](../examples/monitor-alert-rules/mcp-security-alerts.yml) | 三个示例均 `javac` 编译 + `main()` 自检全绿（含对照组断言），可作**可演示案例** |

> **售前话术**：「贵方清单里 7 项依据，我方逐项给出**控制点 → 代码文件 → 可复现验收方式**，并附 4 组可现场跑通的自检（14 攻击 + 7 对照 + 3 边界）。不是承诺，是可验证工件。」
> ⚠️ **诚实边界**：上表是**技术控制点映射**，不构成「已通过第三方测评」的声明；本框架**尚无等保测评报告、无算法备案、无金融行业验收案例**，正式应答必须写清「配合甲方/第三方机构完成测评」，不得表述为「已合规」。

---

## 3. 广发三份征集 · 硬门槛自检（本期口径修正）

| 项目 | 截止 | MCP 相关要求 | 本项目自检 |
| --- | --- | --- | --- |
| `13321` 智能金融资讯数据服务平台（**延长至 13 号**） | **11-13 17:30** | MCP 工具协议 + Skill 技能包 + 子 Agent 编排 + 长期/会话记忆 + 混合检索；**沉淀可外部产品化的能力组件** | ⛔ 资质不满足（成立 3 年 / 注册资金 ≥500 万 / ≥1 个金融案例 / 原厂或授权代理）→ 只能作联合体技术分包 |
| 态势感知及安全分析平台扩容（NGSOC 奇安信） | **11-12 17:30** | 运营管理含「**MCP 安全工具管理**」+ 安全技能库管理 + **AI 安全数据底座**（统一日志模型 / 实体识别关联 / 高性能存储检索） | ⛔ 指定奇安信品牌扩容、需 ≥2 个安全项目案例 → 技术能力可支撑「MCP 安全工具管理」子模块，但不可独立应征 |
| `13322` 企业微信持续建设 | **11-15 17:30** | **MCP Server 五件套写进资质**（工具定义与描述 / 参数校验 / 鉴权与权限控制 / 调用限流 / 日志与可观测性）+ 数据按客户经理归属隔离 + **服务行为自动留痕、日志不可篡改** + 技能可复用可编排 + 指定 Dify/hiagent + **广州本地服务团队** + ≥2 个金融行业案例 | ⛔ 五件套技术能力 ✅ 全部具备（见 `gf-securities-tender-response-2026-11-07.md` 12 条逐条应答），但**本地团队/案例/资质 ⛔** |

> **⚠️ 本期修正**：V1.67 记为「11-12 / 11-13 两份」，官方原文核实为**三份**，13322 截止 **11-15 17:30**。三份均明确公告**不等于招标公告及资格预审公告**，「按时提交合格报名资料后，并不必然获得正式采购邀请」；提交材料需**可编辑电子版 + 加盖公章扫描件**（JPG/PNG/GIF/PDF），邮件至 `gfjc@gf.com.cn`，标题「广发证券XXX项目+供应商名称」，并与项目联系人确认送达。
> **纪律**：本项目无商业合同、无中标记录、无金融案例 → **严禁虚构案例、授权、完税凭证**；定位「联合体技术分包 / 供应商库技术资料提供方」，把 [`enterprise-cloud-gateway-comparison-2026-11-08.md`](enterprise-cloud-gateway-comparison-2026-11-08.md) 的 5 条买方问答 + 本文第 2 节 7 项映射压成 1 页 PDF 作为技术附件。

---

## 4. 买方必问 5 问（用 11-10 新证据回答）

| 买方问题 | 建议应答（带本期新证据） |
| --- | --- |
| 「三大云已经有 MCP 网关了，为什么还要你？」 | 云网关解决**连接与路由**，不解决**跨运行时签字、存量 Java 权限复用、私有化合规工件**。UiPath×Gemini 11-10 原话是「复用组织**已有**的 governance / permissions / audit controls」——这正是外挂治理层的价值；见 [`enterprise-cloud-gateway-comparison-2026-11-08.md`](enterprise-cloud-gateway-comparison-2026-11-08.md) 11 维对照。 |
| 「治理是不是你们自己说的？」 | 11-10 Dynatrace 双 CVE 是**同体内对照**：有 `requestHumanApproval` 的 `send_event` 挡住攻击，漏门的 `create_notebook` 成入口；广发把五件套写进**资质门槛**、浙商把 7 项法规写成**强制依据清单**。治理已是准入条件，不是营销词。 |
| 「写操作怎么保证不出事？」 | 全写工具强制 HITL + 工具元数据 `destructiveHint` 注册期校验 + 只写不删审计。T-Investments 11-10 把强制用户确认明确定位为**券商法律责任转移**的架构手段；Upwork 官方 MCP 同样要求 binding offer/付款回站人工确认。 |
| 「你们有金融案例吗？」 | ⛔ **如实回答：没有**。改为：提供可验证的**技术工件清单**（22 类攻击面验收表 + 4 组可现场跑通的自检 + 7 项法规逐条映射），作为联合体技术分包参与；资质与客户案例由联合体的本地集成商承担。 |
| 「多少钱？」 | 见下节三档模板；锚点用**买方自己的 10 月中标价**（细分件 18–20 万、平台升级 205 万、数据底座 350 万）而非美元销售页，更容易过预算评审。 |

---

## 5. 三档报价模板（建议值，非成交价）

> 锚点：**10 月国内真实中标披露价**（细分件 18.02 万 / 20.14 万 / 205 万 / 350 万）× **7 家 MCP 开发公司公开价**（$3K/月、≤$46/hr、$15K–$250K）× [`mcp-governance-pricing-anchor-2026-11-09.md`](mcp-governance-pricing-anchor-2026-11-09.md) 的治理层 10 倍定律。

| 档 | 交付范围 | 建议区间 | 对应 10 月同类标的 |
| --- | --- | --- | --- |
| **A · 测评/合规工件包** | 7 项强制依据映射表 + 22 类攻击面验收表 + 4 组可演示自检 + 1 次技术答疑 | **¥3–8 万** | 浙商证券安全测评、中信建投检测工具订阅 |
| **B · 治理子模块分包** | A + 网关过滤器/审计/限流/HITL 落地的参考实现与集成支持（2–5 周） | **¥15–40 万** | 国投证券「大师诊股」18.02 万、数据服务 20.14 万、上海银行安全平台模块 |
| **C · 治理底座联合体** | B + 多租户/私有化/等保适配 + 与平台中标方联合交付（6–14 周） | **¥80–200 万** | 国投证券平台升级 205 万、浙江农商数据底座 350 万（分包占比） |

**加价触发器**（命中即上浮一档）：① 要求算法备案辅导；② 要求等保三级 / 信创 / air-gapped；③ 要求已有 Java 权限模型迁移；④ 要求逐工具 HITL 与不可篡改留痕的第三方取证；⑤ 要求广州/本地驻场团队。

> ⚠️ 以上为**建议报价**，不是本项目已签订单或成交价；美元公开价来自卖方销售页，人民币中标价来自公开中标公告，两者**不可直接换算相加**。

---

## 6. 6 天行动清单（11-10 → 11-15）

| 日期 | 动作 | 产出 |
| --- | --- | --- |
| 11-10 今晚 | 把第 2 节 7 项映射 + 云网关对照 5 问 + 本期 3 个可演示示例压成 **1 页 PDF** | 技术附件 v1 |
| 11-11 | 厦大 11-11 09:00 截标（如未备材料则放弃）；核 4 组示例 `javac`+自检可现场跑通 | 演示脚本 |
| 11-11 ~ 11-12 | 联系广州本地集成商 1–2 家，明确「联合体技术分包」分工与资质承担方 | 联合体意向 |
| **11-12 17:30 前** | 态势感知扩容报名（如联合体成立） | 报名邮件 + 送达确认 |
| **11-13 17:30 前** | `13321` 报名（如联合体成立） | 报名邮件 + 送达确认 |
| **11-15 17:30 前** | `13322` 报名（如联合体成立），附技术附件 v1 | 报名邮件 + 送达确认 |
| 全程 | ⛔ 不虚构案例/授权/完税凭证；所有「已合规」表述改为「配合完成测评」 | 合规底线 |

---

## 7. 诚实边界（打印前必读）

- 10 月 23 条招标 / 15 条中标 / 1652.8 万元为**零壹智库不完全统计**（企业预警通底稿，月度序列回溯修订，9 月修订为 36 条），非全市场规模；12 条披露金额才计入合计。
- 本文所有「可交付工件」均为**开源参考实现 / 文档**，不等于已通过等保测评、算法备案或金融行业验收；**本项目无商业合同、无中标、无金融行业案例**。
- 广发三份为**供应商预征集**（非采购邀请），提交不必然获正式投标邀请；资质硬门槛不满足时**不得独立应征**。
- 报价为建议值，非成交价；`CVE-2026-123811/123812` 修版本号以官方 GHSA 为准。
- `mcp-monitor` 主模块尚未内置 `exportPrometheus()`；stdio command allowlist **不做进程隔离**，不能当沙箱。

---

## 来源

- [10月金融智能体招标降三分之一，中标金额创四个月新低 · 腾讯网 11-10](https://new.qq.com/rain/a/20261110A0DO1L00) / [零壹智库原文](http://mp.weixin.qq.com/s?__biz=MzA3MjEwMTQxNA==&mid=2650877860&idx=1&sn=4eff62a7ee78a55c5d52f668c448566f)
- [广发 13321（延长至 13 号）](http://gfjc.gf.com.cn/gyszjgg/13321.jhtml) / [态势感知扩容 11-12](https://www.gf.com.cn/web/article/detail?articleId=01a1aaecb5257570934897d5a51a3212&columnId=4) / [13322 11-15](http://gfjc.gf.com.cn/gyszjgg/13322.jhtml)
- [UiPath × Google Cloud Gemini for Healthcare 11-10](https://www.googlecloudpresscorner.com/2026-11-10-UiPath-Accelerates-Enterprise-AI-Transformation-with-Google-Clouds-Gemini-for-Healthcare)
- [CVE-2026-123811](https://aithreatalert.com/cve/CVE-2026-123811) / [CVE-2026-123812](https://aithreatalert.com/cve/CVE-2026-123812)
- [Best MCP Development Companies 2026（7 家公开报价）](https://www.ayautomate.com/blog/best-mcp-development-companies)
