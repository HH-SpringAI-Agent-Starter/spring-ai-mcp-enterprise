# STDIO 启动命令 allowlist（V1.68 参考实现）

> **状态**：REFERENCE ONLY，纯 JDK 零依赖，不进 Maven reactor、零构建风险。
> **兑现**：V1.67 待办 ④ ——「把 STDIO `command` allowlist 做成可运行示例，对位 Anthropic Won't-Be-Patched 的唯一缓解」。

---

## 为什么今晚必须做这个

| 时间 | 事实 | 对启动层的含义 |
| --- | --- | --- |
| 11-09 | Anthropic 对 STDIO 架构缺陷明确 **"Won't Be Patched"**（12+ CVE、官方四语言 SDK 约 1.5 亿下载） | 报告给出的**唯一缓解**就是 command allowlist，且**不是默认开启** → 责任下沉集成方 |
| 11-10 | `CVE-2026-123811` Dynatrace MCP Server **CVSS 7.5** | `--http` 起的 server 无鉴权/无会话校验/无 Host+Origin 防护，浏览器经 DNS rebinding 直接调 `execute_dql` 读走日志与安全事件；官方缓解第一条 = **停用 `--http`，改回 stdio 或只绑 127.0.0.1** |
| 11-10 | `CVE-2026-123812` **CVSS 3.7** | `create_dynatrace_notebook` 漏了 `requestHumanApproval`；而同 server 的 `send_event` 因有审批门**挡住了同一条攻击路径** → 审批门是真实生效的边界 |
| 11-08 | `CVE-2026-41903` 官方 `mcp-server-git` | 命令合法也不够：仓库自带 `core.fsmonitor` / `core.hooksPath` 会在标注 read-only 的 `git_status` 里执行代码；官方修复参数正是 `-c core.hooksPath=/dev/null -c core.fsmonitor= -c core.pager=cat` |
| 11-06 | `CVE-2026-102610` fast-agent | 默认 `0.0.0.0:8000` + `auth=None` + quickstart `--shell` 交出 root shell |

**结论**：把入口收回 stdio 之后，**最后一个可控点就是「允许拉起哪个进程、带什么参数、带什么环境变量」**。本示例把这件事做成可编译、可自检、可进标书演示的最小实现。

---

## 六条控制点

1. **绝对路径白名单（默认空 = 全拒绝 / fail-closed）**：路径先规范化（去包裹引号、全角转半角、统一分隔符、小写），拒绝相对路径、`..` 穿越、内嵌空格。
2. **shell 解释器黑名单**：`sh/bash/zsh/cmd/powershell/pwsh/wscript/cscript` 即便被误加进白名单也拒绝；`sh -c` / `node -e` / `python -c` 这类内联代码载体一律拒绝。
3. **参数逐条扫描**：shell 元字符 `; & | $ \` > < ( ) { } \n \r`、NUL 字节、`--http` / `0.0.0.0` / `--shell` / `--no-sandbox` 等危险开关、`--host=` / `--bind=` 网络绑定参数。
4. **`-c key=value` 只认显式安全值**：仅放行官方七 CVE 给出的三个 git 安全配置，其余（含 `core.fsmonitor=/tmp/evil.sh`）拒绝。
5. **环境变量净化**：拒绝 `LD_PRELOAD` / `DYLD_INSERT_LIBRARIES` / `BASH_ENV` / `NODE_OPTIONS` / `GIT_CONFIG_*` 等可注入变量，值里含元字符也拒。
6. **argv 数组启动**：`toProcessBuilder()` 在判定通过后才构造 `ProcessBuilder(List)`，**绝不拼命令字符串、绝不经过 shell**；每条拒绝带稳定 reason 码，可直接落 `mcp_security_*` 埋点。

## 运行

```bash
javac -encoding UTF-8 -d /tmp/mcp-selftest McpStdioCommandAllowlist.java
java -Dfile.encoding=UTF-8 -cp /tmp/mcp-selftest com.example.mcp.stdio.McpStdioCommandAllowlist
```

本期实测输出（2026-11-10）：

```
self-test OK (14 attack cases + 7 control cases + 3 boundary assertions)
incremental blocks beyond legacy baseline: 18 checks passed
```

**对照组断言**（延续本项目工程约定：**只看「拦住了多少」会选错方案**）：
- 基线 = 历史最常见的「只检查可执行文件名 allowlist」；
- 7 个合法用例（`git status`、git 官方安全 `-c` 三连、`log --oneline`、`python/node` 脚本路径、带引号路径等）要求**新旧结论一致**，新方案不得误杀；
- 14 个攻击用例中新方案必须全部拒绝，其中 **11 个是旧基线会放行的增量拦截**；
- 3 个边界断言：空白名单 fail-closed、`toProcessBuilder` 拒绝 shell、argv 数组首元素确为可执行文件。

> 本期开发时断言确实抓到一处不一致：`--host=0.0.0.0` 先被精确 flag 表命中返回 `FORBIDDEN_FLAG` 而预期 `NETWORK_BIND_FLAG` → 把网络绑定检查前移、去掉冗余精确项后修正。**断言不是装饰**。

## 对位本框架模块

| 控制点 | 框架模块 | 说明 |
| --- | --- | --- |
| 命令/参数/环境 allowlist | `mcp-governance`、`mcp-registry` | 注册期（server install / tool register）准入，工具默认 disabled |
| 稳定拒绝码 → 埋点 | `mcp-monitor`、`mcp-audit` | 复用 `examples/governance-hardening/McpSecurityMetrics` 的 `mcp_security_*` 指标 |
| 审批门 | `mcp-governance` HITL | 对位 `CVE-2026-123812`；写操作强制 `requestHumanApproval` + `destructiveHint` 注解校验 |
| 网络绑定 / DNS rebinding | `examples/connect-path-guard/` | `McpResolvedIpGuard` 负责解析后真实 IP 校验与 IP 固定 |
| 逐工具参数文法 | `examples/tool-input-grammar/` | 对位 `CVE-2026-41901` 的 `VACUUM INTO` |

## 诚实边界（别把它当沙箱）

- 这是**启动期字符串层**实现，**不能替代进程隔离**。生产还必须：固定可执行文件哈希/签名（防同路径替换）、容器或 seccomp 限制子进程能力、限制工作目录与可写路径、仓库/配置目录只读挂载。
- 不做符号链接 TOCTOU 防护、不做 DNS rebinding 防护（由 `connect-path-guard` 负责）、不校验可执行文件真实性。
- `core.autocrlf=true` 下新增文件须以 **LF** 落盘，否则 API create-tree 的 blob 会与 index 不一致。
- 白名单必须按部署环境显式配置；**构造空集合即全拒绝**，这是设计而非缺陷。

## 来源

- [CVE-2026-123811 · Dynatrace MCP unauthenticated HTTP tool calls](https://aithreatalert.com/cve/CVE-2026-123811)
- [CVE-2026-123812 · missing approval gate](https://aithreatalert.com/cve/CVE-2026-123812)
- [Seven CVEs Across MCP's Own Servers and Desktop Commander (11-08)](https://al-ice.ai/posts/2026/11/mcp-reference-servers-desktop-commander-seven-cve-batch-november-2026)
- [腾讯云开发者社区：MCP 认证与安全 — 授权是可选的，责任不是](https://cloud.tencent.com/developer/article/2779058)
