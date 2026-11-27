# 审批确认 / 委托链面也不是安全边界（V1.81，2026-11-27）

> 十三段式收口第十三段：注解 → 注册中心 → 只读声明 → 决策层 → 配置面 → 结果面 → 身份传播面 → 会话/传输绑定面 → 执行环境隔离面 → 浏览器出站响应面 → 日志/遥测输出面 → 持久化记忆面 → **审批确认 / 委托链面**。
>
> 判词：**确认令牌是流程句柄，不是独立审批人；scope 声明与 readOnlyHint 是审计事实，不是允许分支的输入；只读入口工具内部委托的动作必须逐段重新判定。**

---

## 0. 本期证据链（近 3 天，均为第三方，本框架不受直接影响）

| # | 证据 | 事实 | 对本期论断的意义 |
| --- | --- | --- | --- |
| E1 | **CVE-2026-131688**（11-26 公开，CNA 高危 CVSS 3.1 **8.1**） | WordPress `miniorange-secure-mcp-server` ≤1.5.8：持**有效 MCP bearer token**（含 scope 受限的连接测试令牌）可越过工具白名单与 scope 限制，取得令牌所属管理员完整 Administrator 角色。CNA 定级 PR:N **不代表无需令牌**。暂无在野利用、未在 KEV。 | 「受限 scope」是声明，不是权限边界；令牌绑定的用户身份未被约束在合法 MCP 请求范围内 |
| E2 | **CVE-2026-136620**（官方公告 11-07 / CVE 条目 11-25 入库，CVSS 3.1 **7.1**、CVSS 4.0 6.9） | `pain001-mcp` 0.0.72–0.0.79：`stage_payment_batch` 把 `confirmation_token` **直接返回给同一调用方**，该令牌无法代表独立人员复核；`output_file_path` 无边界校验，可写到服务账号可写位置、可新建目录、可覆盖文件。修 0.0.80：真实路径 confinement + 符号链接校验 + 仅 `.xml` + 禁覆盖 + 相对路径。写入内容仅为生成的 XML，**不是任意代码，也不是银行结算服务**。 | 自批自批 ≠ 双人复核；确认令牌与输出路径都只是句柄/声明 |
| E3 | **官方 MCP servers v2.5.0**（11-18，一次修 8 漏洞涉及 3 个 server） | `mcp-server-git` 6 漏洞：`git_merge` 触发 git 自动维护执行仓库 `.git/config` 的 `gc.recentObjectsHook`（官方 PoC 以 **uid=0 root** 运行）；**只读** `git_diff` 遇构造 `.git/index`（`../outside/secret`）可读工作目录外文件；子模块配置、空名 filter 可执行；promisor remote 令只读工具自行远程拉对象绕本地限制。`server-filesystem` 符号链接 TOCTOU（校验目录后、列目录前替换；Linux/macOS 已修，**Windows 未修 #5600**）。`server-everything` gzip-file-as-resource 在 NAT64 下把私网当公网（2.3.1 修）。升级坑：filesystem 改语义版 2.4.0，**npm 认为低于所有 2025.x/2026.x，不提示升级，须手动 `npm i ...@2.4.0`**。 | 只读入口的内部委托链（hook / submodule / filter / promisor / symlink）必须逐段重判；「升级提示」也不是安全边界 |
| E4 | **Duco MCP server GA**（11-25，伦敦；金融 post-trade 对账平台） | Agent 可用 Duco 工具搜索/分类/加注/匹配异常，但**每个动作跑在用户自己的凭据与既有权限下、带完整审计、IT 无需新增访问模型**；确定性引擎仍负责匹配与规则。厂商称 Pacesetter 客户平均节省 76% 对账时间，平台 30+ 国家 1 万+ 用户。 | 正面标杆：决策权留在确定性引擎与用户凭据，Agent 只拿到工具而非新权限 |
| E5 | **Ivalua × Altares D&B MCP Server**（11-02） | 供应商准入流程中 IVA 检索 D&B 数据并**提出计划、由用户逐动作批准后才创建供应商**；厂商对比称 MCP 驱动的供应商流程处理时间减少 70–96%、复核产能最多提升 20 倍。 | 「提出计划 + 逐动作批准 + 不出合同数据范围」是可售的审批链设计 |

---

## 1. 十二项审批确认面现场验收清单

> 逐条给出：**验收项 → 控制点 → 真实文件 → 现场验收方式**。本清单是买方 POC 的提问脚本，不是营销话术。

