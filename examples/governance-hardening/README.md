# governance-hardening — MCP 治理加固参考实现（V1.66）

> **状态**：参考片段 / REFERENCE ONLY。位于 `examples/` 下，**不在 Maven reactor 中，不参与 CI 构建**。
> 依赖仅为 `spring-boot-starter-web` + `jakarta.servlet-api`（项目已有），无需新增第三方库；
> 新增的 `McpSecurityMetrics.java` 为**纯 JDK 零依赖**，可单独 `javac` 编译运行。
> 对应情报：[`docs/earnings-report-2026-11-06.md`](../../docs/earnings-report-2026-11-06.md) 第 1、2 节 ｜ 本期：[`docs/earnings-report-2026-11-08.md`](../../docs/earnings-report-2026-11-08.md)
> SEO 长文：[`docs/blog-java-mcp-wiz-honeypot-gf-securities-governance-2026-11-06.md`](../../docs/blog-java-mcp-wiz-honeypot-gf-securities-governance-2026-11-06.md)

## 为什么要加这一层

2026-11-03~11-06 一周内，攻防两端同时把「治理」推到台前：

- **攻**：Wiz.io 90 天蜜罐实录攻击者把 LiteLLM / MCP 网关当头等云入口打 —— 一个字符 bearer token 过鉴权（`CVE-2026-59822`）、伪造 MCP server 配置的 `command` 字段在连通性测试里启动矿机且**握手正常**（`CVE-2026-42271`）、host-header 绕过串成未认证 RCE（`CVE-2026-48710`），并从运行中的 Python 进程内存里取回代理主密钥。
- **守/买**：广发证券 3 天内第二个把 MCP 写进核心建设内容的征集（11-13 截止）、山东重工集团级平台「统一安全管控」二次招标（11-20 截止）；周大福（中国香港）把 identity propagation / access controls / audit trails 逐字写进 MCP 共享工具层 JD。
- **规范空白**：官方 SDK 自身连爆 `CVE-2026-108395`（全 SDK 无界请求体 DoS，统一修 4 MiB）、`CVE-2026-111769`（跨源重定向泄漏 refresh_token），加上 SurrealDB 会话指纹按方法名、Instatic 工具跳过对象授权 —— **「升级官方 SDK ≠ 安全」**。

结论：官方 SDK 与云厂商给的是「连接 + 工具」，缺的是「谁负责治理」。这一层必须由部署方自建。

## 文件清单

| 文件 | 作用 |
| --- | --- |
| `McpGatewayGuardFilter.java` | 网关前置过滤器：4 MiB 上限 / 鉴权 fail-closed / 同源校验 / 会话绑用户主体 / 工具默认禁用 / 写操作 HITL / 全程审计，七项收敛到同一入口；**V1.66 起每个拒绝分支落 `mcp_security_*` 埋点** |
| `BoundedRequestWrapper.java` | 流式请求体二次校验（防伪造 Content-Length / 分块绕过），并使 body 可重复读以供审计留痕 |
| **`McpSecurityMetrics.java`** 🆕 | **V1.66**：`mcp_security_*` 10 指标零依赖实现 + Prometheus 文本导出 + 未声明指标 fail-fast + `main()` 自检 |
| `application-hardening.yml` | 最小加固配置：网关 / 注册中心 / 治理 / 监控四层 |
| `README.md` | 本文件：控制点 ↔ 攻击面映射、集成步骤、自测清单 |

## 七条控制点 ↔ 攻击面 ↔ 模块

| # | 控制点 | 对应公开实证 | 项目模块 |
| --- | --- | --- | --- |
| 1 | 鉴权 **fail-closed**：signature/aud/iss/exp/scope 全校验，异常 401 | `CVE-2026-59822` 一个字符 token；`CVE-2026-123272/123271` OAuth resource confusion | `mcp-gateway` + `mcp-auth` |
| 2 | 请求体 **4 MiB** 上限（声明值 + 流式双限，超限 413） | `CVE-2026-108395` 官方 Java SDK `mcp-core` 无界请求体 DoS | `mcp-gateway` |
| 3 | 同源 + 禁跟随跨源 307/308 | `CVE-2026-48710` host-header 绕过；`CVE-2026-111769` 重定向带出 refresh_token / client_secret | `mcp-gateway` |
| 4 | 会话指纹 = 租户 + 用户主体 + clientId | `CVE-2026-121419` SurrealDB 按 access method 名 → 会话劫持 | `mcp-tenant` + `mcp-auth` |
| 5 | 工具注册**默认 disabled** + 配置字段校验 + 测试端点沙箱 | `CVE-2026-42271` 伪造配置启动矿机且握手正常；Uber 800+ servers 默认 disabled 生产实践 | `mcp-registry` + `mcp-governance` |
| 6 | 工具级 ACL **复用主 API 对象级授权** + 写操作 HITL | `CVE-2026-121458` Instatic MCP 侧门越权读草稿；Agiloft 写操作强制人工确认 | `mcp-governance` + `mcp-audit` |
| 7 | 进程行为可观测（shell 派生 / 异常子进程 / 出站白名单 / 凭据访问） | Wiz：内存抠 master key、XMRig 藏 `.claude/unicorn`、DNS 带外回调 | `mcp-monitor` |

## 🆕 V1.66：埋点从「契约」变成「可运行」

