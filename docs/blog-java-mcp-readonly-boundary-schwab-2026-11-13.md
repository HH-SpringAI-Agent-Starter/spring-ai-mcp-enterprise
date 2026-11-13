---
title: "read_only 不是安全边界：一个 MCP Server 4 天 7 个 CVE，以及嘉信理财为什么坚持每笔交易人工确认"
date: 2026-11-13
tags: [MCP, Java, SpringAI, SpringAIAlibaba, 安全, Agent, 数据库, HITL, 企业架构]
categories: [后端, AI 工程化]
---

# read_only 不是安全边界：一个 MCP Server 4 天 7 个 CVE，以及嘉信理财为什么坚持每笔交易人工确认

> 适合发布到掘金 / CSDN / 公众号。关键词：MCP Server 安全、read_only 绕过、
> pgEdge CVE-2026-126196、Spring AI Alibaba、Java Agent 治理、Human-in-the-loop。

## 先说结论

2026 年 11 月 10 到 12 日，**pgEdge Postgres MCP Server 被连续报出 7 个 CVE**，
最高 CVSS 8.8。同一个产品、同一个根因、同一个修复版本 1.1.0。

根因不是「忘了做鉴权」，而是把 **`read_only` 当成了一道边界**：

> 工具注册时写 `readOnlyHint=true`，连接串上带 `read_only`，客户端按语句
> **第一个关键字**判断要不要弹确认框——这三件事都只描述了「期望」，
> **没有一件真的拦住过 SQL**。

同一周，管理 **13.33 万亿美元**资产的嘉信理财（Charles Schwab）上线了自家的
MCP Server，把 AI 从「只读看看组合」推进到「帮你准备并提交交易」——
**但每一笔交易仍然强制人工确认**，官方脚注还写着「Charley 可能给出不准确或
不完整的结论，请自行核验」。

这两件事看起来一攻一守，其实是同一句话：
**MCP 赢下了「连接」这一层，输掉的是「治理」这一层；而治理层唯一的兑现方式，
是把控制点放到语句真正执行之前。**

---

## 一、7 个 CVE，拆开看只有 4 类绕过

| CVE | CVSS | 绕过了什么 |
| --- | --- | --- |
| `CVE-2026-126196` | 8.1 | `read_only` 连接上，未识别语句经 `pgx Exec` 用**简单查询协议**发出 → `SELECT 1; UPDATE ...` 分号堆叠；还能改事务/会话状态，**池化连接的状态残留让后续调用变可写** |
| `CVE-2026-126197` | 8.8 | custom `pl-do` 工具把 JSON 参数插值进固定 dollar-quote 标签的 `DO` 块，参数里带 `$mcp_custom_tool$` 或 `$mcp_args$` 就能提前闭合块、注入任意 SQL |
| `CVE-2026-126199` / `126198` | 6.8 / 3.1 | CLI 和 web client **只按语句首个关键字**分类就决定安全策略与是否弹写确认 |
| `CVE-2026-125322` / `125321` / `125320` | 6.5 / 6.5 / 5.3 | `where` 参数接受了**声明表以外**的子查询；HTTP 身份取**最左 XFF**；不校验 `Origin` 导致 DNS rebinding 读库 |

同一周，官方 `modelcontextprotocol/servers` 也吃了 5 个 GHSA，其中一个特别值得
后端同学看：

> `GHSA-6hqx-gqrx-c5r2`（7.8）：`mcp-server-git` **标为只读**的工具在处理仓库时
> 信任了仓库自带的 `.git/config`，`core.fsmonitor` 被触发 → **任意命令执行**。

**只读工具，执行了代码。** 这不是 SQL 注入，是「只读」这个语义本身被误用了：
它描述的是「我不改用户数据」，不等于「我不会加载不可信的可执行配置」。

另外 `GHSA-q74p-4wrc-q7w6` 是对 10 月那个洞的**修复不完整**——
`objects/info/alternates` 与 linked-worktree `common_dir` 混淆又绕回了
`--repository` 限制。这和上周 AWS 注册中心双 CVE 那句「**打补丁 ≠ 清干净**」
是同一条证据链。

---

