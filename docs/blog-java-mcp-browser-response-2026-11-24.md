# 你的 MCP Server 请求端守得再好，一个响应头就能把边界拆掉（Java 实测）

> 掘金 / CSDN 发布稿 · 2026-11-24 · Spring AI MCP Enterprise V1.78

## 开头：一个反常识的事实

过去十期 we 聊 MCP 安全，聊的都是**请求端**：工具注解、注册中心、只读声明、审批门、配置面、结果回传、身份传播、会话绑定、执行环境。大家默认：只要进来的请求校验够严，边界就守住了。

`CVE-2026-34237`（11-22，CVSS 6.1）把这个假设打碎了。

这是 **MCP Java SDK 官方洞**（`io.modelcontextprotocol.sdk:mcp-core`）：SSE 和 Streamable HTTP 两种传输都下发了 `Access-Control-Allow-Origin: *`。攻击只需要三步：

1. 让内网员工的浏览器打开攻击者页面；
2. 页面请求 `https://internal-mcp-server/sse`——浏览器已在内网边界内；
3. 通配响应头让页面读到 SSE 首帧里的 **session id**，随后带着它向同一端点发 POST。

**攻击者从头到尾没有到内网的可达路由，浏览器替他完成了网络位置。** 这就是本期判词的前半句：CORS 是跨域读取策略、不是认证。

## 两个补充证据

**Mendix 双 CVE（`112136` CVSS 8.8 / `112137` 5.4，11-23）**：Siemens Mendix Studio Pro 的 MCP Server 可被远程交互 + 浏览器 CSRF 触发任意工具执行，危及工程工作站及其构建的工业应用。官方缓解第一条是「只绑 loopback + Origin/Referer allowlist」——但 CSRF 请求 rides 合法浏览器会话，**loopback 挡不住**。判词第二句：loopback 是网络位置、不是授权。

**k8s flag 注入（`134817` CVSS 7.1，CWE-88）**：`mcp-server-kubernetes <4.2.0` 把 dash 开头的位置操作数 `--filename=/etc/passwd` 原样交给 kubectl 当 flag，读走宿主机文件。类型校验挡不住它——参数以数据之名行控制之实。判词第三句：session id 是传输句柄、不是身份；同理，参数是数据、不是控制。

## 三段 Java 判定骨架（纯 JDK 17，可编译）

### 1. 出站响应头闸（CP1 + CP2）

```java
// 通配来源一律拒绝；精确 allowlist；带凭据不得配通配
Verdict checkCors(Request r) {
    if (r.responseAcao == null || r.responseAcao.isEmpty()) return Verdict.allow(); // 同源默认
    if ("*".equals(r.responseAcao.trim()))
        return Verdict.deny(Code.CORS_WILDCARD, "wildcard ACAO on " + r.transport);
    if (r.responseAcaCreds)
        return Verdict.deny(Code.CORS_CREDENTIALS_WITH_WILDCARD, "credentials + wildcard");
    if (!CORS_ALLOWLIST.contains(r.responseAcao.trim()))
        return Verdict.deny(Code.CORS_ORIGIN_NOT_ALLOWED, r.responseAcao);
    return Verdict.allow();
}

// 响应体里的 session id 必须绑定签发 origin，句柄不对跨域可读来源暴露
Verdict checkSession(Request r) {
    if (r.exposedSessionId == null || r.exposedSessionId.isEmpty()) return Verdict.allow();
    if (r.sessionIssuedOrigin == null || r.sessionIssuedOrigin.isEmpty())
        return Verdict.deny(Code.SESSION_ORIGIN_UNBOUND, "session not bound");
    if (r.responseAcao != null && CORS_ALLOWLIST.contains(r.responseAcao.trim())
            && !r.sessionIssuedOrigin.equalsIgnoreCase(r.responseAcao.trim()))
        return Verdict.deny(Code.SESSION_ID_EXPOSED_CROSS_ORIGIN, "session cross-origin readable");
    return Verdict.allow();
}
```

关键点：SSE 与 Streamable HTTP 两条传输**分别判定**（官方 advisory 指出二者实现不同），且升级 SDK 之后仍要在实际 ingress 复查——反向代理可能把通配头重新加回来。

### 2. 监听地址闸 + 工具实参闸（CP3 + CP4）

