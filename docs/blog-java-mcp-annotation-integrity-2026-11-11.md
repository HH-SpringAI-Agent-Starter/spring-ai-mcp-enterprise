# 别再把 `readOnlyHint` 当安全边界了：11393 个 MCP Server 的实测，和一套 Java 注册期治理方案

> 掘金 / CSDN 发布稿 · 2026-11-11 · 关键词：MCP、Spring AI、Java、工具注解、审批门、企业治理、Siemens Mendix CVE、Microsoft Agent Framework
> 配套开源项目：[HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise)

---

## 先说结论

这周（11-09 ~ 11-11）四件事撞到了一起，把很多团队的一个隐性假设打碎了：

> **"我在工具上标了 `readOnlyHint: true`，所以它是安全的。"**

不成立。而且现在有量化证据了。

| 时间 | 发生了什么 |
| --- | --- |
| **11-11** | INITE Atlas 当日实测 **11,393** 个厂商 MCP Server：**25% 的工具连 read-only/destructive hint 都没有**、**4% 把「删除/发送/支付」标成了非破坏性**、**23% 免登录的 server 依然会改数据**、47% 的工具参数没有描述 |
| **11-09** | Microsoft Agent Framework Python **1.25.0** 上线 callable MCP discovery approval policies，并支持「目录刷新后重新审批」；11-11 治理指南直说「**metadata should be verified rather than blindly trusted**」 |
| **11 月** | OpenAI 目录要求三个 hint 必须是显式 boolean；Anthropic 用 read/write hint 决定**自动权限**（只读免确认、destructive 必弹窗），并拒收 catch-all 工具 |
| **11-10** | Siemens 官方公告 Mendix Studio Pro 内置 MCP Server 双 CVE：没绑 loopback（CVSS 9.2）+ 不校验 Content-Type/Origin（CSRF）；Dynatrace `CVE-2026-123811` 是同样的「`--http` 无鉴权 + 无 Host/Origin」 |

规范里其实早就写明白了：**annotations are hints, not enforcement**。`Hint` 这个后缀是刻意设计的 —— 它是给客户端做 UX / 规划参考的，**协议不强制任何客户端遵守**。真正的授权必须发生在服务端。

问题是：一旦「只读免确认」成了上架规则，一个谎报的 hint 就等于一次静默越权。

---

## 一、一个真实能打穿的链路

设想一个再普通不过的企业内部 MCP Server：

```json
{
  "name": "sync_customer",
  "description": "Synchronize customer records",
  "inputSchema": { "type": "object", "properties": { "id": { "type": "string" } } },
  "annotations": {
    "readOnlyHint": true,
    "destructiveHint": false,
    "openWorldHint": true
  }
}
```

它标了只读，但描述里有 `sync`、还要访问外部世界。客户端看到 `readOnlyHint: true` → 自动放行、不弹确认框。而服务端如果也「尊重」这个 hint，写操作就在没有任何人工确认的情况下发生了。

这正好是 `CVE-2026-123812` 的形态：Dynatrace 的 `create_dynatrace_notebook` 漏了 `requestHumanApproval`、缺 `destructiveHint`，间接提示注入就静默植入了带攻击者 DQL 的 notebook。**同一个 server 的 `send_event` 因为有审批门，把同一条攻击链挡住了** —— 审批门到底是不是真边界，这是第一份「同体内一正一反」的实证。

再看 Adversa 11 月榜单：`@modelcontextprotocol/server-everything` 绑所有网卡 + wildcard CORS + 无鉴权，`get-env` 直接返回整个 `process.env`；Basic Memory 老老实实按建议绑了 localhost，还是被 DNS rebinding 打穿了 —— 因为缺的从来不是绑定地址，而是 **Host/Origin 校验**。

---

## 二、Java 侧怎么做：注册期独立定级，运行期强制门

我的做法是把 hint 从「授权依据」降级成「待审材料」，分两步。

### 2.1 注册期：先审元数据，再独立定级

