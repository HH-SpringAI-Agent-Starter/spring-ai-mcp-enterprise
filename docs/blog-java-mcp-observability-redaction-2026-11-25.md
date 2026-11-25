# 你的 MCP Server 调用是安全的，但日志把口令卖了：从 AWS 135614 到「可观测性出口」的第十一道路

> 掘金 / CSDN 投稿稿 · 2026-11-25 · Spring AI MCP Enterprise V1.79

---

## 开头：一个反直觉的事实

过去十期我们一直在讲"哪些东西不是安全边界"：注解不是、注册中心不是、只读声明不是、模型自证不是、CORS 头不是、loopback 不是。

今天讲第十一个，也是最容易被忽视的一个：

**你的调用本身完全合法、鉴权齐全、审计也记了——但"把这次调用记下来"这个动作，把数据库口令裸奔进了日志文件。**

这不是假设。11-24，Apache IoTDB MCP Server 的 `CVE-2026-135747` 进 NVD：server **启动时把连接口令明文写进日志**。任何人读到日志就拿到凭据——而读日志是运维的日常工作，根本不需要攻击者身份。

同一天，AWS 发布 `2026-200-AWS` 公告：`awslabs.ecs-mcp-server < 0.1.36` 的 troubleshooting 工具对敏感信息移除不当，远程攻击者通过 **prompt injection** 让 agent 主动请求"排障指引"，就能拿到 ECS task definition 里本应被 `sensitiveData` 开关脱敏的**明文环境变量和 secret 引用**。官方修法是升级到 0.1.36 —— **并且轮换所有明文 env 凭据**。

最后一句意味深长：**打补丁不等于清干净**。因为泄露出去的东西，关掉开关也收不回来。

---

## 判词：可观测性出口 = 数据出口

我把这一层叫**日志 / 遥测输出面（observability output plane）**，判词是：

> 日志、指标、追踪、诊断输出是"可观测性"，不是"数据已脱敏的证明"。
> `redactSensitive=true` 是配置声明，prompt injection 一绕就过；
> 凡是要离开信任边界的字符串，必须在出口处按内容独立定级脱敏，默认 fail-closed。

为什么"开关"靠不住？三条真实证据：

| 证据 | 打掉的假设 |
| --- | --- |
| IoTDB 135747：启动日志明文口令 | 「敏感数据才会经过脱敏开关」——口令写入日志压根没经过任何开关 |
| AWS 135614：prompt injection 绕过 sensitiveData | 「开了开关就安全」——诱导语句让工具主动把数据读出来 |
| mcp-server-fetch 134492（11-23）：IPv6 site-local `fec0::/10` 被当公网放行 | 「出口 allowlist 挡住内网段就够了」——地址族不全就有洞 |

而买方也已经把话说明白了。11-22 中国经营报援引 IDC MarketScape 2026（28 家厂商）：企业引入智能体平台的首要目标是"强化治理与合规"（58.7%），蚂蚁数科把验收拆成四问——**智能体代表谁、可执行什么动作、何时必须转人工、出偏差能不能追溯**。

最后一问"能不能追溯"，落到工程上就是可观测性出口。** customers 要的是能进标书的出口可审计，不是一个 `redactSensitive: true`。**

---

## 六道闸：一个可运行参考实现

我在项目里加了 `examples/observability-redaction-guard/`，纯 JDK 17 零依赖，不进 Maven reactor。核心是一个 `decide()` 判定链 + 六道闸：

```java
public static Verdict decide(EgressRecord r) {
    if (r == null || r.payload == null || r.payload.isEmpty()) {
        return Verdict.deny(Code.INVALID, "empty payload fail-closed");
    }
    String p = norm(r.payload);

    // CP4 出口端点 allowlist + IP 分类（先拦外发目标）
    String host = allowlistedHost(r.endpoint);
    if (host == null && r.endpoint != null && !r.endpoint.isEmpty()) {
        return Verdict.deny(Code.EGRESS_NOT_ALLOWED, safe(r.endpoint));
    }
    if (host != null && isUnsafeIp(host.replaceAll("^https?://", "").split("/")[0])) {
        return Verdict.deny(Code.EGRESS_IP_UNSAFE, "egress host classified unsafe");
    }

    // CP2 prompt injection 绕过：声明真假都不进允许分支
    if (hasInjection(p) && (r.promptInduced || "diagnostic".equals(r.channel))) {
        return Verdict.deny(Code.PROMPT_INJECTION_BYPASS, "declaration ignored");
    }

    // CP1 凭据 / CP3 secret 引用（内容独立定级，不看声明）
    if (hasCredential(p)) return Verdict.deny(Code.CREDENTIAL_PATTERN, "credential in egress text");
    if (hasSecretRef(p)) return Verdict.deny(Code.SECRET_REFERENCE, "secret reference in egress text");

    // CP5 堆栈不得拼请求参数；CP6 append-only 脱敏审计
    // ...（完整代码见仓库）
}
```

