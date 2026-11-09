# MCP 连接路径防护（Reference Only）

> V1.66 新增 · 2026-11-08 ｜ **V1.67 扩充 · 2026-11-09（兑现 DNS rebinding TODO）** · **不进 Maven reactor、不参与 CI 构建、零外部依赖**
> 纯 JDK，`javac -encoding UTF-8 -d /tmp/out McpConnectPathGuard.java McpResolvedIpGuard.java`，再分别跑两个 `main()` 自检。
> 🆕 V1.67 新增 `McpResolvedIpGuard.java`：解析后真实 IP 校验 + IP 固定（pin），把本目录原「还可补的」第一条关闭。

---

## 为什么单独做这一层

2026-11-05 Pillar Security 公开了 `CVE-2026-119025`（MCP Inspector 官方调试工具，CVSS 4.0 **7.7**；对应 GitHub 公告 `GHSA-q978-78c7-phqw` CVSS 3.1 **8.8**）。它证明了一类**工具级 guardrail 完全看不见**的攻击：

| 步骤 | 发生了什么 |
| --- | --- |
| 1 | 你把 Inspector 指向一个还没信任的远程 MCP server —— 这正是这个工具的用途 |
| 2 | server 返回 401 + OAuth discovery，`authorization_endpoint` 是**由它自由选择的字符串** |
| 3 | Inspector 通过 npm `open` 包自动打开该 URL（交互式终端下 auto-open 默认开，CLI/TUI/Web 三处都有） |
| 4 | Windows/WSL 上 `open` 10.x 把 URL 放进 PowerShell **双引号字符串**，base64 后 `-EncodedCommand` 执行，未做转义 |
| 5 | PowerShell 在双引号里求值 `$(...)`：`https://evil.test/$([IO.File]::WriteAllText('C:/Users/Public/pwned.txt','RCE'))` |

**全过程零工具调用、零审批提示、零模型参与**，代码在工具列表出现之前就已执行。且 URL 是合法的 `https://` —— **只校验 scheme 的防御会直接放行**。真正修好问题的是 `open` 11 的参数转义 + 单引号，以及"URL 根本不到 shell"的 argv 形状。

同一周还有两个同源信号：

- **v1 Inspector 的兄弟洞** `GHSA-4fc4-wcmh-4g86`（**9.6**）：OAuth callback 的 query 参数可把远程 session 翻转成本地 stdio 命令执行，**不限操作系统**。
- **opencode 1.18.33「无 CVE 的修复」**：同类 PowerShell 注入在 2026-09-28 以 PR 标题 `refactor(core): share a browser opener...` 悄悄修掉，没发 CVE、没发公告 → Dependabot / GitHub Advisory DB / SBOM **全部静默**。Adversa AI 统计 10 起 Claude Code 相关攻击仅 2 起有 CVE。

> ⚠️ 诚实边界：研究者在 Windows/WSL 的可达性来自 win32 分支代码审阅（macOS/Linux 走 `spawn` argv 不受影响），**尚无在野利用报告**；Pillar 统计约 768,000 周 npm 下载、>80% `open` 包周下载仍解析到受影响版本。

---

## 四条控制点

| # | 方法 | 挡住什么 | 验收 |
| --- | --- | --- | --- |
| 1 | `checkAuthorizationEndpoint(url, allowlist)` | `$(...)` / 反引号 / 编码绕过 / userinfo / 私网与云元数据 / 非白名单主机；**先字符后 scheme** | 真实载荷必须 `SHELL_METACHAR`，正常 `https://auth.example.com/...?a=b&c=d` 必须通过 |
| 2 | `checkDiscoveryDocument(bytes)` | 无界发现文档（上限 64 KiB，对齐 4 MiB 请求体口径）+ 文档内 shell 载荷 | 超限必须拒 |
| 3 | `browserArgs(exe, url)` | 服务端给的字符串到达 shell / PowerShell | 返回 `List<String>` 交给 `ProcessBuilder`；固定可执行文件路径 + `--` 分隔，绝不拼 `-EncodedCommand` |
| 4 | `autoOpenAllowed(remote, optIn)` + `enforceVersionFloor(tool, current, floor)` | 默认自动打开 + 「无 CVE 修复」永远等不到告警 | 远程目标默认禁自动打开；Inspector 版本下限 2.12.0（导入 `server.json` 用 2.14.0） |

**自带对照组自检**（`main()`）：6 条攻击用例必须被拒 + 3 条正常用例必须放行 + argv 中不得出现 `powershell`。V1.66 已实测 `self-test OK (6 attack cases + 3 control cases)`，V1.67 回归重跑仍通过。

---

## 🆕 V1.67 第五条控制点：解析后真实 IP 校验 + IP 固定（`McpResolvedIpGuard`）

V1.66 只做**字面量**私网检查，README 里明确留了 TODO。攻击者注册 `attacker.example` 先解析到公网 IP 通过主机白名单，把 TTL 设为 0，真正建连时改解析到 `127.0.0.1` / `169.254.169.254`，即可把「检查时公网、连接时内网」的 **TOCTOU 时间差**变成 SSRF / 云元数据窃取。这正对应 `CVE-2026-11624`（DNS rebinding，NIST 要求 Origin 校验为主动修复义务）、`CVE-2026-123786`（Agenta workflow SSRF）与 11-09 检索到的腾讯云 / CSDN 治理实践「出站白名单须含 DNS 解析后真实 IP 阻断」。

