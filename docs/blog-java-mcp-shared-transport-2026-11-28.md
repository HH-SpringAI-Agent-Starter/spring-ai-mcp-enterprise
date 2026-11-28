# 一天 8 个 MCP CVE 之后：共享传输不是隔离边界（Java 执行门 + 对照组方法论）

> 面向掘金 / CSDN 稿件。关键词：MCP 安全、CVE-2026-111770、跨客户端串线、Crypto APIs 138026、Azure AI Gateway、Java Spring AI 企业化。

## 开篇：一个从 0 开始的请求号，把两个客户端的消息串了

2026-11-05 入库、11-22 阿里云 AVD 再分析的 **CVE-2026-111770**，是官方 MCP TypeScript SDK 自己的洞（`typescript-sdk` 客户端 2.0.0–2.3.0，CVSS 7.7，CWE-362 竞态）：当应用把**同一个无状态 HTTP transport 或同一个 Server 实例**共享给多个客户端时，SDK 只按 JSON-RPC 请求 id 把进行中的请求写进一张共享映射表；而 SDK 客户端的请求 **id 从 0 开始编号**——两个客户端第一次请求都是 0，碰撞是必然的，不是一个需要精心构造的攻击。结果：一个客户端的响应和通知被投递给另一个客户端。

官方修复在 2.3.0：无 `sessionIdGenerator` 的 transport 拒绝第二次 `handleRequest`，`connect()` 对已连接实例抛 `ALREADY_CONNECTED`。注意官方明确写出一句很少见于 CVE 公告的话：**升级会导致复用实例失败，需要改应用代码**。stdio 服务器和「工厂为每次调用新建实例」不受影响。

同一周，2026-11-27 一天之内，VulnCheck 由同一研究者发布 **8 个 MCP CVE**：Crypto APIs 的 14 个 npm 包在 HTTP 模式绑定 `0.0.0.0` 且 `/mcp` 无鉴权，任何能连上端口的人都能拿运营者的 API key 调全部工具——花额度、读账户、改 webhook；Jupyter MCP Server（77318/77359）给 `/mcp` 加了 token，却把 `/api/connect`、`/api/stop` 这类**状态变更管理路由**留在无鉴权状态。

三件事拼起来是同一句话：

> **传输实例、请求 id、scope 声明、绑定地址，都是路由事实，不是「这条消息属于谁、这笔账该谁付」的证明。**

我们已经写过十二段「XX 也不是安全边界」：注解、注册中心、只读声明、决策层、配置面、结果面、身份传播、会话/传输绑定、执行环境、出站响应、日志遥测、持久化记忆、审批链。这是第十四段——**共享传输 / 跨客户端隔离面**。

## 判词

```
一个 transport 实例只能属于一个 principal；
一条 HTTP 消息必须携带会话归属，不能只靠 JSON-RPC id；
管理路由必须另设 admin token，不能复用数据面的 token；
非 loopback 绑定没有 token 一律拒绝；
一笔凭证只能由它的 owner 消费。
```

用一段 Java 把它写成执行门（纯 JDK 17、零依赖，不进 Maven reactor）。

## 一、判定骨架（Java 17）

```java
public enum TransportMode { STDIO, HTTP_STATEFUL, HTTP_STATELESS }
public enum Effect { READ, WRITE, MANAGE }

public static Verdict check(Request r) {
    if (r == null || r.tool == null || r.principal == null || r.transportId == null)
        return deny("CP0", "incomplete request - fail closed");

    // CP4 网络绑定：非 loopback 无 token 直接拒绝
    if (!LOOPBACK.contains(norm(r.bindAddress))
            && (r.authToken == null || r.authToken.isBlank()))
        return deny("CP4", "non-loopback bind without auth token");

    // CP3 管理路由：独立 admin token + 真实 effect
    if (MGMT_ROUTES.contains(norm(r.route))) {
        if (r.effect != Effect.MANAGE)
            return deny("CP3", "management route declared as non-manage");
        if (r.adminToken == null || r.adminToken.isBlank())
            return deny("CP3", "management route without admin token");
    }

    // CP5 凭证归属：调用方只能花自己的 key
    if (r.credentialRef != null && !r.credentialRef.isBlank()
            && !canon(r.credentialOwner).equals(canon(r.principal)))
        return deny("CP5", "operator key spent by another caller");

    // CP1 实例独占：同一 transport 不得跨 principal 复用
    if (r.mode != TransportMode.STDIO) {          // stdio 单客户端豁免
        String owner = ATTACHED.get(r.transportId);
        if (owner != null && !owner.equals(canon(r.principal)))
            return deny("CP1", "transport instance already attached by another principal");
        ATTACHED.putIfAbsent(r.transportId, canon(r.principal));
    }

    // CP2 消息归属：必须有 session，且 session 绑到调用方
    if (r.mode != TransportMode.STDIO) {
        if (r.sessionId == null || r.sessionId.isBlank())
            return deny("CP2", "id-only message - id collision across clients");
        if (!canon(r.sessionPrincipal).equals(canon(r.principal)))
            return deny("CP2", "session bound to a different principal");
    }

    // CP6 声明只进审计，append-only，凭证只记摘要
    appendAudit(r, true, "accepted");
    return new Verdict(true, "CP6", "allowed after isolation checks");
}
```

