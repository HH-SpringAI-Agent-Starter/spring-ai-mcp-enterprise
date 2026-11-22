# 执行环境隔离守卫（V1.77 · 2026-11-22）

> **第九段：执行环境隔离面。** 前八段管「谁能动 / 怎么判 / 交出什么 / 收到谁的身份 / 从哪个会话哪个 Origin 进来」，
> 这一段管「**工具真正落到执行环境那一刻，沙箱 confinement 到底闭没闭合**」。

## 判词

**沙箱 confinement 只守住作者画的那条路；声明层（readOnlyHint / sessionId / 登录态）一律不参与授权。**
必须在执行点独立定级、强制命令 allowlist、env 源头与键名双白名单、写路径与读路径同等 confinement、
会话按 issuer + subject 绑定，默认只读 fail-closed。

## 对位证据（11-18~11-22 实时抓取）

| CVE | 事实 | 对位闸 |
| --- | --- | --- |
| `CVE-2026-79748` / GHSA-mx89-jjx9-gjr8（CVSS 9.9） | MCPHub 0.12.14 及更早：`POST /api/servers` 与 `PUT /api/servers/name` 只查登录不查 admin、不限制 command，任何登录非管理员可注册 `command=/bin/sh` 的 server 立即执行（Docker 镜像常以 root 跑），edit 端点无归属校验可覆写管理员建的 server | CP2 / CP4 |
| Hermes Agent `GHSA-p22p-9ph6-gmrh`（11-19, High） | docker/ssh 终端后端下 agent 从任意后端进程可写目录读回 env 文件，接受每个 `KEY=VALUE` 合并进宿主机启动的进程，可注入 `LD_PRELOAD` / `DYLD_INSERT_LIBRARIES` 以 gateway 用户执行代码、触达 provider keys 与 session history | CP3 |
| `CVE-2026-62964`（7.8）Gortex / `75612`（7.5）yt-mcp / `75518`（4.8）DOMShell | Gortex read 工具做仓库 confinement + symlink 检查，write 工具却收绝对路径跳过两查 → 可写 `~/.zshrc`；yt-mcp `add_attachment` 读任意文件外发；DOMShell `domshell_eval` 标 `readOnlyHint:true` 却跑任意 CDP `Runtime.evaluate`，客户端显示错误审批 UI | CP1 / CP4 |
| `CVE-2026-52869` / GHSA-jpw9-pfvf-9f58（7.1, 11-20 VulnCheck 在野） | MCP Python SDK SSE / stateful Streamable HTTP 仅凭 `session_id` 路由、不校验同一认证客户端；多人共享一个 OAuth client 时若不填 `AccessToken.subject`，只按 client 分离不按 user → 跨用户会话注入 | CP5 |
| Jupyter MCP Server（9.3）/ `CVE-2026-76807` Todoist | HTTP 模式 `/api/connect` 无保护经 DNS rebinding 把 kernel 指向攻击者基建；Todoist pre-10.3.3 未校验 Host/Origin + 通配 CORS，恶意网页可驱动 agent 的 SaaS 账户 | CP5 / CP6 |

## 六道闸

| 闸 | 控制点 | 实现位置 |
| --- | --- | --- |
| CP1 | 读写路径同等 confinement：拒绝对路径逃逸 / `..` 遍历 / 符号链接逃逸 / 控制字符 | `checkPath()` / `normalize()` |
| CP2 | 命令 allowlist + argv shell 元字符拒绝 + 解释器直执拒绝 + 非管理员写注册拒绝 | `checkCommand()` |
| CP3 | env 源头白名单 + 危险键（`LD_PRELOAD` 等）拒绝 + 值中命令替换拒绝 | `checkEnv()` |
| CP4 | 服务端独立定级：写动词不看 readOnlyHint，必须审批令牌；声明说谎单独拒绝 | `checkTier()` |
| CP5 | 会话按 issuer + subject 绑定；共享 OAuth client 无真实 subject 拒绝；主体不一致拒绝 | `checkSubject()` |
| CP6 | append-only 脱敏审计：不记原始 env 值 / argv 凭据，只记规范化 op/path/subject | `appendAudit()` |

## 编译与自检（纯 JDK 17 零依赖）

```bash
javac -encoding UTF-8 McpExecutionEnvironmentGuard.java
java -Dfile.encoding=UTF-8 McpExecutionEnvironmentGuard
```

实测输出：

```
self-test: OK
  attack   : 20/20 blocked
  control  : 4/4 allowed (false positives: 0)
  boundary : 2/2 assertions
  legacy-only blocks (new guard catches, baseline misses): 20
  audit entries: 5
  total assertions: 26
```

- **26 断言全绿**：20 条攻击覆盖路径逃逸 / 命令注入 / env 注入 / 只读声明说谎 / 会话主体失配五类。
- **4 个合法调用 0 误杀**：只读读文件 / 带审批的合法写 / 搜索 / 范围内附件。
- **旧基线漏 20 条全拦**：旧基线只看 sessionId 非空 + readOnlyHint，本期全部攻击都能绕过它。
- 2 boundary：审计脱敏（`s3cr3t-value` 与原始 subject 不落盘）、空请求 fail-closed。

## 诚实边界

- **本框架不受**上述第三方 CVE 直接影响；引用为设计依据，非「已修复自身漏洞」声明。
- 守卫**仍在 `examples/` 层未接入 21 个主模块**；allowlist / issuer / 审批令牌硬编码，
  符号链接检测为集合模拟非真实 `lstat`，均不得当生产组件。
- 生产须把策略外置到配置中心 + KMS 轮换 + 共享会话存储；单进程审计表须换 append-only 日志系统。
