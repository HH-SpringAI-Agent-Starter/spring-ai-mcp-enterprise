# 确认令牌被原路返回给调用方：MCP 审批链的第十三道边界

> 掘金 / CSDN 双投稿件 · 2026-11-27 · 关键词：MCP 安全、confirmation_token 自批、CVE-2026-131688、Spring AI Alibaba、Java 执行门

## 开头：一个「双人复核」的笑话

`pain001-mcp` 的付款批次流程有两步：`stage_payment_batch` 先返回 `confirmation_token`，`commit_payment_batch` 拿这个令牌执行。问题是——**两步都由同一个调用方完成，令牌也是他自己去拿的**。这就是 CVE-2026-136620（CVSS 3.1 7.1，11-25 入库）：所谓的「确认令牌」根本不代表有第二个人复核过。雪上加霜的是 `output_file_path` 没做边界校验，生成的 XML 能写到服务账号可写的任何地方。修法是 0.0.80：真实路径 confinement、符号链接校验、只允许 `.xml`、禁止覆盖已有文件。

同一周还有 CVE-2026-131688（11-26 公开，CVSS 3.1 8.1）：WordPress Secure MCP Server ≤1.5.8 里，**scope 受限的连接测试令牌**能越过工具白名单，直接拿到令牌所属管理员的完整 Administrator 角色。注意 CNA 给的 PR:N 不代表不需要令牌——你还是得先有一个有效 bearer token。

两件事拼在一起，结论很清楚：

> **审批令牌是流程句柄，不是独立审批人；scope 是声明，不是权限边界。**

再加上官方 MCP servers v2.5.0（11-18 一次修 8 个洞）：只读的 `git_diff` 遇到构造过的 `.git/index` 能读出工作目录外的文件，`git_merge` 触发 git 自动维护执行仓库自己 `.git/config` 里的 `gc.recentObjectsHook`（官方 PoC 里以 uid=0 root 跑），filesystem 的符号链接 TOCTOU 在 Windows 上到 #5600 还没修——**只读入口内部委托出去的动作，必须逐段重新判定**。

## 一、为什么「声明」永远不能进允许分支

我们做 Spring AI MCP Enterprise 时反复撞到同一堵墙：几乎所有出事的路径，都是把某个声明当成了授权事实。

- `readOnlyHint: true` → UI 自动批准 → 实际执行了写操作（DOMShell 75518）
- scope 声明 `read` → 网关放行 → 令牌其实能提权到管理员（131688）
- `confirmation_token` 存在 → 认为是双人复核 → 令牌由调用方自己领取（136620）
- 「标了只读」→ 跳过内部动作检查 → git hook 以 root 执行（v2.5.0）

这些声明的共同特点是：**它们由请求方或被请求的工具自己提供**。让被授权者提供授权证据，本身就是设计错误。正确做法只有一种——声明进审计日志，允许分支只认服务端独立记录的事实。

## 二、Java 执行门的三段骨架

下面是 `McpApprovalChainGuard`（纯 JDK 17、零依赖）里最关键的三段。完整实现和输出见 `examples/approval-chain-guard/`。

### 1）审批人必须与请求人隔离

```java
if (needsApproval) {
    if (r.confirmationToken == null || r.confirmationToken.isBlank()) {
        return deny("CP1", "state-changing effect has no confirmation token");
    }
    if (!"CONTROL_PLANE".equals(r.tokenIssuer)) {
        return deny("CP1", "token not issued by the control plane");
    }
    if (r.tokenSubject == null
            || canon(r.tokenSubject).equals(canon(r.principal))) {
        return deny("CP1", "self-approval: approver is the requester");
    }
    if (!APPROVERS.contains(canon(r.tokenSubject))) {
        return deny("CP1", "approver is not allowlisted");
    }
}
```

### 2）声明只进审计，服务端独立定级

