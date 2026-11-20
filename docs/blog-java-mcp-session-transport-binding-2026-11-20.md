# 你的 MCP 网关有 sessionId，但那不是安全边界：从 Supergateway 跨站劫持到 k8s-mcp 无授权派发

> 掘金 / CSDN 投稿稿 · 2026-11-20 · Spring AI MCP Enterprise V1.76
> 关键词：MCP Server 安全 / sessionId 绑定 / Origin 校验 / BadHost / OAuth issuer / Java Spring 治理

## 开篇：三行代码让整条八段式防线归零

过去七期我们讲了一条完整的收口链：**注解 → 注册中心 → 只读声明 → 决策层 → 配置面 → 结果面 → 身份传播面**，一句「XX 也不是安全边界」讲了七遍。这周的三批 CVE 把链条的最后一段补上了——**入口本身**。

- `CVE-2026-132121`：恶意网页开一个跨站 WebSocket 连到你绑在 localhost 的 Supergateway，就能驱动它桥接的 MCP 工具，PoC 12/12 次外传本地文件；
- `CVE-2026-130007`：11-19 NVD 披露的 k8s-mcp-server，`POST /message` 拿着一个 sessionId 就直接派发 `tools/call`，**没有 authorization、没有 Origin 校验**，而且 read-only 默认关闭；
- `CVE-2026-48710`（BadHost）：HTTP Host 头塞个问号，鉴权中间件读到的 path 从 `/admin` 变成 `/`，静默放行。

三件事的共同点：**它们都不攻击你的业务授权逻辑，而是绕到入口之前，让「这是谁、从哪来」这件事本身就是假的。** 你的 RBAC 再正确，一个个冒用的会话、跨站的 Origin、注入的 Host 走进来，判定全落在错误的主体上。

## 一段话结论

> sessionId 不是安全边界，Host / Origin 头不是装饰，OAuth 凭据必须绑定 issuer。
> 必须在**每次调用点**校验会话主体绑定、Origin 白名单与会话一致、Host 的 RFC 合法性、凭据的 issuer 绑定，并且默认只读、fail-closed。

## 四个真实洞，逐条拆

### 1. sessionId 只是「关联值」，不是凭证

`CVE-2026-130007` 的 NVD 描述里有句话值得抄进设计文档：**sessionId is only a correlation value**。它用来把 `/sse` 和 `/message` 两次请求对上号，仅此而已。如果 `/message` 不校验这个 ID 属于谁、调用者是不是那个主体，那知道 ID 就等于拿到了服务端权限——而 k8s-mcp-server 的写工具是以**服务端集群权限**跑的。

兄弟洞 `CVE-2026-131857` 更直接：未认证攻击者用 prototype-inherited 的 sessionId 值就能把网关打崩。这是 JavaScript 原型链继承撞上「对象里有这个 key 就当有效会话」的典型。

**工程对策**：会话必须服务端 HMAC 签发、绑定 principal + Origin、带新鲜度；`__proto__` / `constructor` / `prototype` 形态的 ID 一律拒绝。

### 2. localhost 绑定挡不住恶意网页的跨站 WebSocket

很多人觉得「网关只绑 127.0.0.1 就安全」。`CVE-2026-132121` 证明了这是错觉：**浏览器发出的跨站 WebSocket 就是从受害者本机发出的**，localhost 一视同仁地接受。MCP 规范为了 OAuth discovery 还强制了免鉴权端点，等于给这条路铺了红毯。

**工程对策**：WebSocket 升级和 SSE 连接强制要求 Origin 存在、在白名单内、且与会话签发时绑定的 Origin 一致。`Origin: null` 和缺失都拒绝。

### 3. Host 头的解析二义性

Starlette 把 `Host` 和路径拼起来再重新解析。`Host: foo?` 一注入，URL 变成 `http://foo?/admin`，重解析后 `/admin` 成了 query，鉴权中间件读 `request.url.path` 得到 `/`，于是 200 OK。CVSS 只给了 6.5，被安全圈批「严重低估下游影响」。

