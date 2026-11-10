# 昨天说治理能卖 10 倍价，今天说没治理连投标资格都没有：Java 工程师的 MCP 治理落地清单

> 掘金 / CSDN / 知乎同步稿 · 2026-11-10
> 关键词：MCP Server 治理、UiPath Gemini、Dynatrace CVE-2026-123811、金融智能体招标、Spring AI 2.0、Java Spring Boot

---

## 写在前面

11-09 我写过一篇《治理层 = 定价权》：公开报价里，不带治理的 MCP server 是 `$3.7K–15K`，带 audit/SSO/RBAC/多租户的生产级 MCP 是 `$80K–220K`，企业网关 `$250K` 起。**同一份工作量，治理层把它卖出 10 倍价格。**

11-10 又出现三件事，把这件事往上推了一层：

1. **UiPath 成为 Google Cloud Gemini for healthcare 首发合作伙伴**，通过 UiPath MCP Server 把 Gemini 接到企业自动化平台。官方新闻稿原话是 «**with the governance that regulated industries demand**» —— 治理不是加分项，是**允许接入的前提**。
2. **Dynatrace MCP Server 两个 CVE 同期收录**：`CVE-2026-123811`（7.5）`--http` 模式无鉴权、无 Host/Origin 防护，浏览器 DNS rebinding 直接调 `execute_dql` 读走日志和安全事件；`CVE-2026-123812`（3.7）某个写工具漏了 `requestHumanApproval`。而**同一个 server 里，`send_event` 因为有审批门，挡住了同一条攻击链**。
3. **零壹智库 10 月金融智能体招标全景**：23 条招标环比降 1/3，15 条中标合计 1652.8 万元创下半年新低，但国投证券把一个需求**拆成四单**分别定标，12 家中标方只有 2 家拿到两单；浙商证券把 **7 项法规写成强制测评依据清单**。

一句话：**治理层不只是「定价权」，它是「准入权」，也是「法律责任的分界线」。** 这篇不谈概念，给 Java / Spring 工程师一份能落到代码和标书里的清单。

---

## 一、准入权：买方已经把治理写进资质门槛

广发证券三份供应商征集（11-12 / 11-13 / **11-15** 17:30 截止，注意第三份此前被很多汇总写成 11-13，官方原文是 11-15）里，`13322` 把 MCP Server 工程能力直接写成资质：

> 「具备 **MCP Server 设计与开发适配能力（工具定义与描述、参数校验、鉴权与权限控制、调用限流、日志与可观测性）**」+「数据权限按客户经理归属严格隔离」+「服务行为自动留痕、**日志不可篡改**」+「技能可复用、可编排，MCP 工具调用稳定且权限可控」。

这不是技术偏好，这是**能不能报名的门槛**。同一周浙商证券把 `GB/T 45654-2025`、《人工智能安全治理框架 2.0》、《网络安全法》《数据安全法》《个人信息保护法》《生成式人工智能服务管理暂行办法》《证券期货业网络和信息安全管理办法》七项写进**强制测评依据清单**。

> 对 Java 工程师的含义：以后简历上写「会写 MCP demo」没用了，要能拿出**可验收的治理工件**：参数校验规则、权限模型、审计表结构、逐工具的验收方式。

---

## 二、法律责任分界线：HITL 不是最佳实践，是合同边界

最值钱的一条来自 11-10 的 T-Investments：这家券商把交易基础设施通过 MCP 向第三方 AI Agent 开放，外部算法可以分析行情、算税、下单（含止损），但**每笔操作强制用户显式确认**。报道直接点破：这是把「算法故障 / 误触发」的**法律责任从券商转移给投资者**的架构设计。

同一天 Upwork 官方 MCP 连接器的文档也是同一套逻辑：写操作 draft-and-confirm，binding offer、合同接受、付款**必须回 upwork.com 由人确认**。

再看 Dynatrace 的一正一反：`send_event` 有 `requestHumanApproval` → 攻击链断在这里；`create_dynatrace_notebook` 漏了这个门、还缺 `destructiveHint` 注解 → 间接 prompt injection 静默植入含攻击者 DQL 的 notebook，别人打开就执行。

> **结论**：写工具的审批门，是「谁为这次操作负责」的技术表达。做 MCP Server 时，审批门不该是可选项，也不该只挂在「危险」工具上。

---

## 三、七条 Java / Spring 落地清单（每条都能验收）