## 二、Java 侧怎么落地：先把「声明」和「执行」拆开

最容易犯的错，是把 `readOnlyHint` 直接当权限用：

```java
// ❌ 反例：声明层当边界
@McpTool(name = "query_database", readOnlyHint = true)
public ResultSet query(String sql) {
    // 以为注解会兜底，直接把模型给的 SQL 丢给连接池
    return jdbcTemplate.queryForList(sql);
}
```

正确的形状是：**注解一个字节都不参与授权**，由独立的执行层门裁决。
下面是可以直接 `javac` 编译的核心骨架（完整版见文末仓库）：

```java
package com.example.mcp.readonly;

import java.util.*;
import java.util.regex.*;

/** 只读执行层强制门：声明层字段完全不参与授权判定。 */
public final class ReadOnlyGate {

    /** 声明层：只用于展示 / 文档，enforce() 里不读它。 */
    public static final class Claim {
        public boolean readOnlyHint = true;          // 不参与判定
        public String connectionLabel = "read_only"; // 不参与判定
        public String toolName = "query_database";
    }

    /** 执行层：默认值 = 所有控制点均已就位。 */
    public static final class Ctx {
        public String sql;
        public boolean paramsBound = true;         // 强制参数绑定
        public boolean serverReadOnlyTx = true;    // 服务端下发 SET TRANSACTION READ ONLY
        public boolean sessionReset = true;        // 连接归还前 RESET / DISCARD ALL
        public boolean originTrusted = true;       // Origin 白名单 + Host 一致
        public boolean envSanitized = true;        // 不加载仓库自带可执行配置
    }

    private static final Set<String> WRITE = Set.of(
        "INSERT","UPDATE","DELETE","DROP","ALTER","CREATE","TRUNCATE","VACUUM",
        "GRANT","REVOKE","COPY","DO","CALL","EXECUTE","SET","RESET","DISCARD",
        "BEGIN","COMMIT","ROLLBACK","INTO","PRAGMA","ATTACH","DETACH","ANALYZE");
    private static final Set<String> HEADS = Set.of(
        "SELECT","WITH","VALUES","TABLE","SHOW","EXPLAIN");
    private static final Pattern WORD = Pattern.compile("[A-Z_]+");

    public static String enforce(Claim claim, Ctx c) {
        // 声明层一行都不读，先 fail-closed 校验工具注册
        if (claim == null || !"query_database".equals(claim.toolName))
            return "DENY(UNREGISTERED_TOOL)";

        String clean = normalize(c.sql);           // ① 词法归一化
        if (clean == null) return "DENY(MALFORMED_STATEMENT)";
        if (clean.split(";", -1).length > 1 && !clean.trim().endsWith(";"))
            return "DENY(MULTI_STATEMENT)";        // ② 禁止堆叠
        if (clean.contains("$mcp_custom_tool$") || clean.contains("$mcp_args$"))
            return "DENY(DOLLAR_QUOTE_ESCAPE)";    // ③ 模板标签逃逸

        Matcher m = WORD.matcher(clean.toUpperCase(Locale.ROOT));
        Set<String> tokens = new HashSet<>();
        String head = "-";
        while (m.find()) { if ("-".equals(head)) head = m.group(); tokens.add(m.group()); }
        for (String v : WRITE) if (tokens.contains(v))
            return "DENY(WRITE_VERB_" + v + ")";   // ④ 全文扫描，不看首关键字
        if (!HEADS.contains(head)) return "DENY(NON_READ_HEAD_" + head + ")";

        // ⑤ 执行层五个独立控制点，任一失效即拒绝
        if (!c.paramsBound)       return "DENY(FORCE_BIND_REQUIRED)";
        if (!c.serverReadOnlyTx)  return "DENY(NO_SERVER_READONLY_TX)";
        if (!c.sessionReset)      return "DENY(POOLED_SESSION_NOT_RESET)";
        if (!c.originTrusted)     return "DENY(ORIGIN_UNTRUSTED)";
        if (!c.envSanitized)      return "DENY(TOOL_LOADS_UNTRUSTED_REPO_CONFIG)";
        return "ALLOW(READ_ONLY_ENFORCED_AT_EXECUTION_LAYER)";
    }

    /** 剥离行/块注释、单引号字符串、双引号标识符、dollar-quote 后再分类。 */
    private static String normalize(String s) {
        if (s == null || s.length() > 16 * 1024) return null;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length();) {
            char c = s.charAt(i);
            if (c == '-' && i + 1 < s.length() && s.charAt(i + 1) == '-') {
                i += 2; while (i < s.length() && s.charAt(i) != '\n') { out.append(' '); i++; }
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                if (end < 0) return null;
                out.append("  "); i = end + 2;
            } else if (c == '\'') {
                i++; out.append(' ');
                while (i < s.length() && s.charAt(i) != '\'') { out.append(' '); i++; }
                if (i >= s.length()) return null;
                i++; out.append(' ');
            } else { out.append(c); i++; }
        }
        return out.toString();
    }
}
```

