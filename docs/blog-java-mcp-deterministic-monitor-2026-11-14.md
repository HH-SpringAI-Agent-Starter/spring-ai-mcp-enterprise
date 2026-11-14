# 我们把 MCP 的护栏写进了提示词，直到看到那组数字：100% vs 0%

> 适合发布：掘金 / CSDN / InfoQ / 微信公众号 · 约 2600 字 · V1.72 · 2026-11-14
> 关键词：MCP Server、Spring AI、Java、智能体安全、in-prompt guardrail、reference monitor、IBM ContextForge CVE、任务绑定授权

---

## 一、先说结论

如果你现在的 MCP 加固方案里包含下面任意一条：

- 在系统提示词里写了「不要执行危险操作」
- 加了一个人工审批弹窗，用户点了「同意」才调用工具
- 给工具标了 `readOnlyHint` / `destructiveHint`，运行时按标注决定放不放行
- 连接串里写了 `read_only=true`

**这四条在 2026 年 11 月的公开实证面前，都不构成安全边界。**

不是「不够强」，是**位置放错了**。真正的边界必须在**模型之外**、**按当前任务签发**、**确定性执行**、**默认拒绝**。

---

## 二、那组让我改设计的数字

11 月 12 日 MindPattern 复盘了一篇 arXiv 论文，实验设置很朴素：Qwen2.5-1.5B / 7B、13 个任务、12 个工具、每种防御每模型 156 个攻击 episode。结果：

| 指标 | 模型自决 / in-prompt 护栏 | 任务绑定的模型外确定性 monitor |
| --- | --- | --- |
| 模型提出攻击者工具调用 | 64% / 84% | — |
| 实际被放行 | — | **1.3% / 7.7%** |
| **持久自适应攻击者下攻击成功率** | **100%** | severe actions **0%** |

> 论文里 monitor 的定义很关键：**一个确定性的、在模型之外的检查，知道「当前这个任务」允许哪些动作。** 它不读模型的理由，不读工具的自述，不读弹窗结果。

同一周还有三条补丁在讲同一个故事：

1. **VS Code 1.145.2 `GHSA-66hq-jw93-462h`**（8.3 / NVD 9.6）：MCP server 返回的 elicitation 文本被解释成交互式 command link，用户**点一下通知**就执行了命令。1.145.3 的修法不是「让模型别生成恶意文本」，而是**把通知渲染成纯文本并校验 payload**。
2. **官方参考 git server `GHSA-6hqx-gqrx-c5r2`**：`git_status` 被标成只读，但仓库自带的 `core.fsmonitor` 让 agent 一调用就执行代码。修法是 `GIT_CONFIG_COUNT` 强制置 false、清空 filter/merge drivers、拒 Git < 2.31。**「只读」描述的是工具想做什么，不是它能做什么。**
3. **Simon Willison `llm` 0.37.2**：两处把配置好的 OpenAI key 发给兼容端点。

三句话概括：**客户端信任了服务端发来的东西；工具信任了自己写的标注；配置信任了字符串。**

---

## 三、11-13，连网关自己都中招了

IBM 在 11 月 13 日首发两份 ContextForge MCP Gateway 安全公告（影响 v1.0.0~v1.0.11，统一修 v1.1.0）：

**`CVE-2026-110178`** —— `/v1/import` 批量导入带上 `conflict_strategy=update` 时，**不校验共享实体的归属**。一个持有 team-scoped `admin.import` 权限的远程认证用户，可以覆写**其他租户**公共工具/资源/提示的：

- `endpoint URL`
- `request headers`
- `authentication credentials`

然后把后续所有 AI 网关调用重定向到攻击者主机。IBM 在公告里写得很干脆：**Workarounds and Mitigations: None**（只能升级，且升级前不要把 `admin.import` 授给 team 级角色）。

**`CVE-2026-110194`** —— OAuth DCR（RFC 7591）从授权服务器 metadata 里读到 registration endpoint，**不校验就发请求**，认证用户可以借网关打内网。缓解措施恰好是官方给的 `DCR_ALLOWED_ISSUERS` 白名单 / `DCR_ENABLED=false` / 限制网关创建权限。

同一天 AWS `CVE-2026-125926` 的版本区间也坐实了：影响 **v1.0.3~v1.34.0**，**1.35.0 才修**；另一个管理 API 洞在 1.25.0 修，所以「我跑的是 1.25.0+」并不等于安全。更值得注意的是官方那句：**升级不会修改升级前已存配置** —— 你得自己复核 upstream 地址、翻日志、必要时轮换云角色凭据。

> **打补丁 ≠ 清干净。** 这是我们在 V1.70 就写下的判词，这周被两份官方公告同时盖章。

Permit.io 11-13 的长文顺手给了三条可以写进标书的判据：