| # | 验收项 | 控制点 | 真实文件 | 现场验收 |
| --- | --- | --- | --- | --- |
| 1 | 审批人是否与请求人隔离 | CP1 | `McpApprovalChainGuard.java` CP1 | 用请求人自己的令牌走状态变更 → 必须 DENY@CP1（自检 A1/A2/A20） |
| 2 | 确认令牌是否由调用方自签发/自返回 | CP1 | 同上 `tokenIssuer` 判定 | `tokenIssuer=SELF` → 必须 DENY（A1/A20） |
| 3 | 审批人是否在独立 allowlist | CP1 | 同上 `APPROVERS` | 任意用户名签发的令牌 → DENY（A3） |
| 4 | 状态变更是否强制独立审批 | CP1 | 同上 `needsApproval` | 空令牌的 PAYMENT → DENY（A4） |
| 5 | 服务端是否独立定级 required scope | CP2 | 同上 `READ_TOOLS` | 客户端声明 `admin`、服务端记录 `read` 不匹配 → DENY（A5/A6） |
| 6 | claimed scope / readOnlyHint 是否只进审计不进允许分支 | CP2/CP6 | 同上 `canon()` 与 `appendAudit()` | 声明 `read` 但服务端定级 elevated → 仍 DENY（A6）；合法声明错误不被误杀（C5） |
| 7 | 令牌是否 TTL + 一次性 + payload 绑定 | CP5 | 同上 `TOKEN_TTL_MS` / `CONSUMED` / `sha256` | 过期、未来时间、篡改 payload、重放 → 全部 DENY（A7–A10） |
| 8 | 只读入口的内部委托动作是否逐段重判 | CP3 | 同上 `DELEGATED_WRITE` | `git_diff` 委托 `gc.recentObjectsHook`/`promisorfetch`/`symlinkresolve` → DENY（A11–A13）；无害 `blame_read` 放行（C2） |
| 9 | 输出路径是否真实路径 confinement | CP4 | 同上 `toRealPath()` + `startsWith(root)` | 绝对路径、`../outside/secret.xml` → DENY（A15/A16） |
| 10 | 后缀白名单 + 禁覆盖 + 符号链接拒绝 | CP4 | 同上 `endsWith(".xml")` / `Files.exists()` / `isSymbolicLink()` | `.sh`、已存在文件、软链目录 → DENY（A17–A19） |
| 11 | 审计是否 append-only 且脱敏 | CP6 | 同上 `AUDIT` / `appendAudit()` | 令牌原文不落盘、仅记 `sha256:` 前 12 位、条目只增不减（B1） |
| 12 | 是否保留合法调用对照组（0 误杀） | 全链 | `ApprovalChainGuardSelfTest.java` C1–C8 | 8 条合法调用全部 ALLOW；payload 含 `DROP TABLE` 字样但只读的查询不被误杀（C8） |

**自检实测**：`javac -encoding UTF-8 -d .` + `java` → **20 attack + 8 control + 3 boundary = 31 断言全绿，0 合法调用误杀，旧基线（`confirmation_token` 非空 + `readOnlyHint`）漏 15/20 攻击**。
⚠️ 开发期对照组抓到并修正 2 处真问题：① 常量池以原始拼写存储而比对走 `canon()` 归一化 → `READ_TOOLS` 合法工具被误判为 elevated，`APPROVERS` 合法审批人被拒；② 重放用例的 legacy 计数未纳入自动累加 → 边界断言口径错误。**「只写顺风测试会选错方案」再次应验。**

---

## 2. 买方必问 5 问（不合格 / 合格回答对照）

| # | 问题 | ❌ 不合格回答 | ✅ 合格回答 |
| --- | --- | --- | --- |
| 1 | Agent 拿到 confirmation_token 后，谁能批准？ | 「工具自动审批就行，token 有效即可执行」 | 「审批人由控制面独立签发、与请求主体不同、在 allowlist 内；token 绑定精确 payload、5 分钟有效、一次性；请求人自批一律拒绝」 |
| 2 | 只读工具内部会不会执行命令或读工作目录外文件？ | 「标了 readOnlyHint 就不会写」 | 「声明只进审计；内部委托动作逐段重判，hook/submodule/filter/promisor/symlink 一律按独立动作处理；官方 v2.5.0 的 git/filesystem 洞正是此类」 |
| 3 | 升级到修复版是不是就安全了？ | 「升到最新版就没问题」 | 「升级只移除已知绕过：filesystem 改语义版后 npm 不提示升级须手动核对；Windows 软链洞仍未修；须部署后逐路径复验并保留旧配置复查」 |
| 4 | 输出文件写到哪里、能不能覆盖？ | 「配置一个目录就行」 | 「真实路径解析后必须在 confinement 根内；仅允许白名单后缀、已存在文件拒覆盖、路径中任一软链拒绝；信管/审计可在现场逐条触发验证」 |
| 5 | 出事以后能不能追溯？ | 「有日志，开了脱敏」 | 「append-only 审计记录工具、请求主体、claimed/effective scope、hint、令牌摘要（原文不入盘）；脱敏是配置声明，出口仍按内容独立定级复核」 |

