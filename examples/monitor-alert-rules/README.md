# MCP 安全告警规则参考实现（V1.65 · 2026-11-07）

> 兑现 V1.64 待办⑤：把 Wiz 90 天蜜罐的攻击路径、11-03~11-06 的 CVE 波，从「情报」变成「**可运行的检测规则**」。
> **本目录是参考实现，不进 Maven reactor、不参与 `mvn test`，零构建风险。** 复制到自己的项目后按埋点契约接入即可。

---

## 为什么需要单独一层告警规则

`config/prometheus/alerts.yml` 已有的规则是**可用性视角**（错误率、延迟、服务存活、流量骤降）。但 11-06 Wiz 报告里的攻击，**在服务指标上几乎不表现为异常**：

| 攻击 | 为什么常规指标看不出来 |
| --- | --- |
| `CVE-2026-59822` 一个字符 bearer 过鉴权 | 调用**成功**、延迟正常、错误率 0 |
| `CVE-2026-42271` 伪造配置启动矿机 | **MCP 握手正常返回**，业务指标全绿 |
| 盲提示注入拿 shell | 工具调用成功，只是参数里藏了指令 |
| 从进程内存抠 proxy master key | 没有 HTTP 请求，纯本地读内存 |

> 结论：**安全告警不能只从「调用是否成功」推导，必须从「拒绝事件 / 出站行为 / 子进程 / 凭据访问」四类独立信号埋点。** 这就是本目录存在的意义。

---

## 文件说明

| 文件 | 作用 | 接入方式 |
| --- | --- | --- |
| `mcp-security-alerts.yml` | Prometheus / Alertmanager 规则，覆盖 10 类安全信号 | 追加到 `prometheus.yml` 的 `rule_files`（`docker-compose.yml` 已挂载 `config/prometheus/`） |
| `McpSecurityAlertRules.java` | 注册到 `mcp-monitor` 的 `McpAlertService` 自定义规则 + 拒绝事件 → SIEM 回调 | 复制为 `@Configuration` 类，注入 `McpAlertService` |
| `README.md` | 本文件：埋点契约、攻击路径映射、验收方式 | — |

---

## 埋点契约（`mcp_security_*` 指标）

✅ **V1.66（2026-11-08）状态更新 —— 契约已落地**：下表 10 个指标已由 [`examples/governance-hardening/McpSecurityMetrics.java`](../governance-hardening/McpSecurityMetrics.java) 实现（零依赖 `ConcurrentHashMap` + `AtomicLong`，`exportPrometheus()` 直接输出），并挂进 [`McpGatewayGuardFilter`](../governance-hardening/McpGatewayGuardFilter.java) 的每个拒绝分支（短 bearer / 4 MiB / host 不一致 / 注册 `command` 字段 / 受众不匹配 / 待审批 / 凭据取用）。三个新 Java 示例已实测 `javac` 编译 + `main()` 自检全绿。
⚠️ **仍然诚实保留**：`mcp-monitor` **主模块**目前仍只导出 `mcp_tool_invocations_total` / `mcp_tool_errors_total` / `mcp_tool_latency_ms` / `mcp_gateway_*` / `mcp_build_info`；`mcp_security_*` 位于 `examples/` 层，生产集成需把 `McpSecurityMetrics` 单例挂到 actuator 端点（或替换为 Micrometer `MeterRegistry`）。**本目录不需要再自行补埋点**，只需接导出端点。