三个容易写错的地方：

1. **常量要先归一化再存**。管理路由集合存 `norm()` 小写形式，比对也走 `norm()`。我们上一版踩过「常量存原拼写、比对走归一化」的坑，合法工具被误判成 elevated——这是误杀，比漏拦更伤客户信任。
2. **控制点顺序要为可达性服务**。CP4/CP3 放在 CP1/CP2 之前，保证「管理路由无 token」的攻击命中 CP3，而不是先被 CP1 的实例检查抢走——否则你以为测了六个门，实际只测了一个。
3. **凭证原文绝不入审计**。`appendAudit()` 里 `sha256(credentialRef).substring(0,12)`；微软 Azure AI Gateway 11-28 GA 的 JD 与实测都强调「Never expose credentials in logs」，这不是装饰。

## 二、对照组方法论：只写顺风测试会选错方案

判定代码写完后，最容易犯的错是只造攻击用例、看拦截量自嗨。真正的验收必须有**合法对照组**——守卫不能误杀正常业务。

本期自检套件共 **32 条断言**：

| 类别 | 条数 | 说明 |
| --- | --- | --- |
| attack | 20 | CP1 跨客户端复用 6 条、CP2 id-only/会话错绑 5 条、CP3 管理路由 4 条、CP4 非 loopback 3 条、CP5 凭证错主 2 条 |
| control | 9 | stdio 无 session、每请求独立实例、stateful 正确会话、管理路由带 admin token、localhost、假 ADMIN 声明、凭证 owner 正确、payload 含 `DROP TABLE` 字样的只读查询、同 principal 复用自己实例 |
| boundary | 3 | 凭证原文不入审计、旧基线漏检计数、null 请求 fail-closed |

跑出来：

```
V1.82 shared-transport self-test: 20 attack + 9 control + 3 boundary = 32 assertions ALL GREEN
legacy baseline (transport + token only) missed 17/20 attack cases; 9 legitimate calls, 0 mis-killed
```

`legacyBaseline` 故意模拟旧逻辑「transport 非空 + token 非空就放行」——它漏掉 17/20 条攻击。这条对照是整个套件里最有说服力的一行：**不是我们拦得多，是旧基线真的漏得多，而且我们没有因此误杀任何一条合法调用。**

## 三、买方必问：接了网关是不是就安全了

Azure AI Gateway 在 **2026-11-28** 正式 GA：每网关每月 1 万免费请求，2027-01-01 起 `$500/网关/月` 含 1000 万请求、超出 `$20/百万`。它能联邦 MCP servers、把 OpenAPI 转成 MCP 工具、把调用方 key 换成自己的 managed identity 再转发、按 user/team/agent 归属成本。

但一位在 Howden Insurance 部署过的 MVP 实测者写了一句关键的话：**把网关关联到 Foundry，并不阻止别人直连 Foundry**——你还需要私有端点 + RBAC 强制流量过网关。

这正是我们 CP1 的存在意义：即使接入了网关，只要有人让第二个客户端复用了同一个 transport 实例，响应就会串线。安全边界不在「接没接网关」，在「每个实例属于谁、每条消息归属谁、每笔账算谁的」。

## 四、落地清单（9 条）

1. 排查应用是否把 `McpServer`/`Server`/transport 实例在启动时构造一次、多客户端共享——是则改工厂模式每次调用新建。
2. HTTP 模式禁止裸绑 `0.0.0.0`；默认绑 `127.0.0.1`，需要远程接入时强制 `MCP_AUTH_TOKEN`。
3. `/mcp` 之外的 `/api/*` 管理路由单独鉴权，token 与数据面分离。
4. 消息路由强制携带 session id + principal 绑定，禁止只按 JSON-RPC id 关联。
5. 凭证按 owner 隔离，网关侧把调用方 key 换成 managed identity 再转发。
6. 审计 append-only、凭证原文不入盘、只记摘要。
7. 区分依赖语言：CVE-2026-111770 影响 TypeScript SDK；Java SDK 须按自己版本单独核实，不可混称。
8. 升级后做复验：官方明确「升级会破坏复用实例、须改代码」，把改造前后的断言跑一遍。
9. 把「旧基线漏检率」写进验收报告——这是我们找到的最能让买方点头的单一数字。

## 五、诚实边界

- 本框架**不受**上述 CVE 直接影响（均为第三方产品）；官方 MCP **Java** SDK 仍是核心依赖，须单独披露版本。
- Guard 在 `examples/` 层、未接入 21 个主模块；`ATTACHED` 是进程内 Map、会话绑定是字符串标志，非分布式会话存储 / 非密码学签名 / 非真实传输层实现。
- 32 断言是本仓库自检输出，不是第三方渗透测试结果；CVE-2026-111770 无在野利用、未入 KEV。
- 全部报价为公开挂牌价非成交价；Spring AI 2.0 未升级。

完整验收矩阵与三档报价见 [`shared-transport-boundary-2026-11-28.md`](shared-transport-boundary-2026-11-28.md)，可运行守卫在 [`examples/shared-transport-guard/`](../examples/shared-transport-guard/)。