> 上面是**教学骨架**，只做词法归一化和控制点示意。生产版请直接用仓库里的
> `McpReadOnlyEnforcementGate.java`（含 dollar-quote 完整解析、`$$` 无标签块、
> 双引号标识符、9 条合法只读对照与「不得劣于旧基线」断言）。

### 为什么必须写「对照组」

跑一下完整版：

```bash
javac -encoding UTF-8 -d out examples/read-only-enforcement/McpReadOnlyEnforcementGate.java
java -Dfile.encoding=UTF-8 -cp out com.example.mcp.readonly.McpReadOnlyEnforcementGate
# [McpReadOnlyEnforcementGate] self-test OK
# (22 attack + 9 control + 8 boundary = 79 assertions; 20 incremental blocks over legacy baseline)
```

**只演示「拦住了 22 条攻击」是不可信的**——把全部请求拒绝掉就能做到。
真正难的是同时让这 9 条合法查询全部放行：

```sql
SELECT 'DROP TABLE t; DELETE FROM t' AS literal   -- 字符串里全是关键字，不能误杀
SELECT 'a;b' AS literal                            -- 字符串里的分号不是语句分隔
SELECT /* inline hint */ count(*) FROM t           -- 内联注释
WITH recent AS (SELECT * FROM orders) SELECT * FROM recent  -- 只读 CTE
SELECT $$a; b; UPDATE nothing$$ AS body            -- dollar-quote 字符串
```

写代码时我就被自己的断言抓到过两次：一次是只查首关键字让 CTE 里的 `UPDATE`
溜过去，一次是把字符串里的分号算成了堆叠语句。**只看顺风测试会选错方案**——
这条比任何一个 CVE 编号都值钱。

---

## 三、Spring AI / Spring AI Alibaba 里放在哪一层

用 Spring AI Alibaba 接 MCP 时，别把这类校验塞进 `@Tool` 方法体内
（那是**声明层**，模型可以绕开工具直接调别的路径）。正确位置是**执行链上**：

```
Client (Claude / 通义 / 自研 Agent)
   │  MCP tools/call
   ▼
[mcp-gateway]  ← ① Origin/身份/限流/请求体上限（4 MiB）
   │
   ▼
[mcp-registry] ← ② 工具注册期注解完整性 + 审批门（V1.69）
   │             ③ 管理面自防护 + 存量全量复验（V1.70）
   ▼
[只读执行门]   ← ④ 词法归一化 + 强制绑定 + 只读事务 + 会话复位（V1.71）★ 本文
   │
   ▼
[JDBC / 最小权限只读角色]  ← ⑤ 数据库自己的最后一道闸（不可省）
```

要点三条：

1. **数据库角色是主闸，不是可选项**。应用层文法门是第二道闸，两层都要有。
   `SET TRANSACTION READ ONLY` 由服务端下发，不接受客户端参数。
2. **审批门放在执行前，不是执行后**。嘉信理财的做法是「模型可以准备交易，
   但提交必须人点确认」；Upwork 官方 MCP Server 的 proposal 也是
   `draft-and-confirm`（草稿直到人工确认才发出）。
3. **审计要能导出**。Swift Media 11-13 的采购周报已经把「集中式工具注册表 /
   按 agent 的 scope / 对检索内容做提示注入过滤 / **可导出的审计日志**」写成
   RFP 必选项了。只给 dashboard 不够，得给原因码 + 调用方 + 时间 + 脱敏凭据。

