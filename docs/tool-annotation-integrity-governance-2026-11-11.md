# 工具注解完整性治理：从「工具目录」到「注册期准入」（V1.69 售前 / 投标素材）

> 日期 2026-11-11 · 定位：**可直接进标书「威胁建模 / 安全设计 / 技术应答」章节**，也可作售前 Q&A 一页纸。
> 结论：**`annotations` 不是安全边界**。把工具风险分成 READ / WRITE / DESTRUCTIVE 并绑定审批门的动作，必须发生在**服务端注册期**，且必须在**工具目录变更后重新做**。

---

## 一、为什么买方现在会问这个（四股力量，11-09 ~ 11-11）

| 力量 | 事实 | 买方的下一个问题 |
| --- | --- | --- |
| **生态基线** | [INITE Atlas 11-11](https://inite.ai/en/atlas/mcp/report)：11,393 servers，**25% 缺 hint**、**4% 谎报非破坏性**、**23% 免登录仍改数据**、47% 参数无描述 | 「你们怎么证明接进来的第三方工具没有谎报？」 |
| **主流框架** | [Microsoft Agent Framework Python 1.25.0（11-09）](https://www.cloudproinc.com.au/index.php/2026/11/11/microsoft-agent-framework-mcp-approval-policies-governance-guide)：callable discovery approval policies + **catalogue 刷新后复验审批**；指南逐字「metadata ... an input to your policy rather than the final security decision」 | 「上游目录改了描述，我们已有的审批还作数吗？」 |
| **上架规则** | [OpenAI 三个 hint 必须显式 boolean](https://dawloom.com/blog/submit-mcp-server-to-chatgpt-and-claude) / Anthropic 用 read/write hint 决定 auto-permissions 且**拒收 catch-all** | 「只读免确认」一旦被谎报就是静默越权，谁兜底？ |
| **云厂商 + 监管** | [Google Cloud Agent Registry（更新 11-10）](https://docs.cloud.google.cn/agent-registry/manage-mcp-tools) 逐工具 IAM；[中信银行 11-11](https://m.10jqka.com.cn/20261111/c681716629.shtml) 下一代 App「Agent+Skill+MCP」且生成式内容**必须人工审核或预分析后调用** | 「受监管场景里，人工确认能不能被 override 绕过？」 |

**一句话应答**：

> 我们不把工具声明当授权依据。`readOnlyHint` / `destructiveHint` / `openWorldHint` 只是**待审材料**，服务端独立定级、注册期校验、发现期按风险分档审批、目录变更强制复验、运行期 fail-closed 强制门，全部留痕。这是 `mcp-registry` + `mcp-governance` + `mcp-auth` + `mcp-audit` 四层的分工，不是某个 SDK 的配置开关。

---

## 二、四家规则矩阵（写标书「标准符合性」时逐条对位）

| 规范 / 平台 | 硬规则 | 本方案控制点 | 仓库里的真实文件 |
| --- | --- | --- | --- |
| **MCP 规范 / 信任模型** | annotations「should be considered untrusted」；[Hint 后缀是刻意设计，注解仅供参考不构成安全约束](https://devpress.csdn.net/aibjcy/6af303c1e5f4ca4eff8fa677.html)；「Host 信任用户、不信任 Server」 | 注解**不参与放行判断**；`classify()` 独立定级 | [`examples/tool-annotation-gate/McpToolAnnotationGate.java`](../examples/tool-annotation-gate/McpToolAnnotationGate.java) |
| **OpenAI 目录** | 三个 hint **显式 boolean**；逐操作独立工具；输出不含 session/trace/request IDs；隐私政策披露 | 注册期第 1 条：`MISSING_EXPLICIT_HINTS` 拒收；第 5 条：`CATCH_ALL_TOOL` 拒收 | 同上 + [`examples/audit-ropa-export/RopaExportService.java`](../examples/audit-ropa-export/RopaExportService.java) |
| **Anthropic 目录** | `title` + read/write hint 决定 auto-permissions；**禁 catch-all**；destructive 必弹窗 | READ 才自动放行；WRITE/DESTRUCTIVE 强制人工审批流水号 | 同上 |
| **Microsoft Agent Framework 1.25.0** | callable discovery policies；tool-name / read-only 过滤；caller override；**catalogue 更新后 refresh approval decisions** | `grantApproval()` 绑定 fingerprint；`refreshCatalogue()` 变更即作废；override 记录 who / bypassed which policy / reason / outcome | 同上 |
| **Google Cloud Agent Registry** | 中央注册表 + Gateway **逐工具 IAM** | `mcp-registry` 注册准入 + `mcp-governance` 逐工具 scope + `mcp-auth` 服务端身份 | `mcp-registry/` `mcp-governance/` `mcp-auth/` |
| **PowerDuck / 工程共识** | 「hints, not enforcement」「**Overstating safety is more dangerous than overstating risk**」「写外部系统也是写」 | 谎报只读 / openWorld 谎报一律拒；副作用动词表覆盖中英文 | 同上 |
| **Harriet 11-09 网关指南** | 网关四职责：决定访问 / 检查 connector / 持有凭据 / 记录调用 | `mcp-gateway` + `mcp-auth` + `mcp-audit` 三层 | `mcp-gateway/` `mcp-audit/` |

---

## 三、注册期验收清单（**可直接复制进标书**，逐条给验收方式）

| # | 验收项 | 控制点 | 模块 | 验收方式（现场可跑） |
| --- | --- | --- | --- | --- |
| 1 | 三个 hint 必须显式 boolean | 缺一拒收 `MISSING_EXPLICIT_HINTS`；缺 hint 按 `DESTRUCTIVE` 最坏档推断 | `mcp-registry` | 提交缺 `destructiveHint` 的工具定义，注册响应必须为 REJECT 且落审计 |
| 2 | 声明自相矛盾 | `readOnlyHint=true && destructiveHint=true` 拒收 | `mcp-registry` | 提交矛盾声明必须 REJECT |
| 3 | **谎报只读** | 名称/描述/参数含副作用动词（中英文）却声明只读 → `READONLY_LIE` | `mcp-registry` | 提交 `{"name":"delete_customer","readOnlyHint":true}` 必须 REJECT |
| 4 | **谎报 openWorld** | `readOnlyHint=true && openWorldHint=true` → `OPENWORLD_LIE` | `mcp-registry` | 提交「读远程发票」标只读必须 REJECT |
| 5 | catch-all 工具 | `api_request` 类 + `method`/`action`/`operation` 参数 → `CATCH_ALL_TOOL` | `mcp-registry` | 提交带 `method` 参数的通用请求工具必须 REJECT |
| 6 | 整包环境变量读取 | `get-env` 类工具默认禁用 | `mcp-governance` | 调用 `get-env` 必须 DENY |
| 7 | **独立风险定级** | `classify()` 不采信声明 hint；删除类恒为 `DESTRUCTIVE` | `mcp-governance` | 把 `delete_customer` 三个 hint 全改成只读友好值，定级仍须为 DESTRUCTIVE |
| 8 | 服务端身份 | 不接受模型自称 `role=admin`，只认认证后 `subject` | `mcp-auth` | 无 `subject` 的调用必须 DENY |
| 9 | 写工具人工审批 | WRITE/DESTRUCTIVE 必须带绑定 `tool+fingerprint+subject` 的审批流水号 | `mcp-governance` | 无 `approvalId` / 伪造流水号必须 DENY |
| 10 | **审批器 fail-closed** | 审批器缺失 / 抛异常 / 返回 false 一律 DENY | `mcp-governance` | 注入抛异常的 approver，调用必须 DENY |
| 11 | **目录漂移复验** | 描述/参数/hint 任一变更 → 旧审批立即作废 | `mcp-registry` + `mcp-monitor` | 审批后改写描述，用旧流水号调用必须 DENY |
| 12 | override 管控 | DESTRUCTIVE 禁 override；WRITE override 必须带理由并审计绕过的是哪条策略 | `mcp-governance` + `mcp-audit` | 无理由 override / 破坏性 override 必须 DENY |
| 13 | 审计完整性 | append-only；密钥值脱敏；**拒绝分支同样留痕** | `mcp-audit` | 传入 `api_key=sk-live-...` 后审计串不得出现明文 |
| 14 | 传输层准入（配套） | loopback-only / Content-Type / Origin / Host / 禁 wildcard CORS / 未鉴权 401 | `mcp-gateway` | [`examples/local-listener-guard/`](../examples/local-listener-guard/README.md) 17 攻击用例现场跑 |

> 现场演示命令（纯 JDK 17，零依赖，不进 reactor）：
> ```bash
> javac -encoding UTF-8 -d /tmp/mcp-selftest \
>   examples/tool-annotation-gate/McpToolAnnotationGate.java \
>   examples/local-listener-guard/McpLocalListenerGuard.java
> java -cp /tmp/mcp-selftest com.example.mcp.registry.McpToolAnnotationGate
> java -cp /tmp/mcp-selftest com.example.mcp.transport.McpLocalListenerGuard
> ```
> 实测：`17 attack + 9 control + 6 boundary`（32 断言）/ `17 attack + 9 control + 4 boundary`（30 断言），均全绿。

---

## 四、竞品差异化（**买方一定会拿这几家来比**）

| 对手 | 它做到什么 | 它没做什么 | 我们的应答 |
| --- | --- | --- | --- |
| [mcp-gatehouse v0.5.0](http://hol.org/registry/plugins/nickgeorgeseo%2Fmcp-gatehouse)（Python，11-07） | READ/WRITE/DESTRUCTIVE 分层 + fail-closed 审批 + append-only JSONL + 脱敏 + denylist | **单 server 内嵌 Python 库**；无目录漂移复验；无多租户 / 网关 / 跨模块审计工件 | 「它守一个 server，我们守整个企业的工具接入面；且 catalogue 变更后旧审批会自动作废」 |
| 云厂商（Google Cloud Agent Registry / Azure APIM / AWS AgentCore / 腾讯云 MCP 网关） | 逐工具 IAM、中央注册表、产品化策略 | 绑定自家云；私有化/信创/等保场景不可搬；策略原语有 lock-in | 「平台做编排，外挂 Java 治理层；跨运行时、跨云、私有化同一套策略」 |
| 开源网关（IBM ContextForge / Docker MCP Gateway / Kong / Portkey） | 流量入口、代理、可观测 | 清一色**非 Java 栈**，治理多在 Enterprise 付费墙 | 「存量 Java 权限模型直接复用，不用重写一套策略引擎」 |
| 官方 SDK / 目录上架审核 | 规范、hint、自动扫描 | 明确「hint 不是 enforcement」；Anthropic STDIO 架构缺陷「Won't Be Patched」 | 「升级官方 SDK ≠ 安全；责任在集成方，我们把责任做成可验收的层」 |

---

## 五、买方必问 5 问（逐条话术）

| 买方问题 | 建议回答（不要多说，直接给验收方式） |
| --- | --- |
| **Q1「你们怎么防止第三方 MCP server 谎报工具是只读的？」** | 注册期不采信声明：三个 hint 必须显式、矛盾/副作用动词/openWorld 谎报/catch-all 四类一律拒收；通过者由服务端 `classify()` 独立定级。验收：把 `delete_customer` 三个 hint 全改成只读友好值，定级仍为 DESTRUCTIVE。 |
| **Q2「工具描述被上游改了，之前的人工审批还算数吗？」** | 不算。审批流水号绑定 `toolName + SHA-256(元数据) + subject`，`refreshCatalogue()` 检出任何变更立即作废旧审批。验收：审批后改写描述，用旧流水号调用必须 DENY。对应 MS Agent Framework 1.25.0 的 catalogue refresh 要求。 |
| **Q3「只读工具也会读敏感数据，你们怎么分级？」** | 同意「read-only ≠ harmless」。READ 只是**不做写操作**的档位，不等于免审计；数据分类字段（`public` / `internal` / 敏感）保留在工具契约里，按 server + tool + data classification + caller + environment 多因素决策，避免「全量审批」造成 approval fatigue。⚠️ 诚实说明：基于数据分级的差异化策略**本版示例只留了字段、未实现引擎**，属下一版交付项。 |
| **Q4「审批能被业务方 override 吗？」** | 可以，但只对 WRITE，且必须带理由、记录「谁、绕过哪条策略、理由、最终结果」；DESTRUCTIVE 一律禁止 override。审计 append-only 且密钥值脱敏，拒绝分支同样留痕。 |
| **Q5「绑在 127.0.0.1 还需要这么复杂吗？」** | 需要。Siemens 11-10 `CVE-2026-112136/112137` 与 Dynatrace `123811` 都证明浏览器标签页能打到 localhost；`text/plain` 可绕过 CORS 预检、DNS rebinding 可绕过绑定地址。除 loopback 外还须 Content-Type 严格 `application/json` + Origin/Host 白名单 + 禁 wildcard CORS + 未鉴权 401。验收见 `examples/local-listener-guard/` 17 攻击用例。 |

---

## 六、广发三标 · 24 / 48 / 96 小时行动清单（**今晚最该做的事**）

| 时间 | 动作 | 交付物 |
| --- | --- | --- |
| **今晚（11-11）** | 把本文第三节 14 项验收清单 + 云网关对照 + 报价锚压成 **1 页 PDF**；把两个示例的 `javac` + 自检输出截图存档 | 报名附件《工具注册期治理可验收清单》1 页 + 可演示证据 2 张 |
| **11-12 17:30 前（剩 1 天）** | 《态势感知及安全分析平台扩容》报名：突出「**MCP 安全工具管理**」= 注册期准入 + 24 类攻击面；核原厂/授权代理、≥2 金融案例 + ≥2 安全项目案例 | 报名材料包；资质不足**立即**联系广州本地集成商组联合体 |
| **11-13 17:30 前（剩 2 天）** | `13321` 智能金融资讯：突出 MCP 工具协议 + Skill + 子 Agent + 混合检索中「工具接入层」的统一策略 | 逐条应答包（沿用 `gf-securities-tender-response-2026-11-07.md`） |
| **11-15 17:30 前（剩 4 天）** | `13322` 企业微信：五件套逐条对位 + 按客户经理归属隔离 + 日志不可篡改 + 兼容指定 Dify/hiagent | 逐条应答包 + 跨运行时策略说明 |

⚠️ 三份均为**供应商预征集 ≠ 采购邀请**；本项目**无商业合同 / 无中标 / 无金融案例 / 无等保测评**，建议作联合体技术分包，**禁虚构材料**。「MCP 安全工具管理」正是本期注册期治理最直接的应答位。

---

## 七、三档报价模板（沿用 V1.67 报价锚，本期新增「注册期治理」加价项）

| 档位 | 范围 | 价格锚 | 注册期治理增量 |
| --- | --- | --- | --- |
| **POC / 单 server** | 1 个 MCP server + 基础鉴权 + 日志 | $3.7K–15K（公开报价下限档） | 三 hint 校验 + 独立定级（1 天） |
| **生产级 / 多系统** | 多 server + SSO + RBAC + 逐工具权限 + audit + versioning | **$80K–220K** + 年运维 10–30% | **+目录漂移复验 + override 留痕 + 脱敏审计**（约 10–15% 增量，按「可验收项」计） |
| **企业网关 / 受监管** | 跨运行时 + 私有化 + 等保/密评 + 24 类攻击面验收 | **$250K 起** | 全量 14 项验收清单 + 现场演示 + 合规工件导出 |

**加价触发器**（沿用）：多租户隔离、监管行业（金融/医疗）、跨运行时（买方指定 Dify/hiagent 等）、私有化 + 等保/密评、要求可复现验收证据。
⚠️ 全部为**卖方销售页公开价，非成交价**；本项目**尚无商业合同**。

---

## 八、诚实边界

- 本文所有控制点对应的是 **`examples/` 下的参考实现**，尚未接入 `mcp-registry` 主模块、未进 Maven reactor；把它写进标书时应表述为「**参考实现 + 可现场演示的验收方式**」，不得表述为「产品已内置」。
- 副作用动词表是保守启发式，不是语义证明；生产需叠加 per-tool 语句文法（[`examples/tool-input-grammar/`](../examples/tool-input-grammar/README.md)）与人工复核，合法 `POST /search` 需白名单豁免。
- fingerprint 只覆盖**元数据**，不覆盖工具实现代码；实现被替换而描述不变时无法检出。
- 基于 `dataClassification` 的差异化审批策略**本版只保留字段、未实现引擎**（Q3 已如实说明）。
- `McpLocalListenerGuard` 是请求元数据判定器，不是 HTTP server；rebinding 后缀黑名单不能替代解析后真实 IP 校验（见 `McpResolvedIpGuard`），重定向逐跳重校验与 SNI/Host 一致性仍为跨期待办。
- INITE 的「25% / 4% / 23%」是**公开普查的描述与 hint 自动比对**，不等于已被利用；52% 免登录包含刻意公开的数据服务。
- [MCP v2 定稿等时间线](https://www.kwkr.cn/news/48305.html)来自二手站点，**待官方仓库复核**，本文未将其作为符合性依据。
