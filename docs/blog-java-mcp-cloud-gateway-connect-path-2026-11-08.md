# 三大云同一周把 MCP 治理做成了标配，然后官方调试工具在"还没连上"时被 RCE 了

> 掘金 / CSDN 稿件 · 2026-11-08 · 关键词：MCP 网关、Azure AI Gateway、AgentCore Gateway、腾讯云 MCP 网关、CVE-2026-119025、MCP Inspector、Spring AI、Java 企业级治理
> 配套仓库：[HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise)（Apache-2.0，21 模块）

---

这一周 MCP 圈发生了两件方向完全相反的事，放在一起看特别有意思。

**第一件**：微软 Azure API Management 的 **AI Gateway** 进公开预览，AWS **Bedrock AgentCore Gateway** 一口气补齐全 MCP primitives、按身份动态列工具、elicitation HITL、RFC 8693 token exchange；腾讯云云原生智能网关 **v3.9.8**（产品动态 11-06 18:27 更新）让 MCP 网关支持 OAuth 2.0 授权码访问存量 HTTP「无需改造既有鉴权体系」；Celigo 11 月版把 Server / Gateway / Registry 三件套做成了产品。

**第二件**：11 月 5 日 Pillar Security 放出官方 **MCP Inspector** 的完整利用链 —— 你用它去审查一个还没信任的远程 MCP server，它却让那个 server 在你的 Windows 机器上执行了 PowerShell，**在任何工具被调用之前**。

一句话概括：**治理能力正在被平台层收编，而攻击面正在往平台层够不着的地方下沉。**

---

## 一、先接受一个事实：「我们有 MCP 治理」不再是卖点

微软合作方 Adaptiv 的这句话值得抄进方案：

> 传统 API 网关理解的是一次 **HTTP 事务**，AI 网关理解的是一次 **AI 交互**。传统网关不懂 token、prompt、completion、模型、上下文窗口和 AI 安全风险。

看看这一周平台层已经替你做了什么：

| 能力 | 谁做了 |
| --- | --- |
| 工具注册 / 目录 / schema 版本 | AgentCore 全 primitives + `outputSchema` + `readOnly` / `destructive` 注解；Celigo Registry v0.1 |
| 按调用者身份动态列工具 | AgentCore **dynamic listing**（请求时以用户身份回源）；Celigo **caller context** 从已认证请求解析身份，**不接受模型自称**，无身份时返回空而非无限制 |
| OAuth / 令牌交换 | 腾讯云 OAuth2 授权码；AgentCore RFC 8693 on-behalf-of |
| 限流 / 配额 / 成本 | Azure token 限流与配额；腾讯云**组织节点逐级配额与成本计量** |
| HITL 人工确认 | AgentCore **elicitation 三模式**（form / URL / URL-exception） |
| 可观测 | Azure prompt/completion 日志进 Monitor；Celigo step-level 执行日志 |

所以如果你的技术方案第一页还在写「我们提供工具注册、鉴权、限流、日志可观测」，选型会上大概率会被一句「腾讯云已经支持了」打回来。

**但这不是坏消息**：一年前你要先向甲方解释「为什么 MCP 需要治理」，现在这件事由三家云的产品路线图替你背书了。要换的只是锚点 —— 从「有没有治理」换成「**谁对这次调用签字、在哪朵云之外签字**」。

---

## 二、 CVE-2026-119025：护栏看不见的那一层

先看链，注意每一步都绕开了我们熟悉的防护：

```
1. 你把 MCP Inspector 指向一个还没信任的远程 MCP server（这正是它的用途）
2. server 返回 401 + OAuth discovery
   authorization_endpoint 是【由它自由选择】的字符串
3. Inspector 通过 npm `open` 自动打开该 URL
   （交互式终端 auto-open 默认开，CLI / TUI / Web 三处都有）
4. Windows / WSL 上 `open` 10.x 把 URL 塞进 PowerShell 双引号，
   base64 后 -EncodedCommand 执行，未做转义
5. PowerShell 在双引号里求值 $(...)
```

载荷长这样，是个**完全合法的 https URL**：

```
https://evil.test/$([IO.File]::WriteAllText('C:/Users/Public/pwned.txt','RCE'))
```

URL 序列化会编码空格和引号，但保留 `$ ( ) [ ] : , + &`，所以载荷不需要任何编码字符。

三个让人不舒服的细节：

1. **scheme 校验挡不住**。不少项目给这类洞加的防御就是「只允许 http/https」—— 这个 URL 就是 https。真正修好问题的是 `open` 11 的参数转义与单引号，以及"URL 根本不到 shell"的 argv 形状。
2. **模型完全没参与**。零工具调用、零审批提示、零权限判断 —— 你在 agent config 里配的所有 guardrail 都看不见这一层，因为注入发生在**连接路径**，比 agent 低一层。
3. **server 不必在配置时就是恶意的**。载荷在 discovery 响应里按请求到达，过期域名、typosquatting 包名、后来被攻陷的服务，效果完全一样。

