# 本地 MCP HTTP 监听器准入（V1.69 参考实现）

> **状态**：REFERENCE ONLY，纯 JDK 17 零依赖，不进 Maven reactor、零构建风险。
> **兑现**：V1.66 起挂账的「连接路径 / 传输层 Host·Origin 校验」剩余项，对位 2026-11-10 Siemens 双 CVE。

---

## 一句话立场

**把 server 绑在 `127.0.0.1` 不等于安全边界** —— 开发者浏览器里的任意标签页都能打到 localhost。必须同时做四件事：只绑 loopback、非 loopback 显式 opt-in 且强制鉴权、`Content-Type` 严格 `application/json`、`Origin` / `Host` 允许列表 + 禁 wildcard CORS。

---

## 触发本示例的真实公告

| 时间 | 事实 | 本示例控制点 |
| --- | --- | --- |
| **2026-11-10** | [Siemens ProductCERT **SSA-710379** V1.0](https://severitydaily.com/siemens-mendix-studio-pro-mcp-server-cve-2026-112136-8-8-cvss-3-1-ui-r-vs-9-2-cvss-4-0-ui-n-at-p-cwe-1327-loopback-port-7782-ssa-710379-v11-16-0-v11-12-7-cve-2026-112137-csrf-origin-content-type)：Mendix Studio Pro 11 内置 MCP Server **`CVE-2026-112136`** CWE-1327「未把 HTTP listener 限制在 loopback」，CVSS 3.1 **8.8** / CVSS 4.0 **9.2**；端口 **7782**，官方缓解第一条 = 主机防火墙阻断外部 7782、远程访问走 VPN/SSH 隧道 | `checkStart()`：拒 `0.0.0.0` / `::` / 空绑定；非 loopback 必须 `remoteOptIn && authenticated` |
| **2026-11-10** | 同公告 **`CVE-2026-112137`** CWE-352：MCP server「未校验 `Content-Type` 与 `Origin`」，恶意页面可让开发者浏览器向本地 server 发未授权请求（5.4 / 5.1）；官方缓解 = Chromium/WebKit **Private Network Access** 或插件挡 localhost fetch | `checkRequest()`：Content-Type 严格 `application/json`、Origin 白名单、`null` / 缺失均拒 |
| **11-10 复盘** | [`CVE-2026-123811`](https://aithreatalert.com/cve/CVE-2026-123811) Dynatrace MCP Server `--http`：无鉴权 / 无会话校验 / 无 Host+Origin 防护，浏览器经 DNS rebinding 直接调 `execute_dql` 读走日志、安全事件、用户会话；修 2.0.0 | 未鉴权一律 401；Host / `X-Forwarded-Host` 一致性；rebinding 后缀域黑名单 |
| **11 月榜** | [Adversa《Top MCP security resources — November 2026》](https://adversa.ai/blog/top-mcp-security-resources-november-2026)：`@modelcontextprotocol/server-everything` 绑所有网卡 + **wildcard CORS** + 无鉴权 + `get-env` 返回整个 `process.env` → 任意网页跨域握手读走 API key（修 2.0.1）；**Basic Memory** 照官方建议绑 localhost 仍被 DNS rebinding 打穿，因为缺的是 Host/Origin 校验，FastMCP 的 `HostOriginGuardMiddleware` **默认关闭**（升 0.24.0） | 禁 `Access-Control-Allow-Origin: *`；禁 `get-env` 类整包环境变量工具；Host 白名单 |
| **11-09** | [Harriet MCP 网关指南](https://harriethq.com/blog/what-is-an-mcp-gateway) 引规范原文：tool annotations「**should be considered untrusted, unless obtained from a trusted server**」；网关四职责 = 决定访问 / 检查 connector / 持有凭据 / 记录调用 | 与 [`examples/tool-annotation-gate/`](../tool-annotation-gate/README.md) 组成「注册期 + 传输期」双层 |

> ⚠️ **口径**：Siemens  advisory 里 CVSS 3.1 记 `UI:R`、CVSS 4.0 记 `UI:N / AT:P`，是同一评分者用两代标准表达「是否可达」这一前提，**不是两份相互矛盾的公告**；CISA SSVC 同日标注 `Exploitation: none / Automatable: no / Technical Impact: total`，目前**无公开在野利用**。

---

## 控制点

**启动期 `checkStart()`**
1. `0.0.0.0` / `::` / `*` / 空绑定 → `WILDCARD_OR_EMPTY_BIND`；
2. loopback（`127.x` / `localhost` / `::1`）→ 放行；
3. 非 loopback 且无显式 `remoteOptIn` → `REMOTE_BIND_NOT_OPTED_IN`；
4. 非 loopback 有 opt-in 但未鉴权 → `REMOTE_BIND_WITHOUT_AUTH`。

**请求期 `checkRequest()`**
1. 未鉴权 → `UNAUTHENTICATED_CALL`（不接受「本地所以可信」）；
2. `Content-Type` 缺失 / `text/plain` / `application/x-www-form-urlencoded` → 拒；仅接受 `application/json`（允许 `; charset=utf-8`）；
3. `Origin: null` / 非白名单 → 拒；浏览器面向时缺 Origin → 拒；本地 CLI（`browserFacing=false`）可无 Origin；
4. 缺 Host / `*.nip.io` `*.localtest.me` `*.sslip.io` `*.lvh.me` / 非白名单 Host → 拒；`X-Forwarded-Host` 与 Host 不一致 → 拒；
5. `Access-Control-Allow-Origin: *` → 拒；
6. `get-env` / `dump-env` / `read-process-env` → `ENV_DUMP_TOOL_DISABLED`。

配置对象**不可变**（`withXxx()` 返回新实例），每条拒绝都带稳定 reason 码，可直接落 `mcp_security_*` 埋点（见 `examples/governance-hardening/McpSecurityMetrics.java`）。

## 运行

```bash
javac -encoding UTF-8 -d /tmp/mcp-selftest McpLocalListenerGuard.java
java -Dfile.encoding=UTF-8 -cp /tmp/mcp-selftest com.example.mcp.transport.McpLocalListenerGuard
```

本期实测输出（2026-11-11，JDK 17.0.8）：

```
self-test OK (17 attack cases + 9 control cases + 4 boundary assertions)
total assertions: 30
loopback binding alone is NOT a security boundary: Content-Type + Origin + Host + auth are all required.
```

**对照组断言**：`legacyAllows()` 模拟「只检查是否绑 127.0.0.1」的旧基线 —— 旧基线会放行的「loopback + `text/plain` + 无 Origin」CSRF 组合，新方案必须拦下（**增量价值**）；旧基线放行的正常 Streamable HTTP 调用，新方案必须同样放行（**不得劣于既有基线**）。另有 4 条边界断言验证默认配置 fail-closed、loopback 识别范围、配置不可变性、reason 码稳定性。

## 与既有示例的分工

| 示例 | 守的层 | 典型攻击 |
| --- | --- | --- |
| [`connect-path-guard/`](../connect-path-guard/README.md) | **客户端出站连接路径**（URL 字符 / 私网 IP / DNS rebinding / argv 启动） | `CVE-2026-119025` Inspector 握手期 RCE、`123786` SSRF |
| **本示例 `local-listener-guard/`** | **服务端入站监听与请求头** | `112136` / `112137` / `123811` / server-everything |
| [`stdio-command-allowlist/`](../stdio-command-allowlist/README.md) | **拉起哪个 STDIO 进程、带什么参数** | Anthropic STDIO Won't-Be-Patched、`41903` git hook |
| [`tool-annotation-gate/`](../tool-annotation-gate/README.md) | **注册期元数据 + 审批门** | `123812`、注解谎报、catalogue 漂移 |

## 诚实边界

- 本示例是**请求元数据判定器**，不是 HTTP server：没有真实 socket、没有 TLS、没有会话管理，`authenticated` 是配置布尔值而非真实认证结果。
- rebinding 后缀域名黑名单只是**速查表**，不能替代解析后真实 IP 校验 —— 生产请与 `McpResolvedIpGuard` 组合使用，并覆盖重定向逐跳重校验与 SNI/Host 一致性（**跨期待办，仍未做**）。
- 未实现 Private Network Access 头（`Sec-Fetch-Site` / `Sec-Fetch-Mode`）判定、CSP 与浏览器侧缓解；这些属于客户端/浏览器职责。
- Siemens 公告影响版本范围较窄（Mendix Studio Pro 11 `>= V11.15.0` 与 `= V11.12.6 < V11.12.7`），本示例把它抽象为通用准入模式，**不代表本框架受该 CVE 影响**。