六个控制点分别守：

- **CP1 凭据模式**：`password=` / `token=` / `Bearer` / `AKIA` / `PRIVATE KEY` / jdbc pwd —— 命中脱敏或拒绝；
- **CP2 绕过防护**：`redactSensitive` 声明只进审计、不进允许分支；诱导语句强制脱敏；
- **CP3 secret 引用**：`${ENV}` / `process.env` / `secretRef` / task-def env 不得原样出口；
- **CP4 出口 allowlist + IP 分类**：IPv4 私有/回环/链路本地/文档段 + IPv6 site-local `fec0::/10` / ULA / `::1` / `fe80::` 全部拒绝；
- **CP5 堆栈约束**：只记 error code，请求参数不得拼进错误路径；
- **CP6 脱敏审计**：append-only，出口原文与审计行零明文残留。

---

## 一个方法论：只写顺风测试会选错方案

开发过程中，对照组断言抓到两个真问题，值得单独说：

1. **已脱敏文本被误杀**。合法对照 C8 写的是 `password=<redacted> written to sink`，第一版 `hasCredential()` 简单替换占位符后再检测，把后面的普通词 `written` 当成了新口令值 → 误杀。改成"marker 后紧跟 `<redacted>`/`<ref>` 就跳过"的幂等逻辑才对。
2. **攻击夹具自己把判定绕过了**。attack #18 想测"堆栈里没写 error code 就该拒绝"，结果夹具文本里带了 "error" 字样，判定链误以为它合规、直接放行 → 测试假绿。把夹具改成不含 error 标识的原始帧才真正命中。

**如果你只写"攻击被拦"的顺风测试，这两个 bug 一个都发现不了。** 对照组（合法输入必须 0 误杀）+ 断言"不得劣于旧基线"是硬要求。

最终自检：

```
self-test: OK
  attack   : 20/20 blocked
  control  : 8/8 allowed (false positives: 0)
  boundary : 2/2 assertions
  legacy-only blocks (new guard catches, baseline misses): 18
  total assertions: 30
```

旧基线（只看"`redactSensitive=true`"）会放行 18 / 20 条攻击——这就是"声明不是执行"的量化证据。

---

## 落地清单（9 条，拿走就能用）

1. 盘点所有离开信任边界的字符串出口：应用日志、访问日志、指标标签、trace attribute、诊断/排障响应、错误堆栈、告警通知、日志转发端点。
2. 把脱敏从"配置开关"改成"出口过滤器"：过滤链最外层按内容独立定级，`redactSensitive` 只进审计。
3. 覆盖 prompt injection 场景：agent 可能被诱导主动请求"把配置/环境变量打出来"，诊断通道要当高风险管理。
4. 出口端点精确 allowlist，不用通配、不做后缀匹配。
5. IP 分类补全 IPv6：`fec0::/10`（site-local）、`fc00::/7`（ULA）、`::1`、`fe80::` 全部拒绝。
6. 异常路径只回 error code + 脱敏摘要，绝不拼请求参数原文。
7. 审计 append-only + 掩码幂等；`<redacted>` 二次处理不误杀。
8. 每次改规则都跑对照组：合法日志 0 误杀是硬门槛。
9. **升级第三方 MCP Server 后，轮换曾经明文落盘的凭据**——这是 AWS 公告的原文要求，不是可选项。

---

## 诚实的边界

- 本文所有示例在 `examples/`，未接入 21 个主模块；纯 JDK 演示，生产须由日志框架过滤器 + 配置中心 + SIEM 共同保证；
- 掩码不是加密，日志一旦落盘明文无法靠开关收回；
- 本框架不受上述第三方 CVE 直接影响，引用是为生态治理空白背书；
- IP 判定为字面量分类、不做真实 DNS 解析；凭据检测为词法归一化非语义解析。

仓库：https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise
验收清单与三档报价：`docs/observability-redaction-boundary-2026-11-25.md`