> 公告 `GHSA-q978-78c7-phqw` CVSS 3.1 **8.8**，CVE CVSS 4.0 **7.7**，修在 Inspector **2.12.0**（导入 `server.json` 用 2.14.0）。Pillar 统计约 768,000 周 npm 下载，**>80% 的 `open` 包周下载仍解析到受影响版本**。另有 v1 分支的兄弟洞 `GHSA-4fc4-wcmh-4g86` **9.6**，可把远程 session 翻转成本地 stdio 执行且不限 OS。

### 更麻烦的是「没有 CVE 的修复」

同一周复盘里还有个更值得架构师警惕的案例：**opencode 1.18.33**（9 月 28 日）用一模一样的路径修掉了同类 PowerShell 注入，PR 标题却是 `refactor(core): share a browser opener across opencode and tui` —— **没发 CVE、没发公告**。结果：

- Dependabot 只对进入 GitHub Advisory DB 的公告告警 → 静默；
- agent CLI 常装在开发者笔记本上（curl / npm -g / Homebrew），根本不在仓库依赖图里 → SBOM 也看不见；
- Adversa AI 统计 10 起 Claude Code 相关攻击，**只有 2 起产生了 CVE**。

再叠上 LiteLLM `GHSA-7hp6-4w63-5g45`（`internal_user` 复用同一 salt 伪造 proxy-admin → 经 **MCP stdio** 主机 RCE，与 11-04 Wiz 蜜罐的 `CVE-2026-59822` 是同一组件连续两周被打），结论很直白：

> **把 agent CLI 当浏览器管（自动更新 + 版本下限 + 自己维护资产清单），不要当库管（等 CVE 再升级）。**

---

## 三、Java / Spring 项目今晚就能补的四段代码

仓库 `examples/` 下已放好纯 JDK 参考实现（`javac` 编译 + `main()` 自检全绿），这里给思路。

### 3.1 发现文档里的 URL：先查字符，再查 scheme

```java
// examples/connect-path-guard/McpConnectPathGuard.java
private static final String FORBIDDEN_CHARS = "$`|;<>()\\\"'\n\r\t";

public static Verdict checkAuthorizationEndpoint(String rawUrl, Set<String> allowedHosts) {
    if (rawUrl == null || rawUrl.length() > 2048) return Verdict.no("EMPTY/TOO_LONG");
    for (int i = 0; i < rawUrl.length(); i++) {
        char c = rawUrl.charAt(i);
        if (FORBIDDEN_CHARS.indexOf(c) >= 0 || Character.isISOControl(c))
            return Verdict.no("SHELL_METACHAR_AT_" + i);   // $(...) 在这里就死
    }
    if (rawUrl.contains("%24") || rawUrl.contains("%28")) return Verdict.no("ENCODED_METACHAR");

    URI uri = URI.create(rawUrl);
    if (!Set.of("http", "https").contains(uri.getScheme().toLowerCase())) return Verdict.no("BAD_SCHEME");
    if (uri.getRawUserInfo() != null) return Verdict.no("USERINFO");
    if (isPrivateOrMetadataLiteral(uri.getHost())) return Verdict.no("PRIVATE_HOST"); // 169.254.169.254 ...
    if (allowedHosts != null && !allowedHosts.contains(uri.getHost())) return Verdict.no("NOT_ALLOWLISTED");
    return Verdict.ok();
}
```

注意顺序：**字符级校验必须在 scheme 校验之前**，否则 `https://.../$(...)` 会被一个"合法 scheme"放行。另外 `& ? = [ ]` 是合法 URL 常见字符，不要一刀切禁掉（我们本来就不让 URL 到 shell）。

### 3.2 启动浏览器：给 argv，不给 shell 字符串

```java
public static List<String> browserArgs(String browserExecutable, String url) {
    // 可执行文件路径必须是运维配置的固定值，不接受服务端下发
    if (!browserExecutable.matches("[A-Za-z0-9_./\\\\: -]+")) throw new IllegalArgumentException();
    if (!checkAuthorizationEndpoint(url, null).allowed()) throw new IllegalArgumentException();
    return List.of(browserExecutable, "--", url);   // 单个 argv 元素，绝不拼 powershell -EncodedCommand
}
// new ProcessBuilder(browserArgs(exe, url)).start();
```

配套两条策略：**远程目标默认不自动打开**（`autoOpenAllowed(remote, operatorOptIn)`）；**客户端强制版本下限**（`enforceVersionFloor("inspector", current, "2.12.0")`），专门对付"没有 CVE 的修复"。

### 3.3 拒绝路径要能报警：把契约变成埋点