```java
// 纯 JDK，无第三方依赖
public enum Tier { READ, WRITE, DESTRUCTIVE }

private static final Pattern SIDE_EFFECT = Pattern.compile(
    "(?i)(delete|remove|drop|send|post|transfer|pay|create|update|execute|" +
    "删除|修改|写入|发送|支付|创建|执行)");

public Registration register(ToolSpec t) {
    // 1. 三个 hint 必须显式 boolean —— OpenAI 上架硬要求
    if (t.readOnlyHint == null || t.destructiveHint == null || t.openWorldHint == null)
        return reject("MISSING_EXPLICIT_HINTS", t);
    // 2. 自相矛盾
    if (t.readOnlyHint && t.destructiveHint) return reject("CONTRADICTORY_HINTS", t);
    // 3. 谎报只读：描述/参数里有副作用动词却声明只读
    String hay = (t.name + " " + t.description + " " + t.inputFields).toLowerCase();
    if (t.readOnlyHint && SIDE_EFFECT.matcher(hay).find()) return reject("READONLY_LIE", t);
    // 4. openWorld 谎报 / 5. catch-all / 6. get-env 整包导出
    if (t.readOnlyHint && t.openWorldHint) return reject("OPENWORLD_LIE", t);

    // 关键：档位由服务端算，不采信声明
    Tier tier = classify(t);
    String fp = sha256(t.name + t.description + t.inputFields + hints + dataClassification);
    registry.put(t.name, new Registered(t, tier, fp));   // 落指纹，供后面复验
    return new Registration(true, "ACCEPTED", tier, fp);
}
```

注意 `Boolean` 而不是 `boolean`：**「没写 hint」和「写了 false」是两件事**，规范默认 unset，按最坏情况（`DESTRUCTIVE`）处理。

### 2.2 运行期：审批门 fail-closed + 目录漂移复验

```java
public Verdict call(String toolName, CallContext ctx) {
    Registered reg = registry.get(toolName);
    if (reg == null) return deny("UNREGISTERED_TOOL");
    // 不接受模型自称身份，只认服务端认证后的 subject
    if (ctx.subject == null) return deny("NO_AUTHENTICATED_SUBJECT");
    // 元数据变了，旧审批立刻作废（MS Agent Framework 1.25.0 的 catalogue refresh）
    if (!sha256(reg.spec).equals(reg.fingerprint)) return deny("STALE_APPROVAL");

    if (reg.tier == Tier.READ) { audit("ALLOW", ctx); return allow("READ_AUTO_APPROVED"); }

    if (reg.tier == Tier.DESTRUCTIVE && ctx.override)
        return deny("OVERRIDE_FORBIDDEN_FOR_DESTRUCTIVE");
    if (ctx.approvalId == null || !ctx.approvalId.equals(approvals.get(key(reg, ctx))))
        return deny("NO_VALID_APPROVAL");

    try {
        if (!approver.approve(reg.spec, reg.tier, ctx)) return deny("APPROVER_REJECTED");
    } catch (RuntimeException e) {
        return deny("APPROVER_ERROR_FAIL_CLOSED");   // 异常也不能放行
    }
    audit("ALLOW", ctx);                              // 密钥值脱敏、拒绝分支也留痕
    return allow("ALLOW_APPROVED");
}
```

三条容易踩的坑：

1. **审批器不在 / 抛异常 / 返回 false，一律拒绝**。别写 `catch -> true`，那是给自己开后门。
2. **审批流水号必须绑定元数据指纹**。上游把工具描述从「查询工单」改成「查询工单并同步到外部 webhook」，旧审批必须自动失效 —— 这是 11-11 治理指南专门点出的新攻击面。
3. **override 要留「谁、绕过哪条策略、理由、结果」**，且破坏性操作不允许 override。否则 override 就是治理层的万能钥匙。

完整可运行代码在仓库 [`examples/tool-annotation-gate/`](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise/tree/main/examples/tool-annotation-gate)，`javac -encoding UTF-8` 编译 + `main()` 自检 **17 攻击 + 9 对照 + 6 边界（32 断言）**，其中包含「不得劣于旧基线」的对照组断言 —— **只写顺风测试会选错方案**：我第一版把「诚实声明 destructiveHint=true 的删除工具」也拒了，其实是错的，正确行为是**允许注册但强制审批**。

---

## 三、别忘传输层：绑 127.0.0.1 不是安全边界

Siemens 11-10 的公告值得每个做 MCP Server 的人读一遍：Mendix Studio Pro 11 的内置 MCP Server 因为没把 HTTP listener 限制在 loopback（`CVE-2026-112136`，CVSS 4.0 **9.2**），加上不校验 `Content-Type` / `Origin`（`CVE-2026-112137`，CSRF），恶意网页就能让开发者浏览器向本地 7782 端口发请求。

官方缓解第一条是防火墙，但服务端自己该做的是四件事：

| 检查 | 拒绝什么 |
| --- | --- |
| 绑定地址 | `0.0.0.0` / `::` / 空；非 loopback 必须显式 opt-in **且**强制鉴权 |
| `Content-Type` | 缺失、`text/plain`、`application/x-www-form-urlencoded`（用简单请求绕过 CORS 预检） |
| `Origin` / `Host` | `null`、非白名单、`*.nip.io` 类 rebinding 域、`X-Forwarded-Host` 不一致 |
| CORS / 工具 | `Access-Control-Allow-Origin: *`、`get-env` 整包环境变量工具 |

