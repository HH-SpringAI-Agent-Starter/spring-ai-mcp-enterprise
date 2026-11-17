# 只读查询也能偷走管理员密码：MCP 的第六道边界，几乎没人防守

> 掘金 / CSDN 发布稿（V1.74 · 2026-11-17）
> 关键词：MCP 安全、MCP Server、Spring AI、Java 网关、CVE-2026-129875、结果面治理、盲注 oracle、readOnlyHint

---

过去一年，我们反复讲一个道理：**注解不是安全边界、注册中心不是、只读声明不是、审批弹窗不是、配置面也不是**。到 11 月中旬，我以为这个清单已经够长了。直到看到 REBUILD 的那个 CVE。

## 一个「只读查询」怎么偷走管理员口令

`CVE-2026-129875` 的利用过程，简单得令人不舒服：

1. REBUILD 4.5.0~4.5.4 暴露了一个 MCP 工具 `QueryRecords`，可以按字段过滤记录。
2. 它没有限制敏感列，于是低权限用户可以对 `User.password` 字段下 `LK2` 前缀匹配过滤。
3. 网关返回的是**匹配的记录总数**。
4. 攻击者拿这个 count 当**盲注 oracle**：先试 `a*`，再 `b*`……逐字符把管理员的 SHA-256 口令哈希还原出来，然后登录管理员。

整个过程中，工具是「只读」的，查询是「合法」的，网关是「认证过」的。**没有任何一步越权，但管理员口令被偷走了。**

这就是 MCP 治理一直忽略的第六道边界：**工具把什么交回给模型**。前面五道边界管的是「谁能动、动之前怎么判」，没人管「动完之后交出去的东西」。

## 同一周，AWS 用两个 CVE 补了一刀

11-16，AWS Labs 两个 MCP Server 同日暴雷：

- `CVE-2026-126816`：`aws-pricing-mcp-server` 的 cost report 生成器有路径穿越，可以往任意位置写文件。**更糟的是这个工具被错误标记成 read-only，MCP 客户端据此自动批准，用户根本没有确认机会。**
- `CVE-2026-126681`：`Timestream for InfluxDB MCP Server` 不限制连接端点，远程未认证攻击者可以通过 prompt injection，让工具把 API token 发到攻击者指定的地址。

再加上 11-16 的 MCP Python SDK `CVE-2026-115563`（向不可信 auth 端点泄漏 OAuth 密钥）和 code-review-mcp-server `CVE-2026-68934`（8.7 分，`folderPath` 参数拼进 `execSync` 直接跑 shell）——四条披露指向同一件事：

> **回传的字段、写出的文件、外发的端点、拼接的命令——这四样东西，才是真正需要守的地方。**

## 「只读」为什么救不了你

`126816` 最值得琢磨的不是路径穿越本身，而是那句「工具被错误标记 read-only，客户端自动批准」。

这跟我们前面反复验证的结论一模一样：**声明层不参与授权。** `readOnlyHint` 和注解、`destructiveHint`、审批弹窗上的文字、模型的自证一样，只能进审计，绝不能进允许分支。你把它当成放行依据，等于把门钥匙挂在门上。

用 Schwab（嘉信理财，$13.33T）11-12 上线的 MCP Server 做个正面对照就清楚了。它从只读跨到了下单，但它没有让模型「别做危险操作」，而是：

- 机构用自己的 IdP 发 token，**模型永远拿不到用户凭据**；
- 读工具和写工具拆成两个 scope，只有 thinkorswim 账户能下单；
- agent 先 prepare 拿到一个句柄，确认在机构自己控制的界面完成（MCP spec 的 elicitation URL 模式）；
- **记的是动作，不是聊天记录**——每次工具调用、待确认载荷、确认事件、订单 ID 全进机构原本给监管看的审计库。

一句话：**不是让银行相信模型，是让银行拥有授权服务器、工具目录和审计轨迹。** 这才是能过法务和合规评审的架构。

## 落地的六道闸（附 Java 骨架）

我们在 `spring-ai-mcp-enterprise` 里把这个判词做成了一个可运行的参考实现，纯 JDK 零依赖。核心是一次结果评估的短路链：

