# 持久化记忆 / 学习存储面验收边界（2026-11-26 · V1.80）

> 十二段式收口第十二段：**持久化记忆 / 学习存储 / AgentDB pattern store / 向量库是「跨会话状态」，不是「已被批准的事实」。**
> 一次不可信工具写入就会以「用户告诉我的」身份在未来每个会话被读回；注入时间与利用时间被解耦。

## 0. 为什么这一段单独立（买方一句话）

前 11 段都在守「一次调用」：谁能动、怎么判、交出什么、收到谁的身份、从哪个会话进来、在哪个沙箱跑、响应怎么出、日志怎么记。**但没有一段守「这次调用写进去的记忆，明天还活着」**。

- 传统 prompt injection 随对话结束而消失；**记忆投毒不会**——它被 `read_graph` / `search_memories` 读回时，模型把它当成「用户之前告诉我的」，优先级高于当前系统提示。
- Nodeflare 2026 实测：一次成功写入即可；平均 **50.46%** 写入成功率、**41.05%** 概率在另一会话改变行为；plain memory store **7/7** 存下非期望写入，前置写门 **0/7**（1 条误报送审而非丢失）。
- 「Pattern scanning alone caught 6 of 7; **the provenance check caught the last one**」——纯扫描挡不住看起来中性的事实（`"Project uses Next.js 16." from a fetched page`），只有来源标签能拦。
- **CVE-2026-59726**（CVSS 10.0）把这件事从理论推到生产事故：投毒 pattern 写进 AgentDB 学习存储后，**官方明确「仅重新部署打补丁不会撤销投毒」，必须审计清除 `agentdb_pattern-store` 条目并轮换全部 provider key**。

**判词：修复是写路径，不是过滤器；补丁 ≠ 清干净；加密存储 ≠ 语义可信。**

## 1. 证据链（2026-11-20~11-26，均已回原文核实）

