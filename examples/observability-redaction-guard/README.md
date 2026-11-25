# observability-redaction-guard（V1.79 · 第十一段：日志 / 遥测输出面）

> REFERENCE ONLY — 纯 JDK 17 零依赖，不进 Maven reactor。本类**未接入** 21 个主模块。

## 判词

前十段（注解 / 注册中心 / 只读声明 / 决策层 / 配置面 / 结果面 / 身份传播面 / 会话传输绑定面 / 执行环境隔离面 / 浏览器出站响应面）管的都是**调用自身**。这一段管一个全漏的方向：

**调用本身合法，但"把这次调用记下来 / 发出去"这个动作，把凭据、密钥引用和内网拓扑从日志行、指标标签、诊断响应里泄漏出去。**

- `redactSensitive=true` 是**配置声明**，prompt injection 一绕就过；
- 启动日志把口令写进盘，**完全不经过任何开关**；
- 凡离开信任边界的字符串，必须在**出口处按内容独立定级脱敏**，默认 fail-closed。

## 证据（11-23~11-25，均已回原文核实）

| CVE / 公告 | 日期 · 分 | 机制 | 对位控制点 |
| --- | --- | --- | --- |
| `CVE-2026-135747` | 11-24 · CWE-532 | Apache IoTDB MCP Server ≤0.2.0 启动把连接口令明文写日志，修 0.3.0；读日志是日常运维动作，可观测性出口=数据出口 | CP1 凭据 |
| `CVE-2026-135614` / AWS `2026-200-AWS` | 11-24 · CVSS 5.5 | awslabs.ecs-mcp-server <0.1.36 troubleshooting 工具脱敏不当；prompt injection 诱导 agent 请求 guidance 拿到 task definition 明文 env + secret 引用；修 0.1.36 **且轮换凭据** | CP2 绕过 / CP3 引用 |
| `CVE-2026-134492` | 11-23 · CVSS 2.3 | Anthropic mcp-server-fetch SSRF guard 把 IPv6 site-local `fec0::/10` 当公网放行；修 2026.11.11 | CP4 出口 IP 分类 |
| 中国经营报 11-22 / IDC MarketScape 2026 | 28 家厂商 | 企业引入智能体首要目标「治理与合规」58.7%；验收四问：代表谁 / 能做什么 / 何时转人工 / 能否追溯 | 全链 fail-closed + 审计 |

## 六道闸

| 闸 | 代码 | 检查 |
| --- | --- | --- |
| CP1 | `CREDENTIAL_PATTERN` | 出口文本扫凭据模式（password=/token=/Bearer/AKIA/PRIVATE KEY/jdbc pwd），命中脱敏或拒绝；`<redacted>` 占位符幂等跳过 |
| CP2 | `PROMPT_INJECTION_BYPASS` | 不信任 `redactSensitive` 声明；诱导语句 + 诊断通道强制脱敏；声明关闭且含敏感内容一律拒绝 |
| CP3 | `SECRET_REFERENCE` | `${ENV}` / `process.env` / `secretRef` / task-def env 引用不得原样出口 |
| CP4 | `EGRESS_*` | 外发端点精确 allowlist；IP 分类完整（IPv4 私有/回环/链路本地/文档段 + IPv6 site-local `fec0::/10`/ULA/::1/fe80::） |
| CP5 | `STACKTRACE_PARAM_LEAK` | 堆栈只记 error code；请求参数不得拼进错误路径 |
| CP6 | `AUDIT_PLAINTEXT_RESIDUE` | append-only 脱敏审计；出口原文与审计行零明文残留 |

## 自检实测

```
javac -encoding UTF-8 McpObservabilityRedactionGuard.java
java -Dfile.encoding=UTF-8 McpObservabilityRedactionGuard

self-test: OK
  attack   : 20/20 blocked
  control  : 8/8 allowed (false positives: 0)
  boundary : 2/2 assertions
  legacy-only blocks (new guard catches, baseline misses): 18
  total assertions: 30
```

旧基线（只看「声明了 `redactSensitive=true`」）会放行 18/20 条攻击——这正是本类要打掉的假设：声明不是执行。

## 诚实边界

- allowlist / 凭据正则 / IP 分类为可运行演示，生产须由日志框架过滤器 + 配置中心 + SIEM 共同保证；
- 掩码**不是加密**，日志一旦落盘明文无法靠"关掉开关"收回（对位 AWS「升级后须轮换凭据」）；
- 本框架**不受**上述第三方 CVE 直接影响；守卫未接入 21 主模块；
- IP 判定为字面量分类、不做真实 DNS 解析；凭据检测为词法归一化非语义解析；session/实例去重为演示约束。