上周我们发过一份 Prometheus 告警规则（4 组 15 条），但当时诚实标了一句「`mcp-monitor` 尚未导出 `mcp_security_*`，需由过滤器补埋点」。这周补上了：

```java
// examples/governance-hardening/McpGatewayGuardFilter.java（V1.66）
if (req.getContentLengthLong() > MAX_BODY_BYTES) {
    metrics.bodyTooLarge(tenant);                 // mcp_security_body_too_large_total
    reject(resp, 413, req, "BODY_LIMIT", ...);    // reject 内统一 +rejections_total
    return;
}
if (!sameOrigin(req)) {
    metrics.hostMismatch(tenant);                 // CVE-2026-48710
    reject(resp, 403, req, "CROSS_ORIGIN", ...);
    return;
}
// 每个拒绝分支：短 bearer / 注册 command 字段 / 受众不匹配 / 待审批 / 凭据取用 ...
```

`McpSecurityMetrics` 是零依赖实现（`ConcurrentHashMap` + `AtomicLong`），`exportPrometheus()` 直接吐出 10 个指标，指标名和 label 与告警规则逐字对齐。**写错指标名会抛异常**，不会出现"埋了但规则永远查不到"的静默失败。

### 3.4 分层，别和云网关抢入口

```yaml
# 云网关管流量与成本，本层管责任认定
gateway:
  traffic: azure-apim-ai-gateway | tencent-cloud-3.9.8   # 限流 / 配额 / 组织成本
  responsibility: mcp-governance                          # 鉴权决策 / 工具准入 / 写操作审批
  audit: append-only + 不可篡改 + ROPA 七字段导出
  client-admission: examples/connect-path-guard           # 连接路径，云网关不覆盖
```

---

## 四、买方问「Azure / 腾讯云已经有了，为什么还要你」怎么答

别硬扛，先承认：

> 如果贵司全部 MCP 流量都在同一朵云、同一个 Agent 平台内，用云厂商网关是合理的，我们不建议重复建设。

然后给三条真实边界：

1. **托管服务必须把自己作为唯一控制平面**，不可能替甲方在另一个云、另一套 Agent 平台、另一套等保域里签字。Gartner 首个 AI Agent Management MQ 已逐家指出：各家最深的治理只覆盖自有 runtime，第三方运行时管不住 —— 而国内头部券商的征集公告明确要求用采购方指定的 Dify / hiagent 开发 Agent。
2. **云网关把 REST 转成 MCP 工具，但不替你重构十五年陈的 J2EE 权限模型**。工具级 ACL 必须复用主 API 同一套对象授权，否则就会出现 `CVE-2026-121458` 那种「MCP 工具跳过对象级授权」的低权读他人草稿。
3. **私有化 + 等保 / 信创 / 密评 + 日志不可篡改 + 可交付源码**，是托管控制台结构上给不了的；这恰好是国内招标的必选项。

完整能力对照表和 5 条问答话术在仓库 `docs/enterprise-cloud-gateway-comparison-2026-11-08.md`，可直接摘进标书。

---

## 五、写在最后

这一周最值得记住的不是又一个 CVE，而是两句话：

- **治理被标准化之后，值钱的不再是"有治理"，而是"谁签字、在哪签、能不能给甲方看源码和验收"。**
- **攻击面下沉到连接路径之后，工具级护栏再严也不等于安全 —— 你连工具列表都还没拿到。**

仓库是 Apache-2.0，21 个模块，今天新增的三份示例全部在 `examples/` 下、不进 Maven reactor、零外部依赖，`javac` + `java` 就能跑自检。觉得有用点个 Star，也欢迎提 Issue 拍砖。

---

### 参考

- Microsoft AI Gateway 公开预览：https://adaptiv.nz/?p=996227
- Amazon Bedrock AgentCore Gateway：https://dev.to/om_shree_0709/amazon-bedrock-agentcore-gateway-just-became-the-enterprise-mcp-infrastructure-layer-heres-what-m4o
- 腾讯云云原生智能网关 v3.9.8 产品动态（2026-11-06）：https://cloud.tencent.cn/document/product/1826/134899
- Celigo November 2026 Release：https://www.celigo.com/builders-hub/celigo-november-2026-release
- CVE-2026-119025 完整利用链：https://dev.to/piekwerk/mcp-inspectors-oauth-flow-could-run-powershell-on-windows-inside-cve-2026-119025-and-why-npm-1hei
- 一周五洞供应链复盘：https://hangingcontext.com/stories/2026-11-06/ai-supply-chain-flaws-week
- opencode 无 CVE 修复：https://deniz.in/opencode-fixed-a-no-consent-mcp-oauth-rce-in-1-18-33-and-no-cve-was-ever-issued
- 完整情报：[`docs/earnings-report-2026-11-08.md`](earnings-report-2026-11-08.md)