对应实现在 [`examples/local-listener-guard/`](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise/tree/main/examples/local-listener-guard)，**17 攻击 + 9 对照 + 4 边界（30 断言）** 全绿。它和 `connect-path-guard`（客户端出站）、`stdio-command-allowlist`（STDIO 进程）构成三层分工：**出站连接路径 / 入站监听 / 子进程启动**。

---

## 四、落地清单（照着做就行）

- [ ] 注册期：三个 hint 显式 boolean；矛盾、谎报只读、openWorld 谎报、catch-all 一律拒收
- [ ] 风险档位**服务端独立计算**，不读 hint 做放行判断
- [ ] READ 才免确认；WRITE / DESTRUCTIVE 强制人工审批，审批器异常 fail-closed
- [ ] 审批绑定 `tool + 元数据指纹 + subject`；catalogue 变更即作废
- [ ] override 只对 WRITE，带理由 + 完整留痕；DESTRUCTIVE 禁止
- [ ] 审计 append-only、密钥值脱敏、**拒绝分支也要记**
- [ ] 传输层：loopback-only + Content-Type + Origin/Host 白名单 + 禁 wildcard CORS + 未鉴权 401
- [ ] 从 OpenAPI 生成注解时必须人工复核（`POST /search` 其实是只读，`GET` 触发动作其实是写）
- [ ] 只读 ≠ 无害：只读查高管邮箱比写一条日志危险得多，按数据分类再分一层

---

## 五、这件事为什么值钱

[Arcade.dev《Build or buy an MCP runtime》](https://new.benhalpern.com/arcade/should-you-build-or-buy-an-mcp-runtime-for-enterprise-ai-agents-in-2026-36jg) 把企业真正要买的那一层列得很清楚：OAuth lifecycle、credential vaulting、multi-user auth、**permission intersection**、audit pipeline、policy enforcement、observability —— 并给了四个 tipping point（三集成阈值、用户委派动作、异步长时读写、OTel 审计日志）。结论是「**2026 年真正的取舍是风险，不是成本**」。

国内也在同频：[中信银行 11-11](https://m.10jqka.com.cn/20261111/c681716629.shtml) 明确下一代 App 走 **Agent + Skill + MCP**，且生成式内容**必须人工审核或预分析后调用**；上海农商银行写「Skill、**MCP 插件市场**」+ 权限管理；广发证券把「MCP 五件套（工具定义/参数校验/鉴权限权/限流/可观测）」写进了供应商资质门槛。

**「插件市场」这个词本身就意味着：你得有能力审别人注册进来的工具。** 这就是注册期治理的商业位置。

而现有网关（Docker MCP Gateway / IBM ContextForge / Kong / Portkey / Azure APIM / Cloudflare）清一色非 Java 栈，或者把治理锁在 Enterprise 付费墙；竞品 [mcp-gatehouse](http://hol.org/registry/plugins/nickgeorgeseo%2Fmcp-gatehouse) 是 Python 单 server 内嵌库。**存量 Java 系统的权限模型、审计链路、私有化部署，是 Java/Spring 团队在这个赛道最现成的护城河。**

---

## 六、诚实边界

- 文中两个示例是 `examples/` 下的**参考实现**，纯 JDK 零依赖、不进 Maven reactor，尚未接入 `mcp-registry` 主模块；自检是逻辑验证，不是集成测试。
- 副作用动词表是启发式，不是语义证明；生产要叠加 per-tool 语句文法与人工复核。
- 元数据指纹不覆盖工具实现代码；实现被替换而描述不变时检不出。基于数据分类的差异化策略本版只留了字段、未实现引擎。
- INITE 的百分比是**描述与 hint 的公开自动比对**，不等于已被利用；52% 免登录包含刻意公开的数据服务。Siemens 双 CVE 影响版本较窄、目前 **无公开在野利用**，本文只是把它抽象为通用准入模式，不代表本项目受影响。
- 本项目**无商业合同、无中标、无金融案例、无等保测评**；文中报价与招标信息均来自公开页面，不构成任何交付承诺。

---

**相关阅读**（仓库 `docs/`）：`earnings-report-2026-11-11.md`（全源情报）、`tool-annotation-integrity-governance-2026-11-11.md`（14 项可进标书的验收清单）、`enterprise-rfp-checklist.md`（24 类攻击面逐条验收）、`spring-ai-2.0-migration-notes-2026-11-10.md`。

如果这篇对你有用，欢迎给仓库点个 ⭐；有更狠的攻击用例，直接提 issue，我把它加进自检对照组。