| # | 控制点 | 验收方式 | 对位本期事件 |
| --- | --- | --- | --- |
| 1 | 请求体上限 4 MiB + fail-closed | 超限返回 413、非法 JSON 不落业务 | 官方 Java SDK `CVE-2026-108395` |
| 2 | 每请求校验 issuer / audience，token 不转发下游 | 换 aud 的 token 必拒 | 官方 TS SDK `CVE-2026-111771` |
| 3 | Host / Origin allowlist + 解析后真实 IP 校验 | DNS rebinding 用例拒 | `CVE-2026-123811` |
| 4 | 写工具强制 HITL + 注册期校验 `destructiveHint` | 无审批不执行；缺注解不予注册 | `CVE-2026-123812` |
| 5 | 工具默认 disabled、按 caller 差异化授权 | 新注册工具默认不可调用 | Uber 800+ 网关生产实证 |
| 6 | 逐工具语句文法（别用前缀黑名单） | `VACUUM INTO` / CTE 越权必拒 | 官方参考实现 `CVE-2026-41901` |
| 7 | STDIO 启动命令 allowlist | `-c`、元字符、`--http`、`0.0.0.0` 全拒 | Anthropic STDIO「Won't Be Patched」 |

### 代码骨架 1：写工具审批门 + 注解校验（注册期）

```java
// 注册期：元数据声明本身就是准入条件，缺 destructiveHint 的写工具不予注册
public final class McpToolRegistrationGuard {

    public record ToolSpec(String name, boolean sideEffect,
                           boolean destructiveHint, boolean approvalRequired) {}

    public String validate(ToolSpec spec) {
        if (spec.sideEffect() && !spec.destructiveHint()) {
            return "REJECT: side-effect tool missing destructiveHint";
        }
        if (spec.sideEffect() && !spec.approvalRequired()) {
            return "REJECT: write tool must require human approval";
        }
        // 默认 disabled：注册成功也不等于可调用
        return "REGISTERED_DISABLED";
    }

    // 调用期：审批门不可旁路，且与审计同事务落库
    public String invoke(ToolSpec spec, String approvalId) {
        if (spec.sideEffect() && (approvalId == null || approvalId.isBlank())) {
            return "DENY: approval required";   // 对位 CVE-2026-123812
        }
        // audit.append(tool, caller, sanitizedArgs, verdict, traceId); // 只写不删
        return "ALLOW";
    }
}
```

### 代码骨架 2：STDIO 启动命令 allowlist（argv 数组，绝不经 shell）

```java
// 完整可运行版见 examples/stdio-command-allowlist/McpStdioCommandAllowlist.java
// 关键：command allowlist 是 Anthropic「Won't Be Patched」给出的唯一缓解，且默认不开启
public ProcessBuilder launch(String exe, List<String> argv, Map<String, String> env) {
    Verdict v = guard.screen(exe, argv, env);   // 绝对路径白名单 / 元字符 / 危险开关 / 环境净化
    if (!v.allowed()) {
        metrics.increment("mcp_security_stdio_rejected", "reason", v.reason());
        throw new SecurityException("stdio command rejected: " + v.reason());
    }
    List<String> command = new ArrayList<>();
    command.add(exe);
    command.addAll(argv);
    return new ProcessBuilder(command);          // argv 数组，不拼字符串、不经过 shell
}
```

本期这个示例已实测：`javac -encoding UTF-8` 编译通过，`main()` 自检 **14 个攻击用例 + 7 个合法对照 + 3 个边界断言全绿**，其中 **11 个是「只查可执行文件名的旧 allowlist」会放行的增量拦截**。特意加了对照组断言：7 个合法命令（含 git 官方安全 `-c` 三连）新旧结论必须一致 —— **只统计「拦住了多少」会选出误杀一片的方案**。

---

## 四、Spring AI 2.0 GA 了，迁移别踩这五个坑

11 月 dev.to 上 AWS Heroes 把示例升级到 **Spring AI 2.0 + Spring Boot 4.1 GA**，MCP 客户端有 5 个 breaking change：

| Spring AI 1.x | Spring AI 2.0 GA |
| --- | --- |
| `WebClientStreamableHttpTransport`（WebFlux） | **`HttpClientStreamableHttpTransport`**（`java.net.http.HttpRequest.Builder`） |
| 一个 MCP URL | **拆 `base.url` + `endpoint`（`/mcp`）两段** |
| `defaultOptions(ChatOptions)` | `defaultOptions(ChatOptions.Builder)` |
| `.tools(local)` + `.toolCallbacks(provider)` | **`.tools(localTools, callbacks)` 合并** |
| 不需要 | **显式定义 `ToolCallingManager` bean** |

另外 Spring Security 7.1 / Boot 4.1 新增 `InetAddressMatcher` / `InetAddressFilter` 做 SSRF 防护，官方已修「MCP 会话耗尽」高危漏洞。第三方《Spring AI vs LangChain4j 2026》横评的结论也对 Java 栈有利：Spring AI 2.0 把 MCP 当一等集成点（`@McpTool` / `@McpParam` 注解 + Micrometer/Actuator 内建可观测），LangChain4j 需要手工接线。