一个反直觉但很好用的销售/架构话术（来自 Upwork 接单指南原文）：

> **别承诺「全自动」。说清楚哪一步由人批准，反而更让人放心。**

---

## 四、对 Java 工程师意味着什么（招聘与钱）

本期抓到的岗位几乎都不是「MCP 工程师」这个 title，而是把 MCP 当作
**Java 平台工程的一个技能**：

- **海康威视**（西安，5 年+）：MCP Server 与 Skills 设计开发，把业务 API/数据库/
  知识库封装成工具，**并设计权限控制、安全护栏及高风险操作的人工确认(HITL)**；
  明确要求 Spring AI / LangChain4j / **Spring AI Alibaba**。
- **法本信息**（杭州）：**「必须深刻理解并熟悉 MCP 协议，具备基于 Java 扩展和
  开发 MCP Server」**。
- **智慧树**：基于 Spring AI 构建 Text-to-SQL、RAG、**MCP Server**。
- **中软环球**（北京）：大模型网关管理后台 + **开发 MCP 系列服务，适配内网 AI 编码工具**。

也就是说，值钱的不是「会封装一个 MCP Server」，而是：
**能不能把存量 Java 系统的权限、审计、事务边界，平移到 Agent 调用链上。**
这正是 CSDN 11-12 那篇万字长文的结论——「**先把身份、最小权限、工具级审批、
不可篡改日志这四件事做在接入前面**」。

⚠️ 顺带提醒：网上流传的「MCP 岗位 +217%」「初级 40–60 万年薪」口径不明，
别拿来做决策。能核的只有 BLS 软件开发者中位 $135,980（2025-05，官方大类）
和英国 ITJobsWatch 半年 123 个提及 MCP 的永久岗（去年同期 11 个）——
**是技能扩散，不是独立岗位溢价**。

---

## 五、收口

这 18 个月 MCP 走完了别的标准十年走不完的路：Anthropic 开源、OpenAI 倒戈、
捐给 Linux 基金会、无状态规范冻结、SDK 月下载从 9,700 万涨到 4 亿+。
**连接层已经没得选了。**

剩下的差价全在治理层：

> 标准赢了，治理输了，中间的差价就是 2026 年剩下两个月所有 MCP 安全创业公司的订单。

而治理层能不能被买单，取决于一件很朴素的事：
**采购方问「你说只读，是谁保证的」时，你能不能当场编译、当场跑、
当场给他看 22 条攻击被拦、9 条正常查询没被误杀。**

---

### 参考与免责

- 漏洞：[FixTheVuln AI IDE & MCP 披露追踪](https://fixthevuln.com/ai-ide-mcp-disclosures.html)、
  [NVD CVE-2026-126196](https://nvd.nist.gov/vuln/detail/CVE-2026-126196)、
  [CVE-2026-126197](https://hackerfeeds.com/cve/CVE-2026-126197)
- 金融：[RIABiz 嘉信理财 11-13](https://riabiz.com/a/2026/11/13/schwab-surprises-analysts-with-extra-rapid-launch-of-own-mcp-servers-also-leaping-from-read-only-to-agentic-get-stuff-done-ai-it-leaves-rias-out-for-now-and-makes-anthropics-days-numbered-as-schwabs-exclusive-llm)
- 治理：[CSDN《MCP 统一了连接，治理真空却同步扩大》11-12](https://blog.csdn.net/summerliyang/article/details/170562593)、
  [Swift Media 采购周报 11-13](https://swiftmedia.ca/blog/ai-headlines-for-business-november-13-2026)
- ⚠️ 本文示例为**参考实现**，未接入任何主模块，不替代数据库自身权限模型；
  引用的 CVE 不影响本项目（本项目不内置 pgEdge / 官方参考 server 实现）；
  全部岗位与报价为公开招聘/挂牌信息，非成交价。

**完整代码与 12 项验收清单**：`spring-ai-mcp-enterprise`（GitHub:
HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise），
见 `examples/read-only-enforcement/` 与 `docs/readonly-execution-boundary-2026-11-13.md`。
