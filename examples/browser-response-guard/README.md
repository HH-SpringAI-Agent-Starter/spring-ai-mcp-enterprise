# browser-response-guard（V1.78 · 第十段：浏览器 / 出站响应面 + 本地绑定面）

> REFERENCE ONLY — 纯 JDK 17 零依赖，不进 Maven reactor。本类**未接入** 21 个主模块。

## 判词

前九段（注解 / 注册中心 / 只读声明 / 决策层 / 配置面 / 结果面 / 身份传播面 / 会话传输绑定面 / 执行环境隔离面）管的都是**请求端**。这一段管一个被忽略的方向：

**请求端的绑定守住了，服务器发出去的响应头和它监听的那张网卡，照样能把边界拆掉。**

- CORS 是跨域读取策略，**不是认证**；
- loopback 是网络位置，**不是授权**；
- session id 是传输句柄，**不是身份**。

## 证据（近 3 天，均已回原文核实）

| CVE / 公告 | 日期 · 分 | 机制 | 对位控制点 |
| --- | --- | --- | --- |
| `CVE-2026-34237` / `GHSA-hv2w-8mjj-jw22` | 11-22 · CVSS 6.1 | MCP **Java SDK** `mcp-core` 在 SSE / Streamable HTTP 下发 `Access-Control-Allow-Origin: *`；攻击者页面读 SSE 首帧 session id 后跨域 POST，浏览器成网络位置。影响 `<1.0.0/<1.1.1/<0.18.3`，修 `1.0.1/1.1.1/0.18.3` | CP1 CORS / CP2 SESSION |
| `CVE-2026-112136` / `112137` | 11-23 · CVSS 8.8 / 5.4 | Siemens Mendix Studio Pro MCP Server 远程交互 + 浏览器 CSRF，工程工作站任意 tool execution；官方缓解首条「只绑 loopback + Origin/Referer allowlist」 | CP3 BIND / CP5 CSRF |
| `CVE-2026-134817` / `GHSA-8v48-c263-r637` | 11-22/23 · CVSS 7.1 · CWE-88 | mcp-server-kubernetes `<4.2.0` dash 开头位置操作数（`--filename=/etc/passwd`）被当 kubectl flag 读宿主机文件；修 `4.2.0`（`assertNotFlagLike()`） | CP4 ARG |
| 414 台公网 MCP server 审计 | 2026 | 91.8% 无 OAuth（第三方评估，非本框架数据） | 全链 fail-closed |

## 六道闸

| 闸 | 代码 | 检查 |
| --- | --- | --- |
| CP1 | `CORS_*` | ACAO 仅精确 allowlist；通配拒绝；allow-credentials 不得配通配；SSE / Streamable HTTP 分别判定 |
| CP2 | `SESSION_*` | 响应体 session id 必须绑定签发 origin；句柄暴露给跨域读取者拒绝 |
| CP3 | `BIND_*` | 只绑 loopback；`0.0.0.0` / `::` / 任意网卡 / 文档 IP 一律拒绝 |
| CP4 | `ARG_FLAG_LIKE` | dash 前缀位置操作数一律拒绝（`--flag` / `--flag=value` / `-x`） |
| CP5 | `CSRF_*` | 改动类 POST 必带 Origin 且在 allowlist + 有效 CSRF token + Referer 同源 |
| CP6 | AUDIT | append-only 脱敏：session id 掩码、origin query 删除 |

## 自检实测

```
javac -encoding UTF-8 McpBrowserResponseGuard.java
java -Dfile.encoding=UTF-8 McpBrowserResponseGuard

self-test: OK
  attack   : 20/20 blocked
  control  : 4/4 allowed (false positives: 0)
  boundary : 2/2 assertions
  legacy-only blocks (new guard catches, baseline misses): 20
  total assertions: 26
```

旧基线（只看「有 session id + 有 CORS 头」）会放行全部 20 条攻击——`Access-Control-Allow-Origin: *` 在其眼里也算「有 CORS 头」，这正是本期要打掉的假设。

## 诚实边界

- allowlist / CSRF token / 绑定地址硬编码仅为可运行演示，生产须由 ingress / servlet container / service mesh 与配置中心共同保证；
- 升级 Java SDK 只是移除一个 origin 绕过，**反向代理仍可能重新加上通配头**，须在实际 ingress 逐个路径复查响应头；
- 本框架**不受**上述第三方 CVE 直接影响；守卫未接入主模块；
- IP 解析为字面量分类、非真实 socket 绑定探测；session 绑定为字符串标志，非密码学签名。
