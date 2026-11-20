# 会话 / 传输绑定守卫（V1.76 · 2026-11-20）

> **第八段：会话 / 传输绑定面。** 前七段管「谁能动 / 怎么判 / 交出什么 / 收到谁的身份」，
> 这一段管「**这条调用是从哪个会话、哪个 Origin、哪个 Host 进来的**」。

## 判词

**sessionId 不是安全边界；Host / Origin 头不是装饰；OAuth 凭据必须绑定 issuer。**
必须在每次调用点强制校验会话主体绑定、Origin 白名单与会话一致、Host RFC 合法性、
凭据 issuer 绑定，默认只读且 fail-closed。

## 对位证据（11-17~11-19 实时抓取）

| CVE | 事实 | 对位闸 |
| --- | --- | --- |
| `CVE-2026-132121` / `131857` | Supergateway 2.4.0–4.2.2 stdio-to-WebSocket 无 Origin 校验，恶意网页跨站 WebSocket 劫持本机 MCP 网关，PoC 12/12 runs 外传本地文件；未认证者用 prototype-inherited sessionId 打崩网关 | CP1 / CP2 |
| `CVE-2026-130007` | k8s-mcp-server 11-19 NVD：SSE 默认 0.0.0.0:8080 无 auth，sessionId 仅 correlation value，`POST /message` 无 authorization/Origin 校验即派发工具，read-only 默认关闭，修 1.1.0 | CP1 / CP5 |
| `CVE-2026-48710` | Starlette BadHost：Host 注入 `?` 使 URL 重解析边界漂移，`/admin` 被当 query，鉴权中间件读到 `/` 而放行；MCP 规范强制的免鉴权 OAuth discovery 端点成可靠利用路径，修 1.0.1 | CP3 |
| `CVE-2026-115563` | MCP Python SDK OAuth client 把凭据发给 server 自选 AS，issuer 未逐 path 校验、凭据未绑定 AS，修 2.2.0/1.30.0 且须显式传 `issuer=` | CP4 |

## 六道闸

| 闸 | 控制点 | 实现位置 |
| --- | --- | --- |
| CP1 | 会话仅接受服务端 HMAC 签发且绑定 principal+origin；拒绝伪造/未注册/prototype-inherited | `decide()` CP1 + `sign()` |
| CP2 | WS 升级/SSE 握手必须有 Origin、在白名单、与会话绑定 Origin 一致 | `decide()` CP2 + `canonOrigin()` |
| CP3 | Host 按 RFC 9112/3986 拒绝分隔符 / 问号 / 井号 / @ / 空白 / 控制字符 | `validHost()` |
| CP4 | 携带凭据必须声明 issuer 且等于预期授权服务器 | `decide()` CP4 |
| CP5 | 未认证派发第一道门拒绝；只读会话禁止写工具 | `decide()` CP5a / CP5 |
| CP6 | append-only 脱敏审计：sessionId 打码、不记原始凭据、规范 Host/Origin | `appendAudit()` |

## 编译与自检（纯 JDK 17 零依赖）

```bash
javac -encoding UTF-8 McpSessionTransportBindingGuard.java
java -Dfile.encoding=UTF-8 McpSessionTransportBindingGuard
```

实测输出：

```
self-test OK: 20 attack + 4 control + 2 boundary assertions
  legacy-only blocks (new guard catches, baseline misses): 6
  false positives on legal calls: 0
  audit entries: 32
```

- **26 断言全绿**，6 条为「只看 sessionId 非空 + Host 非空 + 有 Origin 头」旧基线漏掉的增量拦截。
- **4 个合法调用 0 误杀**：WS 升级 / SSE 连接 / 带绑定 issuer 的工具调用 / IPv6 Host 字面量。
- 含「不得劣于既有基线」对照断言（旧基线放行的 6 例，新守卫全拦）。
- 开发中断言抓到并修正 1 处真 bug：CP5 未认证派发被 CP1 空 sessionId 先拦导致不可达 → 前移为第一道门。

## 诚实边界

- **本框架不受**上述第三方 CVE 直接影响；引用为设计依据，非「已修复自身漏洞」声明。
- 守卫**仍在 `examples/` 层未接入 21 个主模块**；示例密钥硬编码、会话表进程内、
  Host/Origin 为字面量校验非完整 DNS 解析，均不得当生产组件。
- `TaskGrant` 类签名是 HMAC 演示，生产须走 KMS 轮换 + 共享存储做会话状态。