| # | 方法 | 挡住什么 |
| --- | --- | --- |
| 1 | `resolveAndScreen(host, resolver)` | 解析**全部** A/AAAA 记录，任一命中黑名单即整次拒绝（不能只查第一个） |
| 2 | `isForbidden(InetAddress)` | loopback / RFC1918 / link-local 含 `169.254.169.254` / CGNAT 100.64/10 / `0.0.0.0/8` / 多播 / 保留段 / IPv6 ULA `fc00::/7` / `fe80::/10` / **IPv4-mapped IPv6（`::ffff:127.0.0.1`）** / 6to4 / Teredo |
| 3 | `pin(host, port, resolver)` | 把校验通过的 IP **固定**下来交给连接层，连接时不再做第二次 DNS 解析 —— 光「校验一遍」不够，这是防 rebinding 的关键 |
| 4 | `screenUrl(url, allowlist, resolver)` | 与 V1.66 串联：**先字符后 scheme，再解析后 IP** |
| 5 | 可注入 `Function<String, InetAddress[]>` 解析器 | 自检**不依赖外网**，可确定性复现「一次公网一次私网」的 rebinding 场景 |

**已实测自检**：

```
[McpResolvedIpGuard] self-test OK (16 attack cases + 5 control cases)
pin = api.example.com:443 -> 93.184.216.34
[McpConnectPathGuard] self-test OK (6 attack cases + 3 control cases)   ← V1.66 回归
```

覆盖：字面量私网/元数据/loopback/IPv6 ULA/IPv4-mapped 私网、DNS rebinding（多记录夹带 `127.0.0.1`）、多记录夹带 `10.0.0.7`、解析到元数据、空解析 fail-closed、`$(...)` 载荷在 DNS 之前被拒、白名单主机 rebinding 仍被拒、pin 拒绝 rebinding 主机；对照组含公网域名 / 公网 IPv4 / 全局 IPv6 / 合法白名单 URL 全部放行。

> ⚠️ **诚实边界（仍未做的）**：不替代服务端 Origin/Host 头校验；**HTTP 重定向后的每一跳都必须重新走本校验**（本示例不含重定向链）；连接层须保证 SNI/Host 与 pinned IP 一致；生产还应叠加强制出站代理与 `mcp-monitor` 拒绝埋点。

---

## 与服务端防护的分工

| 层 | 位置 | 负责 | 仓库文件 |
| --- | --- | --- | --- |
| **连接路径（本目录）** | MCP **客户端** / 调试工具 / IDE 插件 | discovery 可信边界、浏览器启动、客户端版本下限、工具供应链准入、**解析后真实 IP 校验 + IP 固定** | `McpConnectPathGuard.java` + `McpResolvedIpGuard.java` |
| 工具入参 | 工具实现内部 / 网关参数校验 | 逐工具语句文法、堆叠语句、注释绕过、只读注解不可信 | [`../tool-input-grammar/`](../tool-input-grammar/README.md) |
| 请求路径 | MCP **服务端** / 网关 | 4 MiB、fail-closed 鉴权、同源、会话主体、工具准入、HITL、审计 | [`../governance-hardening/`](../governance-hardening/README.md) |
| 检测响应 | 监控 / SIEM | 拒绝事件、出站、子进程、凭据访问独立信号 | [`../monitor-alert-rules/`](../monitor-alert-rules/README.md) |

**关键结论**：`mcp-governance` 的工具审批再严，也管不到"还没连上就已执行"的那一步。给采购方/安全评审讲方案时，这三层要分开画，不能混成一句"我们有护栏"。

---

## 还可补的（诚实清单）

- ✅ ~~解析后真实 IP 校验（防 DNS rebinding）~~ —— **V1.67 已由 `McpResolvedIpGuard` 关闭**（含 IP 固定）；
- HTTP 重定向链逐跳重校验 + 连接层 SNI/Host 与 pinned IP 一致性断言；
- 客户端工具**资产清单 + 自动更新通道**（把 agent CLI 当浏览器管，而不是当库等 CVE）；
- 从搜索结果仓库安装 MCP server 的准入策略（Check Point Yoba 假 ChatGPT/Claude 安装包 DLL sideloading 活动）；
- STDIO `command` allowlist 落地（Anthropic 对 STDIO 架构缺陷明确「Won't Be Patched」，唯一缓解是把启动命令收敛到 `npx` / `uvx` / `python` / `node` / `docker` / `deno`，且非 SDK 默认）。

---

## 参考

- `CVE-2026-119025` / `GHSA-q978-78c7-phqw` 分析：https://dev.to/piekwerk/mcp-inspectors-oauth-flow-could-run-powershell-on-windows-inside-cve-2026-119025-and-why-npm-1hei
- 一周五洞供应链复盘（11-06）：https://hangingcontext.com/stories/2026-11-06/ai-supply-chain-flaws-week
- opencode 无 CVE 修复：https://deniz.in/opencode-fixed-a-no-consent-mcp-oauth-rce-in-1-18-33-and-no-cve-was-ever-issued
- 完整情报：[`docs/earnings-report-2026-11-08.md`](../../docs/earnings-report-2026-11-08.md)