1. `adapter:create` 是**独立权限**，rename 要单独校验，ownership 显式记录、transfer 必须授权（不能是创建的副作用）；
2. 内部 ID **不由用户可控的名称派生**，按 tenant 加前缀/命名空间，**409 Conflict 是安全信号，不能吞**；
3. 每个决策都必须携带 tenant：`may user X update adapter Y` 要写成 `may user X **in tenant T** update adapter Y **in tenant T**`。

---

## 四、Java / Spring 侧怎么落地

我们在 [spring-ai-mcp-enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise) 的 `examples/task-bound-monitor/` 放了一个**纯 JDK 17、零依赖、单文件可编译**的参考实现。核心就一句话：**决策只读控制平面签发的 `TaskGrant`，模型说什么一律不算数。**

### 4.1 授权凭据来自控制平面，不来自模型

```java
/** 控制平面按「当前任务」签发，模型不可写、不可转发、不可续期 */
public static final class TaskGrant {
    public final String taskId;
    public final String tenant;
    public final long issuedAt, expiresAt;
    public final String issuedBy;              // 必须是 CONTROL_PLANE
    public final Set<ActionKey> allowedActions; // (tool, verb) 白名单
    public final Set<String> allowedScopes;
    public final Set<String> allowedObjectIds;  // 对象级：account_id 这类
}

public Verdict decide(TaskGrant grant, ToolCall call) {
    // CP1：非控制平面签发的授权直接拒
    if (!CONTROL_PLANE.equals(grant.issuedBy))
        return Verdict.deny(CP1, "授权不是控制平面签发");

    // 模型自证只记审计，绝不参与授权
    if (call.modelAttestation != null && !call.modelAttestation.isBlank())
        log("IGNORE", CP1, call.taskId, "model attestation ignored");

    // CP2：任务绑定 + 默认拒绝
    if (!grant.taskId.equals(call.taskId))  return Verdict.deny(CP2, "跨任务复用授权");
    if (!grant.tenant.equals(call.tenant))  return Verdict.deny(CP2, "租户不匹配");
    if (call.now > grant.expiresAt)         return Verdict.deny(CP2, "授权过期");
    if (!grant.allowedActions.contains(new ActionKey(call.tool, call.verb)))
        return Verdict.deny(CP2, "动作不在任务授权白名单内");
    if (!grant.allowedScopes.contains(call.scope))
        return Verdict.deny(CP2, "资源范围越权");
    if (grant.allowedObjectIds != null && !grant.allowedObjectIds.contains(call.objectId))
        return Verdict.deny(CP2, "对象级越权（account_id 改写）");

    return Verdict.allow(CP2, "任务绑定确定性放行");
}
```

注意 `call.modelAttestation` 和 `call.readOnlyHint` **在允许路径上完全没出现**。它们只进审计日志。这就是「授权源隔离」。

### 4.2 导入路径：受保护字段 + 归属校验

```java
public Verdict screenImport(ImportRequest req) {
    // 跨租户覆盖一律拒（对位 IBM CVE-2026-110178）
    if (!req.actorTenant.equals(req.targetOwnerTenant))
        return Verdict.deny(CP3, "目标实体不属于发起租户");

    // team-scoped 权限不得执行 update 覆盖策略
    if (req.hasTeamScopedAdminImport && "update".equalsIgnoreCase(req.conflictStrategy))
        return Verdict.deny(CP3, "team-scoped 导入权限不得执行 update");

    // endpoint / headers / credentials 属于受保护字段，导入路径不得改写
    for (String f : req.fields.keySet())
        if (PROTECTED_FIELDS.contains(canon(f)))
            return Verdict.deny(CP3, "受保护字段不得经导入改写：" + f);

    // 同名基础设施对象冲突：409 是安全信号，不能被 skip/update 吞掉
    if (req.infraObjectExistsWithSameName && !"error".equalsIgnoreCase(req.conflictStrategy))
        return Verdict.deny(CP3, "冲突不得被吞");

    return Verdict.allow(CP3, "导入归属校验通过");
}
```

> 顺带记一个真实的坑：首版 `PROTECTED_FIELDS` 写成带下划线的 `request_headers`，而字段比对统一走 `canon()` 归一化成 `requestheaders` —— **攻击用例当场把这个保护绕过去了**。修法是受保护字段集本身也归一化。**只写顺风测试，会选出一个看起来很安全、其实漏了整条路径的方案。**

### 4.3 升级后的存量必须全量复验

