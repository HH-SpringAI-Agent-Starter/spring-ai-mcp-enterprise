# 你的 MCP Server 沙箱是纸糊的：从 CVSS 9.9 的 MCPHub 到 Python SDK 在野洞，执行环境面为什么是最后一道没关的门

> 掘金 / CSDN 发布稿 · 2026-11-22 · Spring AI MCP Enterprise V1.77
> 关键词：MCP Server 安全、沙箱逃逸、MCPHub CVE、Java MCP、Spring AI、执行环境隔离

## 开场：三道门都关了，攻击者从第四道进来了

过去两周我们连续写了八篇文章，讲 MCP 的注解面、注册中心面、只读声明面、决策层、配置面、结果面、身份传播面、会话/传输绑定面。每写一篇都以为「这应该是最后一道了」。

11 月中旬的三个 CVE 把这份自信打碎了：

- **MCPHub `CVE-2026-79748`，CVSS 9.9**：登录用户（不需要管理员）调用 `POST /api/servers` 注册一个 `command=/bin/sh` 的 server，网关立刻以 root（Docker 镜像默认）执行。`edit` 端点还不校验归属，你能把管理员建好的 server 直接改掉。
- **Hermes Agent `GHSA-p22p-9ph6-gmrh`，11-19, High**：agent 在 docker/ssh 后端里，从一个「任何后端进程都能写」的目录读回 env 文件，把每个 `KEY=VALUE` 合并进宿主机启动的进程。沙箱里的代码塞一个 `LD_PRELOAD`，就以 gateway 用户身份执行了，provider keys 和 session history 全部可读。
- **NVD 11-18 三连**（Gortex `62964` 7.8 / yt-mcp `75612` 7.5 / DOMShell `75518` 4.8）：读工具认真做了仓库 confinement + symlink 检查，写工具却收绝对路径跳过两查；`domshell_eval` 标了 `readOnlyHint:true`，客户端高高兴兴地把审批 UI 显示成「只读、自动放行」，实际跑的是任意 CDP `Runtime.evaluate`。

共同点只有一个：**作者认真守住了自己画的那条路，攻击者走了没画的那条。** 声明层——`readOnlyHint`、`sessionId`、「已登录」——在每一次都帮了倒忙，因为它们被当成了授权依据。

## 判词：声明层一律不参与授权

> 沙箱 confinement 只守住作者画的那条路；`readOnlyHint` / `sessionId` / 登录态一律不参与授权。
> 写路径必须和读路径同等 confinement，命令必须过 allowlist，env 必须源头与键名双白名单，
> 写动词必须服务端独立定级 + 审批，会话必须按 issuer + subject 绑定，默认只读 fail-closed。

## 六道闸的 Java 骨架

我们在 `examples/execution-environment-guard/` 放了一份纯 JDK 17、零依赖的参考实现。核心 `decide()` 是一条有序短路链：

```java
public static Verdict decide(Request r) {
    Verdict v = checkCommand(r);   // CP2 命令 allowlist + argv 元字符
    if (!v.allowed) return v;
    v = checkPath(r);              // CP1 读写同 confinement
    if (!v.allowed) return v;
    v = checkEnv(r);               // CP3 env 源头 + 键名双白名单
    if (!v.allowed) return v;
    v = checkTier(r);              // CP4 服务端独立定级
    if (!v.allowed) return v;
    v = checkSubject(r);           // CP5 issuer + subject 绑定
    if (!v.allowed) return v;
    appendAudit(r);                // CP6 append-only 脱敏
    return Verdict.allow();
}
```

### CP1：读写同查（对位 Gortex / yt-mcp）

最容易犯的错就是「read 查 confinement，write 忘了查」。我们的做法是路径判定统一走一个函数，绝对/相对路径都归一化到 root 下：

```java
private static Verdict checkPath(Request r) {
    String p = r.targetPath;
    if (p.startsWith("/")) {
        String norm = normalize(p);
        if (!norm.equals(CONFINEMENT_ROOT) && !norm.startsWith(CONFINEMENT_ROOT + "/")) {
            return Verdict.deny(Code.ESCAPE_ABSOLUTE_PATH, "outside root");
        }
    } else {
        String norm = normalize(CONFINEMENT_ROOT + "/" + p);
        if (!norm.startsWith(CONFINEMENT_ROOT + "/")) {
            return Verdict.deny(Code.ESCAPE_PATH_TRAVERSAL, "traversal");
        }
    }
    // 符号链接逃逸 + 控制字符 ...
    return Verdict.allow();
}
```