| 指标 | 类型 | label | 埋点位置 |
| --- | --- | --- | --- |
| `mcp_security_rejections_total` | counter | `reason`, `tool`, `tenant` | 每次 fail-closed 拒绝（401/403/413/202 待审批） |
| `mcp_security_short_bearer_total` | counter | `tenant` | bearer token 长度 < 8 或格式非法 |
| `mcp_security_registration_rejected_total` | counter | `reason`, `tenant` | 注册请求含 `command` / shell 字段、默认 disabled 被尝试启用 |
| `mcp_security_host_mismatch_total` | counter | `tenant` | Host / X-Forwarded-Host 不一致 |
| `mcp_security_egress_blocked_total` | counter | `host`, `tool` | 出站白名单拒绝（含非同源重定向、DNS 私网地址） |
| `mcp_security_credential_access_total` | counter | `principal`, `tool` | 凭据代理取用（短令牌 / provider secret） |
| `mcp_security_child_process_total` | counter | `parent`, `exe` | 非白名单子进程派生（对标 XMRig 藏 `.claude/unicorn`） |
| `mcp_security_pending_approvals` | gauge | `risk_tier` | 当前待 HITL 审批数 |
| `mcp_security_body_too_large_total` | counter | `tenant` | 请求体超 4 MiB |
| `mcp_security_tool_enabled_total` | counter | `tool`, `actor` | 工具从 disabled → enabled 的准入变更 |

**最小埋点示例**（挂在过滤器拒绝分支；若用 V1.66 的 `McpSecurityMetrics` 则已内置，此段仅供改用 Micrometer 时参考）：

```java
// 与 examples/governance-hardening/McpGatewayGuardFilter.java 的 reject() 对齐
private void reject(HttpServletResponse resp, String reason, String tool, String tenant) {
    MeterRegistry registry = ...; // Spring Boot Actuator / Micrometer
    registry.counter("mcp_security_rejections_total",
                     "reason", reason, "tool", tool, "tenant", tenant).increment();
    if ("short_bearer".equals(reason)) {
        registry.counter("mcp_security_short_bearer_total", "tenant", tenant).increment();
    }
    auditSink.record(/* identity, tool, reason, traceId, arguments */);  // 拒绝也必须留痕
    resp.setStatus(statusFor(reason));
}
```

---

## 攻击路径 → 规则 → 验收方式（10 条，可直接进标书）

| # | 真实攻击面 | 规则 ID | 信号 | 验收方式 |
| --- | --- | --- | --- | --- |
| 1 | `CVE-2026-59822` 一个字符 bearer 过 LiteLLM MCP Gateway | `McpShortBearerToken` | `mcp_security_short_bearer_total` 突增 | 发 `Authorization: Bearer x` → 必须 401 + 计数 +1 + 审计可检索 |
| 2 | `CVE-2026-42271` 伪造配置 `command` 启动矿机且握手正常 | `McpMaliciousRegistration` | `mcp_security_registration_rejected_total{reason="command_field"}` | 提交含 `command` 的注册请求 → 必须拒绝 + 告警 |
| 3 | `CVE-2026-48710` host-header 绕过 → 链式未认证 RCE（Qilin 勒索） | `McpHostHeaderMismatch` | `mcp_security_host_mismatch_total` | 伪造 `X-Forwarded-Host` → 403 + 计数 |
| 4 | 从运行进程内存抠 proxy master key | `McpCredentialAccessAnomaly` | `mcp_security_credential_access_total` 短时突增 | 连续取用 > 阈值 → 告警；审计流必须能查到 principal |
| 5 | 盲提示注入拿 shell（DNS 带外确认 / Base64 + Pastebin） | `McpEgressBlockedBurst` + `McpUnexpectedChildProcess` | 出站白名单拒绝 / 非白名单子进程 | 非常规出站立即告警；shell 派生立即 critical |
| 6 | XMRig 藏 `.claude/unicorn` 伪装 AI 开发工具 | `McpUnexpectedChildProcess` | `mcp_security_child_process_total{exe=~"xmrig|...|python.*-c"}` | 非白名单子进程触发 critical |
| 7 | `CVE-2026-108395` 全 SDK 无界请求体 DoS（4 MiB） | `McpOversizedBody` | `mcp_security_body_too_large_total` | 发 >4 MiB → 413 + 计数 |
| 8 | `CVE-2026-111769` / `CVE-2026-123786` 跨源重定向带出 token | `McpCrossOriginRedirect` | `mcp_security_egress_blocked_total{reason="cross_origin_redirect"}` | 跨源 307/308 必须拒绝且不带自定义头 |
| 9 | `CVE-2026-111771` / `123271` / `123272` 受众 / 资源混淆 | `McpAudienceMismatch` | `mcp_security_rejections_total{reason=~"audience_mismatch|resource_confusion"}` | 用别的服务签的 token 调本服务 → 401 + 计数 |
| 10 | 注册准入被绕过（对标 Uber「默认 disabled」） | `McpToolEnableBurst` | `mcp_security_tool_enabled_total` 短时批量启用 | 5 分钟内启用 > 3 个工具 → 人工复核告警 |

