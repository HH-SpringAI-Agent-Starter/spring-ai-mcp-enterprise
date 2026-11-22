# 执行环境隔离面：12 项验收清单（V1.77 · 2026-11-22）

> **本期最有挣钱价值。** 九段式收口第九段：注解 → 注册中心 → 只读声明 → 决策层 → 配置面 → 结果面 → 身份传播面 → 会话/传输绑定面 → **执行环境隔离面**。
> 前八段管「谁能动 / 怎么判 / 交出什么 / 收到谁的身份 / 从哪个会话进来」，第九段管「工具真正落到执行环境那一刻，沙箱 confinement 到底闭没闭合」。

## 判词

**沙箱 confinement 只守住作者画的那条路；声明层（readOnlyHint / sessionId / 登录态）一律不参与授权。**
写路径必须和读路径同等 confinement，命令必须过 allowlist，env 必须源头与键名双白名单，
写动词必须服务端独立定级 + 审批，会话必须按 issuer + subject 绑定，默认只读 fail-closed。

## 一、本期对位证据（11-18~11-22 实时抓取）

| CVE / 事件 | CVSS | 事实 | 对位闸 |
| --- | --- | --- | --- |
| MCPHub `CVE-2026-79748` | **9.9** | 非管理员注册 `command=/bin/sh` 的 server 立即执行、edit 端点无归属校验；Docker 常以 root 运行 | CP2 / CP4 |
| Hermes Agent `GHSA-p22p-9ph6-gmrh` | High | 从可写目录读回 env 文件注入 `LD_PRELOAD` / `DYLD_INSERT_LIBRARIES`，穿透沙箱以 gateway 用户执行、触达 provider keys | CP3 |
| Gortex `CVE-2026-62964` | 7.8 | read 工具查 confinement + symlink，write 工具收绝对路径跳过两查 → 可写 `~/.zshrc` / git hook | CP1 / CP4 |
| yt-mcp `CVE-2026-75612` / DOMShell `75518` | 7.5 / 4.8 | `add_attachment` 读任意文件外发；`domshell_eval` 标只读却跑 CDP `Runtime.evaluate`，客户端审批 UI 被骗 | CP1 / CP4 |
| MCP Python SDK `CVE-2026-52869` | 7.1 | 11-20 VulnCheck 在野：仅凭 session_id 路由不校验同一认证客户端，共享 OAuth client 无 subject → 跨用户会话注入 | CP5 |
| Jupyter MCP（9.3）/ Todoist `CVE-2026-76807` | 9.3 / High | HTTP 端点无保护经 DNS rebinding 指向攻击者基建；未校验 Host/Origin + 通配 CORS 驱动 agent SaaS 账户 | CP5 / CP6 |

## 二、12 项执行环境面验收清单（逐条 → 控制点 → 仓库真实文件 → 现场验收）

> 完整可运行样品见 [`examples/execution-environment-guard/`](../examples/execution-environment-guard/README.md)，纯 JDK 17 零依赖、`javac` + `main` 自检全绿。

| # | 验收项 | 控制点 | 仓库真实文件 | 现场验收方式 |
| --- | --- | --- | --- | --- |
| 1 | 绝对路径逃逸 confinement root 拒绝（读写同查） | CP1 | `checkPath()` | `/etc/passwd` → ESCAPE_ABSOLUTE_PATH |
| 2 | `..` 路径遍历拒绝 | CP1 | `normalize()` | `../secrets.txt` → ESCAPE_PATH_TRAVERSAL |
| 3 | 符号链接逃逸拒绝 | CP1 | `checkPath()` symlinks | `link/config` + symlink 集合 → ESCAPE_SYMLINK |
| 4 | 路径控制字符拒绝 | CP1 | `checkPath()` | `report.csv\u0000.png` → ESCAPE_CONTROL_CHAR |
| 5 | 命令 allowlist + 解释器直执拒绝 | CP2 | `checkCommand()` | `/bin/sh` / `bash` → CMD_INTERPRETER；`curl` → CMD_NOT_ALLOWLISTED |
| 6 | argv shell 元字符拒绝 | CP2 | `checkCommand()` | argv `x; rm -rf /` → CMD_SHELL_META |
| 7 | env 危险键拒绝（注入防护） | CP3 | `checkEnv()` | `LD_PRELOAD` / `DYLD_INSERT_LIBRARIES` → ENV_DANGEROUS_KEY |
| 8 | env 源头白名单 + 值中命令替换拒绝 | CP3 | `checkEnv()` | 源 `/tmp/agent.env` → ENV_UNTRUSTED_SOURCE；`$(curl evil)` → ENV_INJECTION_VALUE |
| 9 | 服务端独立定级：只读声明不参与授权 | CP4 | `checkTier()` | 写动词标 `readOnlyHint:true` → READ_ONLY_LIE |
| 10 | 写动词必须有审批令牌 | CP4 | `checkTier()` | 写动词无令牌 → WRITE_TIER_NEEDS_APPROVAL |
| 11 | 会话按 issuer + subject 绑定（共享 client 隔离） | CP5 | `checkSubject()` | 无 subject → SUBJECT_UNBOUND；共享 client 用 clientId 当 subject → SHARED_CLIENT_NO_SUBJECT；issuer 错投 → ISSUER_MISMATCH；主体不一致 → SUBJECT_MISMATCH |
| 12 | 不得劣于旧基线 + 脱敏审计 | CP6 | `appendAudit()` | 旧基线放行的 20 条全拦；`s3cr3t-value` 与原始 subject 不落审计；**26 断言 / 0 误杀** |