**工程对策**：Host 按 RFC 9112/3986 逐字符校验——拒绝 `/ ? # @ \`、空白和控制字符；鉴权判定绝不依赖重解析后的 path；IPv6 字面量 `[::1]:8080` 要能正常放行，别为了拦截误杀。

### 4. 升级了 SDK，凭据还是发给了攻击者

`CVE-2026-115563` 是那种「补丁装了等于没装」的洞：mcp Python SDK 升到 1.30.0 / 2.2.0 修好了 issuer 校验，但如果你用 ClientCredentials 或 PrivateKeyJWT，**不显式传 `issuer=` 就仍会跟随 server 广告的授权服务器**——client_secret、授权码、PKCE verifier 一起送出去。官方甚至把它设成了 deprecation warning（Python 默认隐藏），3.0 才强制。

**工程对策**：客户端凭据显式绑定预期 issuer，每条 discovery path 都校验，未绑定或不匹配 fail-closed。别信「升级就好了」。

## Java 落地：六道闸的判定骨架

下面是 `McpSessionTransportBindingGuard` 的核心结构（纯 JDK 17 零依赖，完整版在仓库 `examples/session-transport-binding-guard/`）。判定顺序 CP5a → CP1 → CP2 → CP3 → CP4 → CP5，全程 CP6 审计：

```java
private Verdict decide(Request r) {
    // CP5a 未认证派发：第一道门
    if (!r.handshake && r.principal == null) {
        return verdict(Code.UNBOUND_LISTENER, "unauthenticated dispatch");
    }
    // CP1 会话绑定
    if (isPrototypeInherited(r.sessionId)) return verdict(Code.INHERITED_SESSION, "...");
    SessionRecord rec = sessions.get(r.sessionId);
    if (rec == null) return verdict(Code.FORGED_SESSION, "signed-shape session not registered");
    if (!constantEquals(sign(rec), rec.signature)) return verdict(Code.FORGED_SESSION, "bad session signature");
    if (!r.principal.equals(rec.principal)) return verdict(Code.SESSION_PRINCIPAL_MISMATCH, "...");

    // CP2 Origin：握手强制 + 白名单 + 与会话一致
    if (r.handshake && isBlank(r.origin)) return verdict(Code.MISSING_ORIGIN, "...");
    String co = canonOrigin(r.origin);
    if ("null".equalsIgnoreCase(co)) return verdict(Code.NULL_ORIGIN, "...");
    if (!allowedOrigins.contains(co)) return verdict(Code.ORIGIN_NOT_ALLOWED, "...");
    if (!co.equals(rec.boundOrigin)) return verdict(Code.ORIGIN_SESSION_MISMATCH, "...");

    // CP3 Host RFC 合法性（BadHost）
    if (!validHost(r.host)) return verdict(Code.BAD_HOST, "illegal Host header");

    // CP4 issuer 绑定
    if (r.hasCredential && !expectedIssuer.equals(r.oauthIssuer)) {
        return verdict(Code.ISSUER_UNBOUND_OR_MISMATCH, "...");
    }
    // CP5 默认只读
    if (r.writeTool && rec.readOnlyOnly) return verdict(Code.WRITE_NOT_PERMITTED, "...");
    return new Verdict(true, Code.OK, "allowed");
}
```

Host 校验的关键在「逐字符拒绝」而不是「匹配前缀」：

```java
private static boolean validHost(String host) {
    if (host == null || host.isBlank() || host.length() > 253) return false;
    for (int i = 0; i < host.length(); i++) {
        char c = host.charAt(i);
        if (c == '/' || c == '?' || c == '#' || c == '@' || c == '\\') return false;
        if (Character.isWhitespace(c) || Character.isISOControl(c)) return false;
    }
    // IPv6 [::1]:8080 允许，不能为了拦截误杀
    if (host.startsWith("[")) { /* 校验 ] 与端口 */ }
    return host.chars().allMatch(c -> Character.isLetterOrDigit(c) || c=='.'||c=='-'||c==':');
}
```

## 方法论：为什么对照组比拦截数更重要

讲一个踩坑。首版自检里 CP5「未认证派发」用例传了 `principal=null`，结果被 CP1 的「空 sessionId」先拦了，`UNBOUND_LISTENER` 成了永远不可达的分支——**测试全绿，但那条防线根本没被验证**。这正是「只写顺风测试会选错方案」的又一个例子。

我们的自检因此强制要求三样东西：

1. **攻击组**：20 个攻击用例，每个断言具体的拒绝码（不是「被拒就行」）；
2. **对照组**：4 个合法调用必须 0 误杀，包括 IPv6 Host、WS 升级、SSE 连接、带正确 issuer 的凭据；
3. **回归组**：一个故意放水的 `legacyBaseline()`（只看 sessionId 非空 + Host 非空 + 有 Origin 头），6 个它放行的攻击，新守卫必须全拦——**不得劣于旧基线**。

实测输出：

```
self-test OK: 20 attack + 4 control + 2 boundary assertions
  legacy-only blocks (new guard catches, baseline misses): 6
  false positives on legal calls: 0
```

## 给 Java / Spring 工程师的行动清单

1. 盘点你所有 MCP 端点的**会话签发主体**：sessionId 是服务端签的还是客户端传的？
2. 所有 WS / SSE 入口加上 Origin 白名单 + 会话一致性校验；
3. Host / URL 校验改为逐字符 RFC 校验，别用 `startsWith("http")`；
4. OAuth 客户端凭据显式绑定 issuer，别只升级 SDK；
5. 默认只读、默认拒绝，写工具和写权限显式开通；
6. 审计 append-only + 会话 ID 打码，别让审计日志成为新的泄露源。

## 诚实边界

- 本框架不受上述 Supergateway / k8s-mcp-server / Starlette / Python SDK CVE 直接影响，引用为设计依据；
- 参考守卫仍在 `examples/` 层，未接入 21 个主模块；示例密钥硬编码、会话表进程内，生产须换 KMS + 共享存储；
- Host / Origin 判定是字面量校验，完整 DNS 解析 / SNI 校验是下一步。

项目地址：`github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise` · V1.76 · Apache 2.0。