> 我们的框架一直坚持「**核心治理层基于官方 SDK、Spring AI Alibaba 只是可选兼容层、不进主 reactor**」—— 这次 GA 再次说明这个决定是对的：上游 breaking change 不会把治理层一起掀翻。详细迁移对照见 `docs/spring-ai-2.0-migration-notes-2026-11-10.md`。

---

## 五、钱在哪：拆单后的细分件

10 月中标数据最反直觉的一点：**金额最大的一单不是智能体本体，是它要接的数据底座（350 万）**。国投证券把一个需求拆成四单：桌面工作台 165 万（火山引擎）、平台升级+知识库 205 万（腾讯云）、「大师诊股」本体 18.02 万、诊股数据服务 20.14 万（今日投资）。

对没有金融案例、没有 500 万注册资金、没有本地团队的团队，**别去投整包**。可切的是：

- **安全测评 / 合规工件**（浙商证券 7 项强制清单、中信建投检测工具订阅）→ 交付「依据 → 控制点 → 代码 → 验收方式」映射表；
- **治理子模块**（上海银行单列大模型安全平台、民生银行 AI 邮件安全智能体）→ 模块增补，避开整包资质；
- **持续供数**（国投瑞银 ≤10 万/年、不多于 3 家中标）→ 低门槛长尾。

7 家 MCP 开发公司的公开报价也能当锚：`$3K/月` 嵌入式工程师、≤`$46/hr`、`$15K–$250K` 价格带、单系统 `6–8 周` / 多系统 `10–14 周`。原文一句话最精准：**「The code is the easy part.」** 决定 server 能不能活到生产的是工具设计、认证、逐工具权限和日志。

> ⚠️ 以上都是**公开中标金额与卖方销售页报价**，不是成交价，更不是我已拿到的订单。写进方案时务必标注口径。

---

## 六、给 Java 工程师的行动清单

1. 把「工具定义 / 参数校验 / 鉴权限权 / 调用限流 / 日志可观测」这五件套做成**可演示的验收表**，不是 PPT 上的架构图。
2. 写工具一律挂 HITL，注册期校验 `destructiveHint`；审计只写不删、参数脱敏、带 traceId。
3. 别用前缀黑名单做参数校验（官方参考实现刚被 `VACUUM INTO` 打穿），用**逐工具语句文法**。
4. STDIO 场景把 command allowlist 打开，并用 **argv 数组**启动；`--http` / `0.0.0.0` 一律拒。
5. 升级 Spring AI 2.0 前先跑一遍上面 5 个 breaking changes 的对照。
6. 关注 11-12 / 11-13 / 11-15 广发三份征集、11-20 山东重工集团级平台（统一安全管控）—— **预征集 ≠ 采购邀请**，资质不足就找本地集成商组联合体，别虚构案例。

---

## 参考（均为 11-08 ~ 11-10 实时检索）

- [UiPath × Google Cloud Gemini for Healthcare 官方新闻稿 11-10](https://www.googlecloudpresscorner.com/2026-11-10-UiPath-Accelerates-Enterprise-AI-Transformation-with-Google-Clouds-Gemini-for-Healthcare)
- [CVE-2026-123811](https://aithreatalert.com/cve/CVE-2026-123811) / [CVE-2026-123812](https://aithreatalert.com/cve/CVE-2026-123812)
- [10月金融智能体招标降三分之一 · 腾讯网 11-10](https://new.qq.com/rain/a/20261110A0DO1L00)
- [官方参考服务器七 CVE 复盘 · AL-ICE 11-08](https://al-ice.ai/posts/2026/11/mcp-reference-servers-desktop-commander-seven-cve-batch-november-2026)
- [Spring AI 2.0 MCP 迁移 Part 4](https://bizarro.dev.to/aws-heroes/building-ai-agents-with-spring-ai-and-amazon-bedrock-agentcore-part-4-provide-mcp-tools-for-2odf) / [Spring AI vs LangChain4j 2026](https://dev.to/jamilxt/spring-ai-vs-langchain4j-which-java-ai-framework-should-you-choose-in-2026-4mop)
- [Best MCP Development Companies 2026（7 家报价）](https://www.ayautomate.com/blog/best-mcp-development-companies)
- [T-Investments 交易 MCP 与强制用户确认](https://thevalue.engineering/news/t-investments-mcp-protocol-ai-agents.html)
- [腾讯云：MCP 认证与安全 — 授权是可选的，责任不是](https://cloud.tencent.com/developer/article/2779058)

---

*本文对应开源项目 [spring-ai-mcp-enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise) V1.68。文中所有示例均为 `examples/` 下的参考实现，纯 JDK 零依赖、不进 Maven reactor；框架无商业合同、无中标记录、无金融行业案例，请勿将技术映射表述为「已通过合规测评」。*