> 第 9 条对应 11-06 当日披露的 `CVE-2026-111771`（官方 TS SDK 不校验 `aud` / `AuthInfo.resource`）；第 8 条对应同日 `CVE-2026-123786`（Agenta `follow_redirects=True` 带 Secret token）。**这两条是 V1.65 相对 V1.64 新增的检测能力。**

---

## 与 `McpAlertService` 的关系（两层，别混）

| 层 | 组件 | 数据源 | 适合检测 |
| --- | --- | --- | --- |
| **应用内实时** | `McpSecurityAlertRules.java` → `McpAlertService.addRule()` | `McpMetricsCollector.ToolMetrics`（错误率 / 延迟 / 调用数） | 凭据盗刷、工具被爆破、拒绝风暴的**进程内快速反应**（含 5 分钟抑制） |
| **集群 / SIEM** | `mcp-security-alerts.yml` → Prometheus / Alertmanager | `mcp_security_*` 埋点契约 | 跨实例聚合、长时窗趋势、出站 / 子进程 / 凭据访问等**非调用指标** |

`McpAlertService` 的 `AlertRule` 只吃 `ToolMetrics`，**表达不了「出站白名单被拒」「子进程派生」「凭据访问」** —— 所以这四类必须走 Prometheus 层，不能硬塞进应用内规则。这是本参考实现刻意分两层的原因。

---

## 三步接入

1. 把 `mcp-security-alerts.yml` 复制到 `config/prometheus/`，在 `prometheus.yml` 的 `rule_files` 追加该路径（原 `alerts.yml` 保留，两者互补）。
2. 把 `McpSecurityAlertRules.java` 作为 `@Configuration` 加入自己的服务，注入 `McpAlertService`（`mcp-monitor` 自动装配已提供该 Bean）。
3. ~~按上表在 `McpGatewayGuardFilter` 的拒绝分支补 `mcp_security_*` 埋点~~ → **V1.66 已兑现**：过滤器已接入 `McpSecurityMetrics`，直接把 `getMetrics().exportPrometheus()` 挂到端点即可；用「验收方式」列逐条打一次，确认**告警 + 审计**双落地。新指标名写错会 fail-fast，不会静默丢数据。

> **审计优先于告警**：告警可能丢、可能被抑制（默认 `suppressMs = 300_000`），但 append-only 审计流不能丢。所有拒绝事件必须先写审计再发告警 —— 这也是广发 13322 号公告「服务行为自动留痕、日志不可篡改」的落地方式。

---

## 相关文档
- 情报来源：[`docs/earnings-report-2026-11-07.md`](../../docs/earnings-report-2026-11-07.md) 第 1 节（11-06 三 CVE）
- 攻击面清单：[`docs/enterprise-rfp-checklist.md`](../../docs/enterprise-rfp-checklist.md)（16 类攻击面 → 治理模块）
- 加固参考实现：[`examples/governance-hardening/`](../governance-hardening/README.md)
- 现有可用性告警：[`config/prometheus/alerts.yml`](../../config/prometheus/alerts.yml)