```java
public Verdict evaluate(ToolResult r) {
    // CP1：回传字段命中敏感 token => 禁止原样回传
    for (Field f : r.fields)
        if (isSensitiveField(f.name()))
            return deny("SENSITIVE_FIELD_RETURNED", ...);   // REBUILD 129875

    // CP6：声明只读却有副作用 => 自动批准陷阱
    if (r.declaredReadOnlyHint && r.sideEffect != SideEffect.NONE)
        return deny("READONLY_HINT_NOT_AUTHORITY", ...);    // AWS 126816

    // CP3：输出路径必须落在允许根目录内、无 ..
    if (r.sideEffect == SideEffect.WRITE_FILE && pathEscapes(r.outputPath))
        return deny("OUTPUT_PATH_ESCAPE", ...);

    // CP4：外发端点必须在 allowlist，拒绝 IP 字面量 / 云元数据
    if (r.sideEffect == SideEffect.NETWORK_EGRESS && !hostAllowed(r.egressEndpoint))
        return deny("EGRESS_ENDPOINT_NOT_ALLOWLISTED", ...); // AWS 126681 / SDK 115563

    // CP5：shell 参数含元字符 => 禁止 execSync 拼接
    if (r.sideEffect == SideEffect.SHELL && hasShellMeta(r.shellFragment))
        return deny("SHELL_METACHAR_IN_PARAM", ...);        // code-review 68934

    // CP2：敏感字段前缀枚举达阈值 => 盲注 oracle
    if (r.probe != null && r.probe.mode() == PREFIX
            && isSensitiveField(r.probe.fieldName())
            && r.probe.distinctGuesses() >= 5)
        return deny("ORACLE_ENUMERATION_DETECTED", ...);    // REBUILD 129875

    audit(r); // append-only，字段名白名单 + 值脱敏 + rule 归因
    return allow(...);
}
```

注意 `isSensitiveField` 做的是**归一化匹配**：`User.password_hash`、`PassWord`、`user-pwd` 都会被 `alnum()` 洗成含 `password`/`pwd` 的形式拦下来——攻击者换大小写、换分隔符都没用。

## 千万别只写顺风测试

写完守卫，最容易犯的错就是只测「拦了多少攻击」。我在这套实现里坚持放 12 条**合法对照**：

- `customer_name` 前缀搜索 50 次 → 放行（业务正常搜索）
- `password` **等值**单次查询 → 放行（不是枚举）
- `/allowed/root/reports/cost.csv` 写文件 → 放行
- allowlist 端点的外发 → 放行
- `git status` 这样的安全 shell → 放行

如果只测攻击，你会把正常业务一起打死还不自知。**「不得劣于旧基线」**也是硬要求：旧逻辑（信 `readOnlyHint` 或「无副作用就放行」）会放过的 16 条攻击，新守卫必须全拦下。最终自检是 `27 attack + 12 control + 4 boundary = 43 断言全绿、0 误杀`。

## 给 Java 团队的一句话

SnapLogic 11-17 刚发布 Discovery Hub（MCP 中央注册 + 治理 + 可观测），IBM ContextForge、AWS AgentCore、Kong、Bifrost 都在把 MCP 治理做成标准层——但清一色不是 Java 栈，治理锁在各自的付费墙或平台里。

如果你手里是一堆 Java/Spring 的存量业务系统，又想安全地接进 agent，那么差异化不在「再造一个网关」，而在于：

1. 用 MCP 把存量 Java 系统**安全**包装成工具（复用已有 SSO、权限、审计）；
2. 在工具调用链路上做**六段式确定性执行门**，每一段都能现场验收；
3. 把「回传什么、写到哪、连到哪、拼成什么命令」这第六段补上——**这是目前最没人防守、也最容易出事的一段**。

> 项目开源地址：`github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise`
> 本期完整情报与技术清单见 `docs/earnings-report-2026-11-17.md` 与 `docs/result-field-boundary-2026-11-17.md`。

---

**诚实声明**：文中第三方 CVE（REBUILD / AWS / IBM / Python SDK / code-review）本框架均不受直接影响，参考实现在 `examples/` 层、未接入主模块；示例中的敏感字段检测为词法归一化匹配非语义解析，生产需配白名单；升级官方 SDK ≠ 安全。