V1.65 的 [`examples/monitor-alert-rules/`](../monitor-alert-rules/README.md) 定义了 10 个 `mcp_security_*` 指标和 4 组 15 条 Prometheus 规则，但当时诚实标注「需由网关过滤器在拒绝路径上补埋点」。本期补齐：

| 分支 | 落点指标 |
| --- | --- |
| 短 bearer（`Bearer x`） | `short_bearer_total` + `rejections_total{SHORT_BEARER}` |
| 请求体 > 4 MiB | `body_too_large_total` + `rejections_total{BODY_LIMIT}` |
| Host / X-Forwarded-Host 不一致 | `host_mismatch_total` + `rejections_total{CROSS_ORIGIN}` |
| 注册请求带 `command` | `registration_rejected_total{reason="command_field"}` |
| 鉴权失败（aud / resource 关键字） | `rejections_total{AUDIENCE_MISMATCH}`，供 `McpAudienceMismatch` 精确告警 |
| 工具未准入 | `rejections_total{TOOL_DENIED}` |
| 写操作待审批 | `pending_approvals{risk_tier="high"} +1` + `rejections_total{PENDING_APPROVAL}`（**结单时须 -1**） |
| 凭据代理类工具 | `credential_access_total` |

两个尚未由本目录触发、需在执行层补的指标：`egress_blocked_total`（出站白名单）与 `child_process_total`（子进程采集）—— 调用 `getMetrics().egressBlocked(...)` / `.childProcess(...)` 即可，见第 3 步集成说明。

**本地验证（V1.66 实测通过）**：

```bash
javac -encoding UTF-8 -d /tmp/mcp-classes McpSecurityMetrics.java
java -cp /tmp/mcp-classes com.example.mcp.hardening.McpSecurityMetrics
# [McpSecurityMetrics] self-test OK, series=3
```

**导出到 Prometheus**：把应用级 `McpSecurityMetrics` 单例注入过滤器，再挂一个端点返回 `filter.getMetrics().exportPrometheus()`。生产环境把该端点加入抓取目标即可，规则文件无需改动。

## 集成到主项目（3 步）

1. 把两个 `.java` 复制到 `mcp-gateway/src/main/java/...`，把 `application-hardening.yml` 的键合并进 `mcp-server` 的 `application.yml`。
2. 将示例契约替换为项目内实际 Bean：
   - `TokenVerifier` → `mcp-auth` 的 JWT/OAuth2.1 校验器（务必校验 `aud`/`iss`，别只验签名）；
   - `CallerPolicy` → `mcp-governance` 的 tool-policy + `mcp-registry` 的准入状态；
   - `AuditSink` → `mcp-audit` / `mcp-monitor` 的落库与 SIEM 流。
3. 在 `mcp-monitor` 增加进程行为采集（shell / child-process / outbound-egress），并对 `X-MCP-Session-Key` 建立跨请求关联索引。

> ⚠️ **不要**把这个 Filter 当作唯一防线。它解决的是「请求进来之前」；工具执行期的沙箱、出站限制、密钥轮换仍需按
> [`docs/mcp-security-hardening-quickstart.md`](../../docs/mcp-security-hardening-quickstart.md) 前 5 步配套落地。

## 自测清单（可写进标书验收章节）

| 用例 | 期望 |
| --- | --- |
| `Authorization: Bearer x` | **401** + 审计事件 `AUTH_FAIL` |
| 请求体 > 4 MiB（含 chunked 且伪造 Content-Length） | **413** + `BODY_LIMIT` |
| 伪造 `X-Forwarded-Host` 与 `Host` 不一致 | **403** + `CROSS_ORIGIN` |
| 调用未审核启用的新工具 | **403** + `TOOL_DENIED` |
| 调用写操作且未审批 | **202** + `PENDING_APPROVAL` + 审批单号 |
| 同 access method 下用户 A 拿用户 B 的 `mcp-session-id` | 会话不互通（指纹按 subject 隔离） |
| 低权用户调用他人资源 | 拒绝（复用主 API 对象级授权） |
| 工具进程派生 shell / 非常规出站连接 | `mcp-monitor` 告警 |
| 🆕 `Authorization: Bearer x` | **401** 且 `mcp_security_short_bearer_total` +1（不只是审计事件） |
| 🆕 请求体 > 4 MiB | **413** 且 `mcp_security_body_too_large_total` +1 |
| 🆕 注册请求带 `command` 参数 | **403** 且 `mcp_security_registration_rejected_total{reason="command_field"}` +1 |
| 🆕 用别的服务签发的 token 调用 | **401** 且 `mcp_security_rejections_total{reason="AUDIENCE_MISMATCH"}` +1 |

> ⚠️ **本期新边界**：以上过滤器路径属于**请求路径**；`CVE-2026-119025` 证明还有一层「**连接路径**」攻击在工具列表出现前就执行，本目录管不到，须由 [`examples/connect-path-guard/`](../connect-path-guard/README.md) 在客户端侧承担。

相关售前一页纸（**18 类**攻击面逐条验收）：[`docs/enterprise-rfp-checklist.md`](../../docs/enterprise-rfp-checklist.md)｜云网关分层定位：[`docs/enterprise-cloud-gateway-comparison-2026-11-08.md`](../../docs/enterprise-cloud-gateway-comparison-2026-11-08.md)。
