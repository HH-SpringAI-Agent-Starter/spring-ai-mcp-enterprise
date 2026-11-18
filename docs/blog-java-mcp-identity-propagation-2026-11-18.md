# 你的 MCP 网关说「不给 AI 邮箱」，结果它照样发过去了：身份传播面的 6 道闸

> 掘金 / CSDN 发布稿 · 2026-11-18 · Spring AI MCP Enterprise V1.75
> 关键词：MCP 安全、MCP 网关、IBM CVE-2026-110278、身份传播、Spring AI、Java、Spring Boot、零信任

## 开篇：一个让所有「配置即安全」信仰崩塌的 CVE

11 月 12 日，IBM 发了 ContextForge MCP Gateway 的一个漏洞 `CVE-2026-110278`，CVSS 7.4。它的描述读起来像一句悖论：

> 管理员在配置里明确写了「这个上游 server 不许接收用户身份」（user id / email / group / team），
> 但当 Agent 真的去调用工具、读资源的时候，**这些配置被忽略了**——身份照样被发了过去。

更糟的是，网关用的身份签名是**静态 payload**：攻击者抓到一个令牌，就能重放它冒充原用户。官方修复版本 1.1.1，不能立即升级的唯一缓解是——**把身份传播整个关掉**，代价是那些依赖用户级授权的工作流直接失效。

如果你用 Spring AI / Spring Boot 搭过 MCP Server，这个场景一定不陌生：我们在 `application.yml` 里配了一堆 `allowed-*`、把注解标得漂漂亮亮，然后就**相信配置会保护我们**。这个 CVE 用最尖锐的方式提醒：

**声明不是边界。配置不是边界。只有每次调用时真正执行的那道门，才是边界。**

## 我们把边界推到了第七段

过去几个版本，我们沿着 MCP 调用链一段段把「看似安全其实不然」的东西剥掉：

| 段 | 曾经以为的边界 | 实际判词 |
| --- | --- | --- |
| 1 | 工具注解 `readOnlyHint` | 注解可以被谎报 |
| 2 | 注册中心的准入 | 注册中心自己能被提权 |
| 3 | `read_only` 声明 | 声明能被协议绕过 |
| 4 | 审批弹窗 / 提示词护栏 | 能被持久攻击推到 100% 失守 |
| 5 | 网关配置面 | 治理权可中立，授权不能外包 |
| 6 | 工具返回结果 | 只读查询也能当盲注 oracle |
| **7** | **per-server 身份转发配置** | **配置被忽略、令牌被重放** ← 本文 |

第七段管的是一个问题：**工具真正执行时，被调用方收到的到底是谁的身份？**

## 6 道闸：把「声明」变成「执行」

我们在 `examples/identity-propagation-guard/` 给了一个纯 JDK、零依赖的参考实现 `McpIdentityPropagationGuard`。核心就 6 道闸：

### CP1：声明 block 的字段，被请求就直接拒绝（fail-closed）

不要静默丢掉，要拦下来并留下信号——因为「任务请求了一个 server 声明不接收的字段」本身就是异常。

```java
for (String field : IDENTITY_FIELDS) {
    if (!required.contains(field)) continue;
    if (policy.declaredBlockedFields.contains(field)) {
        return block(BLOCKED_FIELD_FORWARDED,
            "server declared block but invocation requested: " + field);
    }
    // ...
}
```

这正是 110278 的反面：配置从「审计输入」升级为「强制拦截依据」，且拦截发生在出站调用点。

### CP2：字段最小化，只下发任务真正需要的

```java
if (!policy.allowedFields.contains(field)) {
    stripped.add(field);   // 记录剥离，不下发
    continue;
}
```

Impartner 11-17 上线 MCP 时的措辞是「sensitive fields are withheld from AI assistants」——买方已经把「字段最小化」写进产品定义了。

### CP3：动态签名 + 新鲜度 + nonce 防重放

对位「静态签名可重放」：

```java
if (Math.abs(now - ts) > FRESH_WINDOW_SECONDS) return block(STALE_SIGNATURE, ...);
if (!constantEquals(expected, sign(userId, tenant, ts, nonce))) return block(BAD_SIGNATURE, ...);
if (usedNonces.contains(nonce)) return block(REPLAYED_NONCE, ...);
usedNonces.add(nonce); // 仅全绿后消费
```

时间戳 5 分钟窗口（可配）、HMAC 完整性、**nonce 一次性消费**，三件套缺一不可。示例里用 `constantEquals` 规避时序侧信道；示例密钥硬编码仅为可运行，生产必须走 KMS 轮换。

### CP4：拒绝 header 覆盖

IBM 的另一个洞 `110279` 是客户端用 header 覆盖上游凭据。我们的做法是直接拒绝身份/凭据类 header：

```java
if (FORBIDDEN_HEADERS.contains(norm1(key))) {
    return block(HEADER_OVERRIDE, "client header overrides upstream identity: " + key);
}
```

身份只取已认证上下文，不取调用方「自称」。

### CP5：租户绑定

```java
if (!eq(id.tenantId, policy.tenantId)) return block(TENANT_MISMATCH, ...);
```

券商场景里的「数据按客户经理归属严格隔离」，落到技术层就是这一行。

### CP6：审计本身不能成为泄露源

email 打码、groups 只记数量，append-only：

```java
audit.add("ALLOW task=" + ... + " email=" + mask(id.email)
        + " groups=" + id.groups.size() + " stripped=" + stripped);
```

## 对照组：只写顺风测试会选错方案

守卫写了 15 条断言：**10 攻击 + 3 合法对照 + 2 边界**。但真正值钱的是那个「不得劣于旧基线」的对照：

```java
// legacyBaseline() 只信任 server 声明的 block 配置
if (legacy.allowed() && !newGuard.allowed()) legacyOnly++;
```

结果：**旧基线放行的 3 条攻击（静态签名 / header 覆盖 / 跨租户），新守卫全部拦下**；同时 3 条合法调用 0 误杀。

如果你只测「拦下了多少攻击」，很可能做出一个把所有调用都拦掉的「安全」系统——那是安全的，但没用。**对照组是硬要求。**

## 跑起来

```bash
javac -encoding UTF-8 McpIdentityPropagationGuard.java
java -Dfile.encoding=UTF-8 McpIdentityPropagationGuard
# self-test OK: 10 attack + 3 control + 2 boundary assertions
#   legacy-only blocks: 3 | false positives: 0
```

## 为什么这件事在现在值钱

- **买方已经自建完 MCP**（广发研究 MCP&Skills 上线智慧数平台，21 行业 / 9 万指标），钱从「帮你建」转到「帮你管」。
- **Kaseya 11-17 自己承认**：48% 的 MSP 说客户要 AI，只有 13% 真的靠它赚到钱——它把 first-party MCP server 定位成「治理层」而不是「连接层」。
- **同质化**：恒生研究院的判断很直接——你能提供的 Skill，隔壁也能；真正的壁垒是「业务本体地图」和可验收的治理能力。

对 Java + Spring 工程师来说，这是把「写个 MCP server」升级成「交付一套治理层」的机会——而治理层，才是这轮定价权真正涨的地方（同范围报价能差 19 倍）。

## 诚实边界

- 本框架不是 ContextForge，**不受** 110278 直接影响；引用它是作为设计依据。
- 参考实现在 `examples/`，**未接入主模块**；密钥硬编码、进程内 nonce 去重都是演示约束。
- 12 项身份面验收清单 + 买方必问 5 问 + 三档报价见 `docs/identity-propagation-boundary-2026-11-18.md`。

---

*项目地址：https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise*
