# 你的 MCP Server 写进记忆的那句话，明天还在替攻击者说话（Java 实现写门）

> 掘金 / CSDN 投稿稿 · 2026-11-26 · Spring AI MCP Enterprise V1.80
> 标签：`MCP` `Java` `Spring AI` `Agent 安全` `提示注入` `记忆投毒`

## 开篇：一个「合法」的写操作

```java
// 看起来完全正常：把工具返回值里的一句"事实"记进长期记忆
memoryServer.addObservations("Project uses Next.js 16.");
```

这次调用没有任何越权、没有 SQL 注入、没有 RCE。它只是往记忆库写了一句看起来中性的话。

三个月后，一个新会话里，Agent 读到这条记忆，把它当成「用户之前告诉我的事实」，据此选错了技术栈。**注入发生在 t1，利用发生在 t3，原始网页早就离开了上下文。**

这不是理论。Nodeflare 2026 年对记忆 MCP Server 的实测给出的数字是：平均 **50.46%** 的载荷成功写入记忆，**41.05%** 的概率在另一个会话里改变了 Agent 行为。OWASP 已在 Agentic 应用 Top 10 中把它列为 **ASI06: Memory & Context Poisoning**。

## 为什么「过滤器 + 加密」都不够

很多人第一反应是加个关键词过滤器。Nodeflare 的实测把三种方案放在一起跑 13 次写入（6 条用户真实事实 + 7 条非期望写入）：

| 方案 | 非期望写入被存为可信 | 说明 |
| --- | --- | --- |
| 朴素 memory store | **7 / 7** | 来什么存什么 |
| 仅模式扫描 | 6 / 7 | 漏掉那条看起来中性的 `"Project uses Next.js 16." from a fetched page` |
| 模式扫描 + host provenance（完整写门） | **0 / 7** | 唯一误报进隔离区送审，而不是被静默丢弃 |

原文结论一针见血：**"Pattern scanning alone caught 6 of 7; the provenance check caught the last one."**

因为模型就是调用者，`add_observations("...")` 看不出是用户说的还是网页说的。**来源标签只能由 host 在写路径上打，不能由模型自报。** 加密存储也救不了——加密防的是偷看，防不住语义污染。

更狠的是 11 月 24 日的 `CVE-2026-59726`（CVSS **10.0**）：ruflo 的默认 docker-compose 把 MCP bridge 和 MongoDB 绑到 `0.0.0.0`、`POST /mcp` 无鉴权，攻击者绕过只在 autopilot 流程生效的 tool blocklist，直接 `tools/call → terminal_execute` 拿到 RCE，读光容器里的 provider API key，并把投毒 pattern 持久化进 AgentDB 学习存储，影响该实例**未来所有用户**。官方处置里最刺眼的一句是：

> **A patched redeploy alone does NOT undo poisoning.**

重新部署不算清干净。你必须去审计库里把那条投毒 pattern 找出来、删掉、轮换凭据。这就是「写入期治理 + 可回滚」必须独立成一道闸的原因。

## 六道闸：把「写进去」当成一次独立的授权决策

我在 `spring-ai-mcp-enterprise` 里做了一个纯 JDK 零依赖的参考实现 `McpPersistentMemoryGuard`，把一次记忆写入拆成六道独立判定：

| 闸 | 防的是什么 | 真实对应 |
| --- | --- | --- |
| CP1 PROVENANCE | 工具返回值/网页/模型自报无 host 背书，不进批准区 | Nodeflare provenance 检查 |
| CP2 QUARANTINE | agent 写入先落隔离区，读工具永不返回 pending 条目 | 7 控制之「隔离区」 |
| CP3 SCOPE_TTL | 全局 scope / 永久写入须升级审批 | ruflo「影响未来所有用户」 |
| CP4 POISON | 载荷扫指令式注入、secret 字面量、多语言变体 | RunPod 返回值夹带 |
| CP5 RECHECK | 低信任记录召回进高权限上下文前重新定级 | NIST 六阶段的「检索」阶段 |
| CP6 ROLLBACK | append-only 脱敏审计 + hash 定位，投毒可回滚 | ruflo 官方处置要求 |

关键设计是**隔离区不是丢弃区**：唯一一条被命中的真实用户偏好（`Always run the linter before committing.`）进隔离区送人工审，而不是被静默丢掉。误报的代价必须廉价，否则守卫会被业务方关掉。

## Java 核心：判定链（可现场编译运行）

