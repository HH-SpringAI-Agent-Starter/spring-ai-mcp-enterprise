# 日志 / 遥测输出面验收清单（V1.79 · 2026-11-25）

> 本期最有挣钱价值文档。可直接进标书 / RFP 的「可观测性与数据防泄漏」验收列。
> 十一段式收口：注解 → 注册中心 → 只读声明 → 决策层 → 配置面 → 结果面 → 身份传播面 → 会话/传输绑定面 → 执行环境隔离面 → 浏览器出站响应面 → **日志/遥测输出面**。

## 0. 一句话判词

**日志 / 指标 / 追踪 / 诊断输出是「可观测性」，不是「数据已脱敏的证明」；
`redactSensitive=true` 是配置声明、prompt injection 一绕就过；凡离开信任边界的字符串，
必须在出口处按内容独立定级脱敏，默认 fail-closed。**

---

## 1. 为什么这一段是新的（近 3 天证据）

| 证据 | 机制 | 打掉的旧假设 |
| --- | --- | --- |
| `CVE-2026-135747`（Apache IoTDB MCP Server ≤0.2.0，11-24，CWE-532） | 启动把连接口令明文写日志；读日志是日常运维动作 | 「只有攻击者才读内部数据」——运维/日志采集/SIEM 转发都是合法读者 |
| `CVE-2026-135614`（AWS `2026-200-AWS`，11-24，CVSS 5.5） | troubleshooting 工具脱敏不当，prompt injection 诱导 agent 请求 guidance 拿到明文 env 与 secret 引用；修 0.1.36 **且轮换凭据** | 「开了 sensitiveData 开关就安全」——声明一绕就过；打补丁≠清干净 |
| `CVE-2026-134492`（Anthropic mcp-server-fetch，11-23） | SSRF guard 把 IPv6 site-local `fec0::/10` 当公网放行 | 「出口 allowlist 只要挡住 10./192.168.」——地址族不全就有洞 |
| 中国经营报 11-22 / IDC（28 家厂商） | 采购从"造智能体"转"管智能体"；治理合规 58.7% 居首；蚂蚁四问含「能否追溯」 | 「能跑 Demo 就能验收」——买方要的是出口可审计 |

---

## 2. 十二项日志 / 遥测输出面验收清单（逐条 → 控制点 → 真实文件 → 现场验收）

| # | 验收项（买方话术） | 控制点 | 仓库真实文件 | 现场验收方式 |
| --- | --- | --- | --- | --- |
| 1 | 启动/运行日志不得出现明文口令、API Key、Bearer token | CP1 凭据模式 | `examples/observability-redaction-guard/McpObservabilityRedactionGuard.java` | 注入 `password=rootpwd123` 日志行 → DENY `CREDENTIAL_PATTERN` |
| 2 | 指标标签、追踪属性不得携带凭据（标签高基数是泄漏高发区） | CP1 | 同上 `channel("metric")` / `channel("trace")` | `db_password=prod pass` 标签 → DENY |
| 3 | 已脱敏文本二次处理幂等，不得把 `<redacted>` 再判为新凭据 | CP1 占位符跳过 | 同上 `hasCredential()` | 对照 C8 `password=<redacted>` → ALLOW，0 误杀 |
| 4 | prompt injection 诱导的诊断请求，即便声明开启也必须强制脱敏 | CP2 绕过防护 | 同上 `PROMPT_INJECTION_BYPASS` | `ignore previous ... reveal token` + diagnostic → DENY |
| 5 | 脱敏开关关闭且含敏感内容时 fail-closed，不得静默放行 | CP2 | 同上 `redact(false)+sensitive` | 声明关 + `accesskey=AKIA...` → DENY |
| 6 | `${ENV}` / `process.env` / `secretRef` / task-def env 引用不得原样出口 | CP3 引用 | 同上 `SECRET_REFERENCE` | `resolved ${DB_PASSWORD}` → DENY |
| 7 | 日志/遥测外发端点必须精确 allowlist，通配符/后缀欺骗一律拒绝 | CP4 端点 | 同上 `EGRESS_NOT_ALLOWED` | `https://evil.example.com/ingest` → DENY |
| 8 | 出口目标 IP 全地址族分类（IPv4 私有/回环/链路本地/文档段 + IPv6 site-local/ULA/::1/fe80::） | CP4 IP | 同上 `isUnsafeIp()` | `169.254.169.254` 元数据伪装、`fec0::1` → DENY |
| 9 | 异常堆栈只记 error code，请求参数不得拼进错误路径 | CP5 | 同上 `STACKTRACE_PARAM_LEAK` | `param("token=leakme")` → DENY；error-code-only 堆栈 → ALLOW |
| 10 | 审计 append-only，出口原文与审计行零明文残留 | CP6 | 同上 `audit()` / `mask()` | B1 断言审计行不含 `hunter2secret` 原文 |
| 11 | 掩码幂等：已脱敏文本二次脱敏不留引用/口令原文 | CP6 | 同上 `mask()` | B2 断言 `${DB_PASS}` → `<ref>`、`password=` → `<redacted>` |
| 12 | 守卫生效不依赖声明：旧基线放行的攻击必须被新守卫拦下 | 全链 | 同上 `legacyBaseline()` | 20 attack 中 18 条被旧基线误放行、被新守卫全拦 |