---

## 3. 竞品差异化

| 维度 | 通用 Agent 平台 / 网关 | 官方 MCP Server 现状 | 本框架 V1.81 |
| --- | --- | --- | --- |
| 审批主体 | 平台内用户点击，令牌常回传调用方 | confirmation_token 由同一接口返回（pain001 旧版） | 控制面独立签发 + 请求/审批主体隔离 + allowlist |
| scope | OAuth scope 声明即授权（131688 提权） | 声明式权限 | 服务端独立定级，声明只进审计不进允许分支 |
| 只读语义 | readOnlyHint 供 UI 自动批准 | git_diff 只读却读目录外文件 | 内部委托动作逐段判定，入口声明不继承 |
| 文件输出 | 配置目录 | 软链 TOCTOU、Windows 未修 | 真实路径 + 后缀 + 禁覆盖 + 软链全链拒绝 |
| 版本治理 | 「升级即可」 | npm 语义版降级不提示升级 | 升级后逐路径复验 + 旧配置复查清单 |
| 可审计性 | 聊天记录式日志 | 因 server 而异 | append-only、令牌摘要不入原文、31 断言可现场复现 |

**边界**：本框架**不声称**替代上述任何第三方产品的安全责任；Guard 仍在 `examples/` 层，**未接入 21 个主 reactor 模块**；审批人与令牌是字符串 allowlist 与进程内 hash 绑定，**非密码学签名 / 非独立身份系统**；confinement 为路径判定，不是运行时竞态防护；不是生产组件。

---

## 4. 三档报价（公开挂牌价口径，非成交价）

| 档位 | 交付物 | 周期 | 参考价位 |
| --- | --- | --- | --- |
| **L1 审批链审计包** | 12 项清单现场验收 + 31 断言复现 + 旧基线漏检对照 + 一页加固建议 | 2 周 | **$15K–30K** |
| **L2 集成加固** | CP1/CP2/CP5 接进 `mcp-gateway` 审批链，含独立审批人接口、payload 绑定、一次性令牌 | 4–8 周 | **$80K–220K** |
| **L3 生产级私有化** | CP1–CP6 全链接入 + 与既有 IdP/审批流对接 + 逐路径复验 + 运维审计包 | 3–6 个月 | **$300K–700K**，运维 **$5K–25K/月** |

> 对标公开挂牌：Launch Day 2026 L1 $100K–300K / L2 $300K–700K / L3 $1M+；可信审计独立成项 $15K 起；生产级 $80K–220K、网关 $250K 起。以上均为挂牌价，非成交价，不构成报价承诺。

---

## 5. 24 / 48 / 72 小时交付节奏

| 时点 | 交付 |
| --- | --- |
| **24h** | 12 项清单 + 31 断言输出 + 旧基线 15/20 漏检对照压成 1 页 PDF；对位 JD 逐条写应答 |
| **48h** | 在客户给定 PoC 环境复现 A1–A20（确认令牌自批、scope 提权、委托链、路径逃逸），出逐条 PASS/FAIL 表 |
| **72h** | 出 L2 集成方案与工作量拆分，标明「字符串标志 / 进程内去重」等演示约束与生产替换清单 |

---

## 6. 诚实标注

- 本框架**不受** CVE-2026-131688 / 136620 / 官方 servers v2.5.0 相关漏洞直接影响（均为第三方产品）；官方 MCP Java SDK 仍为核心依赖，须单独披露版本。
- CVE-2026-131688：CNA 高危 8.1，**必须持有有效 bearer token**，PR:N 不代表无需认证；暂无在野利用、未入 KEV；修复版本信息尚不完整。
- CVE-2026-136620：写入内容仅为生成的 XML，非任意代码、非银行结算；0.0.80 修复；官方公告 11-07、CVE 入库 11-25。
- 官方 v2.5.0：filesystem 的 Windows 软链洞 **#5600 未修**；npm 语义版降级不提示升级；git server 须升 2026.11.18、everything 2.3.1、filesystem 2.4.0，均须手动核对。
- Duco 76%、Ivalua 70–96%/20x 均为**厂商自述**，非独立第三方评测。
- 上海银行会话式 AI 技改中标 224.97 万（中科金财，11-26 公告）**未点名 MCP，不计入可投清单**。
- 全部报价为公开挂牌价、非成交价；Spring AI 2.0 未升级。