### CP4：不信任 readOnlyHint（对位 DOMShell / MCPHub）

写动词的定级只由 `operation` 决定，客户端声称什么都不看：

```java
private static Verdict checkTier(Request r) {
    boolean serverSideWrite = WRITE_OPS.contains(r.operation);
    if (serverSideWrite && r.claimedReadOnly) {
        return Verdict.deny(Code.READ_ONLY_LIE, "write op declared readOnlyHint=true");
    }
    if (serverSideWrite && (r.approvalToken == null || !APPROVAL_TOKEN.equals(r.approvalToken))) {
        return Verdict.deny(Code.WRITE_TIER_NEEDS_APPROVAL, "write op lacks approval");
    }
    return Verdict.allow();
}
```

注意「声明说谎」是独立的拒绝码。这不是吹毛求疵——DOMShell 骗过 Claude Desktop 和 Cursor 的审批 UI，靠的就是没人核对声明与实际动作是否一致。

### CP5：共享 OAuth client 必须填 subject（对位 Python SDK 52869）

`CVE-2026-52869` 是 11-20 进 VulnCheck 在野目录的官方 SDK 洞：SSE / stateful Streamable HTTP 只凭 `session_id` 路由，不校验请求来自打开会话的同一个认证客户端。官方修复里有一句关键提示：**如果很多用户共享一个 OAuth client（比如 hosted client 或网关），必须让 token verifier 填 `AccessToken.subject`，否则会话只按 client 分离、不按 user 分离。**

我们把这条写成了硬规则：

```java
if (r.authClientId != null && SHARED_CLIENTS.contains(r.authClientId)
        && r.sessionSubject.equals(r.authClientId)) {
    return Verdict.deny(Code.SHARED_CLIENT_NO_SUBJECT, "shared client without per-user subject");
}
```

把 clientId 当成 subject 用 = 拒绝。这正是官方洞的成因。

## 对照组方法论：为什么只看「拦住了多少」会骗人

写安全守卫最容易自欺的指标是「我拦下了 N 个攻击」。如果 N 个攻击本来旧逻辑就能拦，你的增量为零。所以我们固定跑两组对照：

- **20 attack**：本期五类攻击（路径逃逸 / 命令注入 / env 注入 / 只读声明说谎 / 会话主体失配），新守卫 20/20 全拦。
- **旧基线**：只看 `sessionId` 非空 + `readOnlyHint` 的朴素逻辑，这 20 条它**全放行** → 20 条都是真实增量。
- **4 control**：只读读文件 / 带审批的合法写 / 搜索 / 范围内附件 → 4/4 放行，**0 误杀**。
- **2 boundary**：审计脱敏（原始 env 值和 subject 不落盘）、空请求 fail-closed。

实测：`self-test: OK / attack 20/20 / control 4/4 / boundary 2/2 / total 26`。

这套方法论来自我们此前做自适应标定的教训：**只看「误报更少」会选错方案**。守卫也一样，只看「拦得更多」会写出一个谁都调不动的系统。

## 给 Java / Spring 团队的两句实话

1. **Spring AI 官方 SDK 已经在帮你**。Spring AI 2.0 内置 MCP Java SDK 2.0.0，Streamable HTTP 默认、`@McpTool` 等注解进核心。但官方 SDK 管的是「怎么连、怎么声明」，不管「执行那一刻的 confinement」——这一段得自己写，或者用一个把六道闸做成可演示样品的框架。
2. **别把第三方 CVE 当成「我们修好了」**。我们引用 MCPHub / Hermes / Gortex / Python SDK 的 CVE 是当设计依据，本框架不受这些洞直接影响，参考守卫也还在 `examples/` 层、没有接进 21 个主模块。策略硬编码、符号链接集合模拟、单进程审计表都是演示约束，生产要外置到配置中心 + KMS 轮换。

## 下一步

第九段收口之后，剩下的不是「第十个面」，而是把九段闸门真正串成一条调用链并接进网关——这是我们 `docs/gate-integration-map` 里分期①的活。如果你在做 MCP 私有化交付，欢迎拿这份 12 项执行环境面验收清单去压你的供应商。

（完整清单 + 买方必问 5 问 + 三档报价见 `docs/execution-environment-boundary-2026-11-22.md`；可运行样品见 `examples/execution-environment-guard/`。）