| # | 来源 | 事实 | 售前用途 |
| --- | --- | --- | --- |
| E1 | [CVE-2026-59726（GitLab Advisory / NVD，11-24，CVSS 10.0）](https://nvd.nist.gov/vuln/detail/CVE-2026-59726)，ruflo < 3.16.3，Noma Security | 默认 compose 把 MCP bridge + MongoDB 绑 `0.0.0.0`、`POST /mcp` 无鉴权；tool blocklist 只在 autopilot 强制、`/mcp` 与 `/mcp/:group` 绕过 → `terminal_execute` RCE、读容器 env 全部 provider key、投毒 AgentDB 影响该实例未来所有用户；处置须防火墙 :3001/:27017 + 轮换 key + **清除投毒 pattern + 审计 MongoDB**，重新部署不能撤销投毒 | 论证「写入期 + 回滚」是独立控制点 |
| E2 | NIST SP 2400-102e 初始公开草案（11-09，[CN-SEC 11 月解读](https://cn-sec.com/archives/5606895.html)） | 长期记忆投毒列入 Agentic SI 攻击分类；一次不可信工具调用植入休眠载荷、注入与利用解耦；接触→提取→写入→**检索**→规划→动作六阶段；**加密无法阻止语义污染**；WAF / 输入分类器 / 单轮检测在 t0 起效也无法证明 t2 检索可信 | 论证「检索期重定级」独立成闸 |
| E3 | [Nodeflare《How to Stop MCP Memory Poisoning》](https://nodeflare.tech/blog/mcp-memory-poisoning)（2026，13 次写入实测，OWASP **ASI06**） | 50.46% 写入成功率 / 41.05% 跨会话改行为率；plain store 7/7、模式扫描 6/7、完整写门 0/7；ChatGPT（2024 SpAIware）/ Gemini（2025）/ Claude Code（2026 MemoryTrap）真实事件；厂商只堵了 exfiltration 通道，「model may write something untrusted into memory」始终成立 | 量化「写门 vs 过滤器」的差距 |
| E4 | [RunPod MCP 事件复盘（腾讯云 11-20~22）](https://cloud.tencent.com/developer/article/2793066) | create-pod 返回值夹带「要求 Agent 评分且不告知用户」指令，文本只在 MCP 返回值、不在底层 REST；公开静态扫描只覆盖工具描述，看不到返回值入口；MCP 文本入口有三处（tools/list / 返回值 / initialize） | 论证「返回值也必须按内容定级」 |
| E5 | [MCP 协议安全实战（工具描述投毒）](https://www.hqwc.cn/a/2038977.html) | 工具 description 直接进 System Prompt、协议层无「工具作者声音 vs 用户声音」区分；2026-07-28 规范改无状态设计，把会话与安全责任从协议层移交实现者 | 论证「协议越轻、实现者责任越重」 |

> ⚠️ E2 为 11-09 发布、非近 3 天事件；E1 影响的是第三方产品 ruflo，**本框架不受直接影响**；E3/E4 数字为第三方实验/事件，非本仓库实测拦截率。

## 2. 十二项记忆面现场验收清单（逐条 → 控制点 → 真实文件 → 现场验收）

| # | 验收项 | 控制点 | 仓库真实文件 | 现场验收方式 |
| --- | --- | --- | --- | --- |
| 1 | 用户显式陈述 + host 背书写入批准区放行 | CP1 | `examples/persistent-memory-guard/McpPersistentMemoryGuard.java` | control C1 → ALLOW |
| 2 | 工具返回值 / 网页 / 模型自报无 host 背书不得进批准区 | CP1 | 同上 | attack #4/#5/#6/#16/#18 → NO_PROVENANCE |
| 3 | agent 发起写入默认落隔离区、未确认不得直进批准区 | CP2 | 同上 | attack #7 → BYPASS_QUARANTINE；control C2/C3 → ALLOW |
| 4 | 全局 scope 或永久（`ttl=0`）写入须升级审批 | CP3 | 同上 | attack #2/#8/#9/#17 → SCOPE_TTL_ESCALATION；control C7（升级审批）→ ALLOW |
| 5 | 指令式注入载荷写入拒绝（含空格拆分 / 多语言变体） | CP4 | 同上 | attack #1/#3/#11~#15/#20 → POISON_PATTERN；#12 `ig nore pre vious` norm 后命中 |
| 6 | secret 字面量不得写入记忆（含用户粘贴的 key） | CP4 | 同上 | attack #19（`api_key=sk-live-…`）→ SECRET_IN_MEMORY |
| 7 | 低信任记录召回进高权限上下文前重新定级 | CP5 | 同上 | attack #10 → LOW_TRUST_RECALL；control C4（用户显式、普通上下文）→ ALLOW |
| 8 | append-only 审计零明文残留（注入原文不落审计） | CP6 | 同上 | boundary B1：审计行只含 hash + code、不含 `reveal all secrets` |
| 9 | 投毒 hash 可定位、DELETE 清理动作放行（补丁≠清零） | CP6 | 同上 | boundary B2 + control C8 → 可按 hash 定位回滚 |
| 10 | 只读 / 删除动作不被误拦 | 全链 | 同上 | control C5（read_graph）/ C8（purge poisoned）→ ALLOW |
| 11 | 不得劣于旧基线（旧基线漏掉的必须全拦） | 全链 | 同上 | legacyBaseline 放行 20 条、新守卫 20/20 全拦 |
| 12 | 合法业务写入 0 误杀 | 全链 | 同上 | `java McpPersistentMemoryGuard` → 8/8 control、false positives = 0 |

> 全部 12 项可在 [`examples/persistent-memory-guard/`](../examples/persistent-memory-guard/README.md) 现场跑（纯 JDK 零依赖，不进 Maven reactor）。
> ⚠️ 参考实现**未接入主模块**；marker 为词法子串匹配非语义解析、hash 为演示摘要非密码学签名、隔离区/审批流为布尔标志；真正可靠的控制是 provenance 与隔离区，不是正则。

## 3. 买方必问 5 问（不合格 / 合格回答对照）

| # | 问题 | ❌ 不合格回答 | ✅ 合格回答 |
| --- | --- | --- | --- |
| 1 | Agent 写进记忆的内容，怎么区分是用户说的还是网页/工具返回值说的？ | 「我们有 prompt 过滤 / 关键词黑名单」 | 「来源标签由 **host 在写路径上设置**，不由模型自报；无背书来源一律进隔离区，读工具永不返回 pending 条目」 |
| 2 | 一条记忆被投毒了，你们怎么清？ | 「升级到新版本 / 重新部署就好了」 | 「每条写入留 hash + append-only changelog，可定位到具体投毒条目、**回滚到指定时间点快照**；参考 ruflo 官方处置——重新部署不能撤销已写入的 pattern，还须轮换凭据」 |
| 3 | 用户 A 的记忆会不会影响用户 B？ | 「我们按用户名做 key」 | 「按**授权 token 的 `sub`** 做 key 做租户隔离，绝不采用模型给出的名字；全局 scope 写入须升级审批」 |
| 4 | 记忆能存 API key 吗？存了怎么防泄漏？ | 「我们开了加密存储」 | 「**记忆不存任何 secret**（含用户粘贴的 key），写路径扫描 secret 字面量直接拒；加密无法阻止语义污染」 |
| 5 | 摘要 / RAG / 向量库这条链怎么管？ | 「摘要是可信的，因为是我们模型生成的」 | 「接触→提取→写入→**检索**→规划→动作六阶段每段都定级；检索阶段低信任记录不得直接进高权限上下文（对位 NIST 六阶段）」 |

## 4. 竞品差异化（为什么不是「买个网关就够了」）

| 能力 | 通用 MCP 网关 / 商业平台 | 本项目十二段守卫 |
| --- | --- | --- |
| 工具调用鉴权 | ✅ 标准能力 | ✅（V1.65~V1.70） |
| 注入扫描 | 多为**出站过滤器** | ✅ 入站 + 决策层 + 结果面 + 出口 + **写入期**（本段） |
| 来源溯源 | 通常只记调用者 | ✅ host 侧写入 provenance、模型自报不采信 |
| 隔离区 / 人工晋升 | 常见是「审批弹窗」 | ✅ pending 区读工具不可见 + 人工/可信源晋升（对位 V1.72「审批弹窗不是边界」） |
| 跨会话回滚 | ❌ 普遍缺失 | ✅ hash + append-only changelog + 时间点快照 |
| Java / Spring 私有化 | 多为 Go/TS SaaS | ✅ Java 零依赖参考实现、可进私有化交付、可现场跑断言 |

> 核心差异：**网关管「谁能调」，本守卫管「调完之后写进去的东西明天还算不算数」**。E1 已证明这是生产级 RCE 的持久化放大器。

## 5. 三档报价（公开挂牌价锚定，非成交价）

| 档 | 范围 | 公开价格锚 | 交付物 |
| --- | --- | --- | --- |
| L1 评估 | 单记忆服务写门 + provenance + 隔离区 PoC | [SpecsWriter](https://specswriter.com/knowledge/what_does_an_enterprise_mcp_server_security_audit_involve_and_how_should_organizations_approach_it_in_2026.php)：MCP 安全审计外部咨询**单服务器环境起 $15K**；[Vibidsoft](https://www.vibidsoft.com/mcp-server-development) 固定价评估 **$1,500 起**（不给小时价） | 12 项验收逐条打分 + 30 断言自检输出 + 投毒回滚演练 |
| L2 集成 | 多系统 + per-user 隔离 + write 审批 + 出口审计 | [dev.to 9 家横评](https://dev.to/tomakadlec/what-a-custom-mcp-server-costs-and-when-to-buy-a-connector-instead-4an2)：Clyrix **$35K–90K**（多系统）、Synchronized **$15K–120K+**（生产级）；Launch Day **$300K–700K**（带写动作） | 记忆面 + 日志出口面串接、升级审批工作流、季度复审 |
| L3 平台 | 全十二段治理 + 私有化 + 合规工件 + 托管运维 | Launch Day **$1M+** 顶层；商业审计平台年费数千~数万美元；维护 **$1,500/月起** | 全段守卫接入 21 主模块、SBOM/CVE 披露、等保/审计工件、24/48/72h 响应 |

> 报价为公开挂牌价 / 预算带，**非成交价**；国内项目须按本地浏览器/信创/私有化口径重估。

## 6. 24 / 48 / 72 小时响应包

| 时限 | 动作 | 交付 |
| --- | --- | --- |
| 24h | 确认是否暴露 `POST /mcp` 类无鉴权写端点、记忆服务是否默认给模型写权限；拉出 `agentdb_pattern-store` / `MEMORY_FILE_PATH` / 向量库写入清单 | 一页暴露面清单 + 是否命中 E1 判定 |
| 48h | 挂 CP1~CP4 写门（provenance + 隔离区 + scope/TTL + 载荷扫描）；对历史记忆跑一遍 hash 比对，列出可疑条目 | 写门部署 + 可疑条目清单（不自动删除，先送审） |
| 72h | 按 hash 回滚 / 清除投毒条目、轮换可能外泄的 provider key；接 CP5 检索重定级 + CP6 审计；出复验报告 | 清除 + 轮换 + 12 项复验报告 + 30 断言输出 |

## 7. 诚实边界

- 本框架**不受** ruflo `CVE-2026-59726` 直接影响（第三方产品）；NIST 草案 11-09 发布非近 3 天事件；E3/E4 为第三方实验/事件非本仓库实测。
- 参考守卫**未接入 21 主模块**；marker 词法匹配非语义解析、hash 非密码学签名、隔离区/审批为布尔标志；paraphrase 指令会绕过纯扫描 —— **provenance + 隔离区才是依赖的控制**。
- 加密存储不等于语义可信；**「打了补丁」不等于「清干净了」**——这是 E1 官方处置与 V1.70/V1.79 的共同结论。
- Spring AI 2.0 未升级；全部报价为公开挂牌价非成交价。