```java
final String requiredScope =
        READ_TOOLS.contains(canon(r.tool)) ? "read" : "elevated";

// claimedScope 和 readOnlyHint 从不出现在下面的判断里
if (!requiredScope.equals(canon(r.effectiveScope))) {
    return deny("CP2", "server-recorded scope " + r.effectiveScope
            + " != required " + requiredScope + " (claim ignored)");
}
```

### 3）一次性 + TTL + payload 绑定

```java
if (r.tokenIssuedAt > r.now
        || r.now - r.tokenIssuedAt > TOKEN_TTL_MS) {
    return deny("CP5", "confirmation token expired or future-dated");
}
if (!boundHash.equals(r.tokenBoundHash)) {
    return deny("CP5", "confirmation token not bound to this exact payload");
}
if (!CONSUMED.add(r.confirmationToken)) {
    return deny("CP5", "confirmation token replayed");
}
```

还有 CP3：内部委托动作逐段重判。`gc.recentObjectsHook`、`promisorfetch`、`symlinkresolve`、`filewrite` 这些动作在只读入口里出现时一律拒绝——**入口的只读声明不向下继承**。CP4 做真实路径 confinement（`toRealPath()` + `startsWith(root)` + 后缀白名单 + 禁覆盖 + 全链软链检查）。CP6 审计只写令牌的 SHA-256 前 12 位，原文不入盘。

## 三、对照组：只写顺风测试会选错方案

自检跑出 **20 attack + 8 control + 3 boundary = 31 断言全绿**，旧基线（`confirmation_token` 非空 + `readOnlyHint`）漏掉 **15/20** 条攻击，8 条合法调用 0 误杀。

开发过程中真的抓到两个 bug：

1. 常量池用原始拼写（`search_records`、`risk-officer-1`），比对却走 `canon()` 归一化（去下划线、转小写）→ 合法工具和合法审批人都被误杀。修正为常量本身也用 `canon()` 形式。
2. 重放用例是手动注册的，legacy 漏检计数没跟着累加 → 边界断言口径错位。

**这就是为什么对照组不是装饰**：只写「攻击被拦住」的测试，你会选出一个把正常业务全杀掉的方案；只写「正常调用放行」的测试，你会选出一个什么都不拦的方案。

## 四、9 条落地清单

1. 状态变更必须有独立审批人，审批人与请求人不能是同一主体。
2. 确认令牌由控制面签发，绑定精确 payload 的哈希，5 分钟有效，一次性消费。
3. scope 由服务端按工具独立定级；客户端声明的 scope 只写审计。
4. `readOnlyHint` 只写审计，不参与任何允许分支。
5. 只读工具内部委托的 hook / submodule / filter / promisor / symlink 动作逐段重判。
6. 输出产物做真实路径 confinement，限制后缀，禁止覆盖，全链拒绝符号链接。
7. 升级到修复版后逐路径复验：filesystem 2.4.0 是语义版，npm 认为它比 2025.x/2026.x 低，**不会提示升级**，必须手动装。
8. 审计 append-only，令牌与凭据原文不落盘；脱敏开关本身也要按内容二次复核。
9. 每次发布都跑攻击 + 合法对照两组断言，缺一组不发。

## 五、给买方的两个问题

招标和售前里，只需要问两句就能筛掉大半供应商：

- 「你们的确认令牌是谁签发的？调用方能不能自己拿到？」——答「工具自动返回即可」的，对位 CVE-2026-136620。
- 「标了 readOnly 的工具，内部委托的动作还检查吗？」——答「标了就不查」的，对位官方 v2.5.0 的 git 洞。

## 边界声明

本框架不受上述第三方 CVE 直接影响；Guard 位于 `examples/` 层，未接入 21 个生产模块；审批人与令牌是字符串 allowlist 与进程内哈希绑定，**不是密码学签名，也不是独立身份系统**；路径 confinement 是判定不是运行时竞态防护。把它当可执行的参考设计和验收清单，不要当生产组件。Spring AI 2.0 尚未升级。