```java
public Map<String, Verdict> revalidateStored(List<StoredConfig> stored) {
    Map<String, Verdict> out = new LinkedHashMap<>();
    for (StoredConfig c : stored) {
        if (!screenUpstreamUrl(c.upstream, false).allowed)
            out.put(c.id, Verdict.deny(CP5, "存量上游不合规（升级不会自动修正）"));
        else if (!fingerprint(c).equals(c.fingerprint))
            out.put(c.id, Verdict.deny(CP5, "配置指纹漂移 → 隔离待人工复核"));
        else
            out.put(c.id, Verdict.allow(CP5, "存量复验通过（幂等）"));
    }
    return out;   // 必须幂等，可导出成复验报告
}
```

### 4.4 分层图

```
[控制平面]  TaskGrant(taskId, tenant, actions, scopes, objectIds, 有效期)
                 │ 只读下发
                 ▼
[Agent/LLM] ──提议 ToolCall──▶ [mcp-gateway]
                                   │
                    ┌──────────────┼───────────────┐
                    ▼              ▼               ▼
             连接路径准入    任务绑定监视器    只读执行层强制
             (URL/真实IP/     (CP1~CP6,       (词法全文扫描/
              Host/SNI)        fail-closed)     绑定参数/会话复位)
                    └──────────────┼───────────────┘
                                   ▼  任一 DENY 即整体 DENY
                          真实工具 / DB / API
                                   ▼
                     append-only 脱敏审计 → Prometheus
```

**授权数据在左上方，模型在左边，两者之间必须有一条不可逾越的线。**

---

## 五、跑一下：51 条断言，8 个合法调用一个不误杀

```bash
cd examples/task-bound-monitor
javac -encoding UTF-8 -d /tmp/mcpv172 McpTaskBoundReferenceMonitor.java
java -cp /tmp/mcpv172 McpTaskBoundReferenceMonitor
```

```
[attack] 16 cases, legacy-baseline would have ALLOWED 11 of them
         (incrementally blocked by the deterministic monitor)
[control] 8 legitimate cases ALLOWED, no regression vs legacy baseline
self-test: OK | attack=36 control=8 boundary=7 | total=51 | failures=0
         | incrementally-blocked-vs-legacy=11
```

**比「拦下 36 个攻击」更重要的是那 8 个合法调用全部放行**：参数里带 `DROP TABLE`、`ignore previous instructions`、`/* ; DROP TABLE t */` 的合法只读查询都没有被误杀。示例内置了一个「旧方案」（只看注解 + 模型自证）作对照组，断言**新方案不得劣于基线**。

> 只看拦截量，你会选出一个把业务全拦死的方案。这是我们在四期 Gate 里反复踩到的同一条教训。

---

## 六、市场侧：钱已经不在「帮你建 MCP」了

11-13 广发研究 **MCP & Skills 正式上线智慧数平台**：5 项 MCP 能力、21 个行业、200+ 数据源、9 万余核心指标、1000+ 上市公司盈利预测、900+ 产业图谱。同一周 Swift Media 采购周报的标题是「**MCP gateways become a procurement requirement**」——集中工具注册表、per-agent scopes、检索内容注入过滤、可导出审计日志，已经写进 RFP。

嘉信理财 11-12 上线自有 MCP Server（$13.33T 客户资产、日均 1070 万笔交易），从只读扩展到能下单，但**每笔交易 client in-the-loop**；Sitenna 的 MCP Connector 逐字写着「**AI never has more access than the person using it**」。

所以对 Java / Spring 团队来说，机会不是再写一个 MCP Server，而是回答三个问题：

1. **谁在什么时候、被允许调哪个工具？**（任务绑定授权）
2. **出事能不能导出证据？**（append-only 脱敏审计）
3. **升级之后，之前被植入的配置还算不算数？**（存量全量复验）

这三个问题的共同点是：**都不能靠模型回答**。

---

## 七、诚实边界

- 这是 `examples/` 层的**参考实现，未接入主模块**，不是生产组件；`CONTROL_PLANE` 目前是字符串标志而非密码学签名，生产要补 MAC 校验、真实 DNS 解析后 IP 校验与逐跳重校验。
- arXiv 数字是**学术模拟实验**（小模型 / 13 任务 12 工具），证明的是控制点**位置**的重要性，**不是本框架的实测拦截率**。
- 本框架**不受**文中 IBM / AWS / VS Code / git server 等 CVE 直接影响，它们是论证控制点必要性的公开实证。
- 项目**无商业合同 / 中标 / 金融案例 / 等保测评**；文中报价均为公开挂牌价，非成交价。

---

**代码与全部文档**：[HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise) · Apache-2.0
本期新增：`examples/task-bound-monitor/`、`docs/deterministic-control-plane-boundary-2026-11-14.md`（12 项验收清单 + 买方必问 5 问）、`docs/gate-integration-map-2026-11-14.md`（四 Gate 接入点图与工作量评估）、`docs/earnings-report-2026-11-14.md`。

_如果这篇对你有用，欢迎 Star / 提 Issue —— 尤其是「对照组断言」这块，我们很想看到更多反例。_