> 现场跑：`java McpExecutionEnvironmentGuard` → `self-test: OK / attack 20/20 / control 4/4 / boundary 2/2 / legacy-only 20`。
> ⚠️ 参考实现**未接入主模块**；策略硬编码、符号链接集合模拟、单进程审计表为演示约束；**本框架不受上述 CVE 直接影响**。

## 三、买方必问 5 问（含不合格 / 合格回答对照）

| # | 买方问 | ❌ 不合格回答 | ✅ 合格回答 |
| --- | --- | --- | --- |
| 1 | 你们的 read-only 工具，写路径有没有单独校验？ | 「我们标了 readOnlyHint，客户端会拦」 | 「声明层不参与授权；写动词服务端独立定级，write/edit/attach 走与 read 同一 confinement，缺审批令牌直接 fail-closed」 |
| 2 | 工具命令能不能被执行成 shell？ | 「我们用 command 字符串启动，方便配置」 | 「命令走 allowlist，argv 数组启动、禁 shell 元字符，解释器直执与非管理员注册一律拒绝」 |
| 3 | 沙箱里的 env 能不能影响宿主机进程？ | 「我们读回 env 文件合并，方便传配置」 | 「env 源头与键名双白名单，LD_PRELOAD/DYLD_* 与命令替换值一律拒绝，不接收沙箱内可写目录回读的 env」 |
| 4 | 多个用户共用一个 OAuth client 时怎么隔离会话？ | 「按 session_id 路由就行」 | 「session 按 issuer + subject 绑定；共享 client 必须有 per-user subject，缺 subject 或主体不一致拒绝」 |
| 5 | 出事了能不能审计到是谁、改了什么？ | 「我们有日志」 | 「append-only 脱敏审计：记录规范化 op/path/subject，env 原始值与凭据不落盘，留存 ≥180 天、可导出」 |

## 四、竞品差异化（诚实边界）

| 维度 | 通用 MCP server / 网关 | 本框架（Java + Spring） |
| --- | --- | --- |
| 只读声明 | readOnlyHint 交给客户端渲染审批 UI（DOMShell 证明会被骗） | 服务端独立定级，声明层不参与授权 |
| 命令执行 | command 字符串直接跑（MCPHub 非管理员可塞 /bin/sh） | allowlist + argv 数组 + 解释器拒绝 + 管理员门槛 |
| 沙箱 env | 读回 env 文件合并（Hermes 穿透） | 源头 + 键名双白名单 |
| 会话隔离 | session_id 仅 correlation（Python SDK 在野洞） | issuer + subject 双绑定、共享 client 强制 per-user |
| 审计 | 有日志但常含原始凭据 | append-only + 脱敏 + 可导出 |
| 诚实边界 | — | 守卫仍在 examples/ 层未接入 21 主模块，策略外置与 KMS 轮换为生产前置 |

## 五、三档报价（公开估算，非成交价）

| 档 | 交付 | 价格锚 |
| --- | --- | --- |
| 诊断档 | 执行环境面 12 项验收 + 现状差距报告 + 30 天复验 | ¥3.7 万起（对标 Dawloom 最低档 $3,700） |
| 加固档 | 六道闸接入网关 + 策略外置 + 审计导出 + 现场跑通 26 断言 | $80K–220K（生产级治理层锚） |
| 托管档 | 企业网关 + 多租户 + 规格版本变更全量复验年度托管 | $250K 起 |

> 报价策略：按交付物 / 集成 / 修订轮次 / 工时拆（OpenAI×Upwork 固定价预算 +52.4%、时薪仅 +1.5%）；10 倍定律对位 21 模块治理层。

## 六、24 / 48 / 72h 行动清单

- **24h**：把本 12 项清单 + 26 断言输出压成 1 页 PDF 进售前包；发 SEO 稿抢 CVE 流量。
- **48h**：独立分支评估把六道闸接进 `mcp-gateway`（先跑通 26 断言）；回广发平台核实 13327 报名截止。
- **72h**：备标中原银行 AI 网关、工信部五所 12-03（广州、中小企业、不接受联合体）；核对山东重工 11-20 是否已过。

## 七、诚实边界（延续口径）

- 本框架不受上述第三方 CVE 直接影响；引用为设计依据，非「已修复自身漏洞」声明。
- 广发四份为预征集 ≠ 采购邀请；本项目 7 项资质全不满足，只能联合体技术分包；无企业主体 / 合同 / 中标 / 金融案例 / 等保测评。
- 全部报价为公开挂牌价 / 估算非成交价；Spring AI 2.0 未升级；守卫未接入主模块。