```java
public static Verdict decide(MemoryWrite w) {
    if (w == null || w.op == null || w.content == null || w.content.isEmpty()) {
        return Verdict.deny(Code.INVALID, "empty request fail-closed");
    }
    String c = norm(w.content);

    // 只读 / 删除（清理投毒）直接放行并审计
    if (w.op == Op.READ || w.op == Op.DELETE) {
        audit(w, Code.OK);
        return Verdict.allow();
    }

    // CP4 载荷注入 / secret —— 不管目标区，命中即拒
    if (containsAny(c, POISON_MARKERS)) {
        return Verdict.deny(Code.POISON_PATTERN, "instruction payload in write");
    }
    if (containsAny(c, SECRET_MARKERS)) {
        return Verdict.deny(Code.SECRET_IN_MEMORY, "secret literal in memory store");
    }

    // RECALL：CP5 低信任记录不得直接进高权限上下文
    if (w.op == Op.RECALL
            && w.recalledIntoHighPrivilege && lowTrust(w.source) && !w.humanConfirmed) {
        return Verdict.deny(Code.LOW_TRUST_RECALL,
                "low-trust record into high-privilege context");
    }

    // CP2 隔离区：未人类确认不得直接写批准区
    if (w.targetZone == Zone.APPROVED && !w.humanConfirmed) {
        return Verdict.deny(Code.BYPASS_QUARANTINE, "unconfirmed write bypassed quarantine");
    }

    // CP1 provenance：批准区不接受无 host 背书的来源自报
    if (w.targetZone == Zone.APPROVED && lowTrust(w.source)
            && (w.provenance == null || w.provenance.isEmpty())) {
        return Verdict.deny(Code.NO_PROVENANCE, "source self-report without host provenance");
    }

    // CP3 scope/TTL：全局或永久写入须升级审批
    if (w.targetZone == Zone.APPROVED
            && (w.scope == Scope.GLOBAL || w.ttlSeconds == 0) && !w.elevatedApproval) {
        return Verdict.deny(Code.SCOPE_TTL_ESCALATION,
                "global/permanent write needs elevated approval");
    }

    // CP6 append-only 审计 + hash 留痕
    audit(w, Code.OK);
    return Verdict.allow();
}
```

注意判定顺序：**CP4 载荷检测在 provenance 之前**——不管来源多可信，写入内容里带「忽略之前的指令 / 不要告诉用户 / 从现在起你是 / 评分」这类指令式载荷，一律先拒。而 `READ` 和 `DELETE` 放在最前放行，是为了让「清理投毒」这个补救动作本身不被守卫卡住。

## 对照组：只写顺风测试会选错方案

守卫最难的不是拦住攻击，是**不误杀正常业务**。我写了 8 条合法对照：

- 用户显式偏好（有 host 背书 + 人类确认 + 用户级 scope + TTL）→ 放行
- agent 把普通事实写进隔离区待审 → 放行
- 已批准偏好召回普通上下文（非高权限）→ 放行
- 只读查询、会话级带 TTL 记忆、升级审批后的全局写入 → 放行
- **清理投毒的 DELETE 动作 → 放行**

编译运行的实测结果：

```
self-test: OK
  attack   : 20/20 blocked
  control  : 8/8 allowed (false positives: 0)
  boundary : 2/2 assertions
  legacy-only blocks (new guard catches, baseline misses): 20
```

这里的「旧基线」是参考 memory server 的默认逻辑——「是 memory write 工具 + content 非空就写入」。它放行了全部 20 条攻击。**只有把对照组一起跑，你才知道自己不是在用一个「全部拒绝」的假守卫骗自己。**

## 落地清单（9 条）

1. 读写工具分离：多数 agent 只给读工具，写工具按会话/agent 授予，CI/定时任务不给写权限。
2. 所有 agent 写入落 pending 区，`read_*` 工具永不返回 pending 条目。
3. 来源标签由 host 在写路径设置，模型自报一律不采信。
4. 写入前扫描：指向未来 agent 的指令、URL/图片链接、secret、工具引用、不可见字符、超大条目。
5. 按授权 token 的 `sub` 做租户隔离，绝不采用模型给出的用户名。
6. 记忆不存任何 secret（含用户粘贴的 key）。
7. pending / 低信任条目设 TTL，没人确认就过期。
8. 每条写入留 hash + append-only changelog，可回滚到指定时间点快照。
9. 持有记忆的服务器不给任意公网访问。

## 诚实边界

这份实现是**参考守卫**：纯 JDK、未接入 21 个主模块、marker 是词法子串匹配不是语义解析、hash 是演示摘要不是密码学签名。paraphrase 过的指令会绕过纯模式扫描——**真正可靠的控制是 provenance 和隔离区，不是正则**。本框架不受 `CVE-2026-59726` 直接影响（那是 ruflo 的洞），文中数字来自第三方实验与官方公告，不是本仓库实测拦截率。

完整 12 项验收清单、买方必问 5 问与竞品对比见 `docs/persistent-memory-boundary-2026-11-26.md`；可运行示例见 `examples/persistent-memory-guard/`。