```java
Verdict checkBind(Request r) {
    String addr = r.bindAddress.trim().toLowerCase(Locale.ROOT);
    if (LOOPBACK.contains(addr)) return Verdict.allow();
    if (DOC_IPS.contains(addr) || addr.isEmpty())
        return Verdict.deny(Code.BIND_PUBLIC_INTERFACE, "non-loopback: " + addr);
    InetAddress ia = InetAddress.getByName(addr);          // 解析失败也 fail-closed
    if (!ia.isLoopbackAddress())
        return Verdict.deny(Code.BIND_PUBLIC_INTERFACE, "resolves to " + ia.getHostAddress());
    return Verdict.allow();
}

Verdict checkArg(Request r) {
    for (String a : r.argv) {
        if (a != null && !a.isEmpty() && a.charAt(0) == '-')
            return Verdict.deny(Code.ARG_FLAG_LIKE, "dash operand: " + a); // --filename=/etc/passwd
    }
    return Verdict.allow();
}
```

### 3. CSRF 闸（CP5）—— 合法来源是 allowlist，不是单一硬编码 host

```java
Verdict checkCsrf(Request r) {
    if (!r.mutating) return Verdict.allow();
    if (r.requestOrigin == null || r.requestOrigin.isEmpty())
        return Verdict.deny(Code.CSRF_ORIGIN_MISSING, "mutating request without Origin");
    String origin = r.requestOrigin.trim();
    if (!CORS_ALLOWLIST.contains(origin))                   // 与 CORS 白名单同源同策略
        return Verdict.deny(Code.CSRF_ORIGIN_MISMATCH, "Origin not in allowlist");
    if (!CSRF_TOKEN.equals(r.csrfToken))
        return Verdict.deny(Code.CSRF_TOKEN_MISSING, "no CSRF token");
    if (r.requestReferer != null && !r.requestReferer.trim()
            .toLowerCase(Locale.ROOT).startsWith(origin.toLowerCase(Locale.ROOT) + "/"))
        return Verdict.deny(Code.CSRF_REFERER_MISMATCH, "Referer not same-origin as Origin");
    return Verdict.allow();
}
```

⚠️ 开发实测踩坑：同源判定一开始硬编码成单一 host，结果把合法的第二个 origin（admin）误杀了——**对照组断言立刻抓到**。同源策略必须是服务端声明的 allowlist，不是一个写死的字符串。

## 对照组方法论：为什么 26 断言比架构图值钱

完整样品在 `examples/browser-response-guard/`，`javac -encoding UTF-8` 编译 + `main` 自检：

```
self-test: OK
  attack   : 20/20 blocked      // 通配 CORS、session 泄漏、非 loopback、flag 注入、CSRF 五类
  control  : 4/4 allowed         // 精确 allowlist、同源 GET、admin 来源、合法位置参数 0 误杀
  boundary : 2/2 assertions      // 审计脱敏（session id 掩码 + origin query 删除）+ 空请求 fail-closed
  legacy-only blocks: 20         // 旧基线放行的 20 条新守卫全拦
  total assertions: 26
```

旧基线只看「有 session id + 有 CORS 头」——在它眼里 `Access-Control-Allow-Origin: *` 也算「有 CORS 头」，于是 20 条攻击全部放行。**只写顺风测试会选错方案**：本次开发中对照组抓到 3 个真问题（同源判定硬编码、审计断言不可达、Referer 夹具未对齐），全部修正后才全绿。

## 落地清单（9 条）

1. 升级官方 Java SDK 到 1.0.1 / 1.1.1 / 0.18.3 对应分支；
2. 升级后在 ingress / servlet container / service mesh / 应用端点**逐路径复查**响应头；
3. SSE 与 Streamable HTTP 分开探测，检查反向代理是否重加通配头；
4. ACAO 只允许精确 origin allowlist，禁止「通配 + 秘密路径」当授权；
5. session id 绑定签发 origin，句柄不对跨域来源暴露；
6. MCP Server 只绑 loopback，启动期 fail-closed 校验；
7. 改动类 POST 强制 Origin allowlist + CSRF token + Referer 同源；
8. 工具位置操作数 dash 前缀拒绝，参数只作数据；
9. append-only 脱敏审计，session id 与 query 凭据不落盘。

## 诚实边界

本框架不受上述第三方 CVE 直接影响，但**核心依赖官方 SDK**，`34237` 必须显式披露——升级只移除一处绕过，部署后复查才是闭环。参考守卫在 `examples/` 未接入主模块，策略硬编码、IP 字面量分类为演示约束。

_项目：github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise · Apache 2.0_