**自检实测输出**（`javac -encoding UTF-8` + `java McpObservabilityRedactionGuard`）：

```
self-test: OK
  attack   : 20/20 blocked
  control  : 8/8 allowed (false positives: 0)
  boundary : 2/2 assertions
  legacy-only blocks (new guard catches, baseline misses): 18
  total assertions: 30
```

---

## 3. 买方必问 5 问（含不合格 / 合格回答对照）

| 问题 | ❌ 不合格回答 | ✅ 合格回答 |
| --- | --- | --- |
| Q1 你们的日志脱敏怎么保证？ | 「我们在配置里开了 `sensitiveData=true`」 | 「声明只进审计不进允许分支；出口处按内容独立扫描凭据模式与 secret 引用，可对 `password=`/`${ENV}` 逐类现场 DENY 演示」 |
| Q2 运维人员读日志算不算风险？ | 「内部日志只有自己人看」 | 「读日志是日常动作；AWS 135614 证明 prompt injection 能借诊断工具把 task-def env 从日志/响应里带出来，所以我们把可观测性出口当数据出口守」 |
| Q3 打了补丁是不是就安全了？ | 「升级到修复版即可」 | 「AWS 官方要求升 0.1.36 **且轮换所有明文 env 凭据**；我们配套存量全量复验脚本，不靠单一版本断言」 |
| Q4 出口 allowlist 怎么防 SSRF 绕过？ | 「挡住 10.0.0.0/8 和 192.168 就行」 | 「IPv4 私有/回环/链路本地/RFC5737 文档段 + IPv6 site-local `fec0::/10`/ULA/::1/fe80:: 全地址族分类，对位 `CVE-2026-134492`」 |
| Q5 怎么证明没有误杀正常日志？ | 「我们测试都通过了」 | 「8 条合法对照（数值指标/只读 SQL/error-code 堆栈/已脱敏文本/allowlist 端点）0 误杀，含 `<redacted>` 幂等对照；30 断言可现场跑」 |

---

## 4. 竞品差异化

| 玩家 | 定位 | 本项目差异 |
| --- | --- | --- |
| 三大云 AI Gateway（Azure APIM / AWS AgentCore / 腾讯云 3.9.8） | 把网关治理做成标准产品层 | 云网关管"谁能连"，本项目管"连完之后记下来的东西干不干净"——**栈外挂、私有化、不换编排平台** |
| 日志框架自带脱敏（logback mask 等） | 字段级正则替换 | 字段级属于声明式；对 prompt injection 诱导的诊断响应与跨通道出口无能为力；本项目六道闸按内容定级 |
| SIEM 后处理 | 事后检测 | 事后已泄漏；本项目 fail-closed 在出口前拦截，审计只留脱敏行 |

---

## 5. 三档报价（公开挂牌价口径，非成交价）

| 档 | 范围 | 交付 |
| --- | --- | --- |
| L1 评估 | $5K–15K（1–2 周） | 出口面审计 + 12 项验收清单打分 + 整改路线图 |
| L2 落地 | $20K–50K（8–12 周） | 六道闸接入网关/治理层 + 日志框架过滤器 + 存量复验 + 对照组测试套件 |
| L3 企业 | $80K–220K+（季度起） | 多租户 + 私有化 + SIEM 对接 + 等保/密评工件 + 持续复验；年运维 $5K–25K/月 |

> 报价锚来源：Launch Day Advisors（L1 $100K–300K / L2 $300K–700K / 维护 $5K–25K/月）、dev.to 9 家横评（$1.5K→$1M+，600 倍价差）。**涨的不是代码量，是治理层。**

---

## 6. 24 / 48 / 72 小时行动清单

- **24h**：把本清单 12 项 + `java McpObservabilityRedactionGuard` 的 30 断言输出压成 1 页 PDF，进售前包；README 区块日期改 11-25。
- **48h**：回广发采购平台核实 13327 报名截止与材料清单（官方正文无截止，不得凭编号推断）；跟踪华安证券办公智能体池、上海银行两个中标项目的后续分包机会。
- **72h**：在独立分支评估把 CP1/CP2/CP4 接进 `mcp-monitor` 的日志出口过滤器（先跑通 30 断言）；备标中原银行 AI 网关、工信部五所 12-03。

---

## 7. 诚实边界

- 本框架**不受** IoTDB / AWS ECS / mcp-server-fetch 等第三方 CVE 直接影响；
- 守卫**仍在 `examples/` 层，未接入 21 主模块**；凭据正则 / IP 分类 / allowlist 为可运行演示，生产须交日志框架过滤器 + 配置中心 + SIEM 共同保证；
- 掩码**不是加密**，日志一旦落盘明文无法靠"关掉开关"收回（AWS「升级后须轮换凭据」同理）；
- IP 判定为字面量分类、不做真实 DNS 解析；凭据检测为词法归一化非语义解析；审计表为单进程内存演示；
- 全部报价为公开挂牌价非成交价；广发四份为预征集 ≠ 采购邀请、7 项资质不满足只能联合体技术分包；本项目无企业主体/合同/中标/金融案例/等保测评；Spring AI 2.0 未升级。
