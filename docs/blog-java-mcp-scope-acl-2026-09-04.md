# 从"拿到令牌"到"能调哪个工具"：MCP 企业级 Scope 权限映射实战（Java/Spring Boot）

> 发表于：2026-09-04 ｜ 作者：HH-SpringAI-Agent-Starter 开源项目组
> 关键词：MCP、OAuth2、Scope、RBAC、Spring Boot、Spring AI、insufficient_scope、多租户
> 配套开源项目：`HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise`（V1.19）

---

## 一、问题：你的 MCP Server 可能"一钥通行"

做企业级 MCP（Model Context Protocol）Server 的同学都遇到过这个尴尬：

> 令牌体系做得很完善——OAuth2 client-credentials 签发、JWT 签名校验、RFC 7662 内省、吊销、刷新轮换……**但任何拿到合法令牌的客户端，都能调用服务器上的所有工具。**

财务计算工具、数据库查询工具、系统信息工具，全部裸奔在一个令牌之下。管理员和只读分析 bot 没有区别。

这在面试和投标里是致命的：当面试官问"你怎么实现 least-privilege（最小权限）"时，答案不能是"我们用了 OAuth2"——因为 **OAuth2 只回答"你是谁"，不回答"你能动什么"**。

本周海外 MCP 岗位的 JD 已经把这写进硬性要求：

- **Greelow**（$6,000–9,000/月）：*"per-user scoping"*、*"least-privilege scopes"*
- **Sumo Logic**（$207K–243K/年）：*"OAuth, token exchange, secrets management, and multi-tenant isolation"*
- **NTT DATA**：*"authorization checks"*、*"least-privilege controls"*、*"auth that survives a security review"*

一句话：**企业 MCP 的授权，必须细到"工具"这一级。**

## 二、方案：Token Scope → Tool ACL

思路很直接，把 OAuth2 里现成的 `scope` 概念用到底：

1. **令牌签发时**，客户端按需申请 scope（如 `tools:finance:read`）；
2. **工具声明时**，标注所需 scope（支持通配：`tools:finance:*`、`tools:**`）；
3. **调用执行前**，比对令牌 scope 与工具所需 scope → 不足返回 **403 `insufficient_scope`**（RFC 6750 §3.1），执行器零调用。

```
client-credentials 签发令牌（scope=tools:finance:read）
        │
        ▼
调 finance_indicator ── scope 命中 ──► 执行 ✅
        │
        ▼
调 db_query（需 tools:database:*）── scope 不足 ──► HTTP 403
        WWW-Authenticate: Bearer error="insufficient_scope"
```

### 通配符设计（企业 scope 的分层语法）

企业 scope 天然带资源层级，我们支持四种形态：

| 语法 | 示例 | 说明 |
| --- | --- | --- |
| 精确 | `tools:finance:read` | 资源:动作 |
| 单段通配 | `tools:finance:*` | finance 下任意动作 |
| 多段通配 | `tools:**` | tools 下任意层级 |
| 全匹配 | `*` | 不过滤 |

段落按 `:` 切分，`*` 只匹配当前段，`**` 匹配当前段之后的所有段。这样"财务分析 Agent 只能读财务数据"和"运维 bot 只能查系统信息"都能用一行 scope 表达。

### 解析优先级（运维友好）

1. **工具显式声明**（`requiredScopes` 字段）——最高优先级
2. **配置按工具名覆盖**（`tool-overrides`）——改配置即可，不用动代码
3. **配置按分类兜底**（`category-defaults`，如 `finance → tools:finance:*`）
4. 均未命中 → 无约束放行（**向后兼容**）

## 三、核心实现（Java 17 + Spring Boot 3.4）

### 1. 工具定义加一个字段，零破坏

工具定义类保持 12 参构造签名不变（28 个既有工具全部无感），新增字段走 setter 链：

```java
ToolDefinition def = new ToolDefinition(
    "finance_indicator", "财务指标计算器", "...", "finance",
    "1.0.0", null, true, "admin,user", 5000, 20,
    inputSchema, null);
def.setRequiredScopes("tools:finance:read");   // V1.19 新增
```

### 2. 通配匹配器（20 行核心逻辑）

```java
public static boolean matches(String tokenScope, String pattern) {
    if ("*".equals(pattern)) return true;
    String[] t = tokenScope.split(":");
    String[] p = pattern.split(":");
    int ti = 0;
    for (int pi = 0; pi < p.length; pi++) {
        if ("**".equals(p[pi])) return true;          // 多段通配：剩余全满足
        if (ti >= t.length) return false;
        if (!"*".equals(p[pi]) && !p[pi].equals(t[ti])) return false;
        ti++;
    }
    return ti == t.length;                             // 精确匹配不许 token 多段
}
```

### 3. 执行前强制（fail-closed）

```java
public Mono<Map<String, Object>> invokeWithScope(String name, Map<String, Object> params,
                                                 Set<String> tokenScopes) {
    if (policy == null || !policy.isEnabled() || tokenScopes == null) {
        return invoke(name, params);                   // 向后兼容路径
    }
    ScopeDecision decision = policy.authorize(tokenScopes, executor.getDefinition());
    if (!decision.allowed()) {
        return Mono.just(Map.of(
            "success", false, "error", "insufficient_scope",
            "requiredScopes", decision.requiredScopes(),
            "tokenScopes", decision.tokenScopes(),
            "httpStatus", 403));
    }
    return invoke(name, params);
}
```

### 4. 双通道都覆盖

| 通道 | 拒绝时的表现 |
| --- | --- |
| REST `POST /api/mcp/tools/{name}/invoke` | HTTP 403 + `WWW-Authenticate: Bearer error="insufficient_scope"` + 审计落库 |
| Streamable HTTP `tools/call` / `message` | JSON-RPC 错误码 `-32090` + HTTP 403 + WWW-Authenticate |
| 长任务 `tasks/create` | **scope 预检**，无权限任务不入队（fail-fast，避免白排队） |

### 5. 客户端自描述（关键体验）

策略启用后，`tools/list` 直接返回每个工具所需的 scope：

```json
{ "name": "finance_indicator", "requiredScopes": ["tools:finance:read"] }
{ "name": "db_query",          "requiredScopes": ["tools:database:*"] }
```

**客户端可以在令牌签发阶段就申请正确的 scope**，而不是被 403 打回来再猜。另有 `GET /api/mcp/scope/policy` 观察端点查看全局授权矩阵。

## 四、测试与回归

新增 26 个用例全绿（匹配器 8 + 策略 8 + 强制链路 10），覆盖：

- 拒绝时**执行器零调用**（`AtomicInteger` 计数断言）
- 通配边界（`tools:*:read` 中段通配、token 多段拒绝）
- 优先级（显式 > 覆盖 > 兜底）
- 旧签名调用不拦截（X-API-Key 路径无感）
- 全仓 17 模块 BUILD SUCCESS，128+ 既有测试零回归

## 五、给你的面试/投标话术

**"怎么保证 MCP 工具的最小权限？"**

> 我们的令牌体系从 V1.8 起就带 OAuth2 scope，V1.17 网关可强制 Bearer 校验（RFC 6750），V1.19 把 scope 落到**工具级 ACL**：工具声明所需 scope（支持 `*`/`**` 通配），执行前 fail-closed 比对，越权调用返回 403 `insufficient_scope` + `WWW-Authenticate` 头，同时写审计日志。而且默认开关关闭、12 参构造不变——存量客户端零迁移。

这背后是一条完整的能力进化线：**令牌带 scope（V1.8）→ 网关强制鉴权（V1.17）→ 工具级强制（V1.19）**，每一版都是上一版的自然延伸——这本身就是"架构演进有章法"的证明。

## 六、下一站

- **Upwork MCP Server 示例**（官方 API + OAuth）：职位扫描 + proposal 生成，做成变现入口
- **mcp.so / smithery 注册表提交**：把 A2A + OAuth2 + SSE + Signed Card + Scope ACL 特性集打标上线
- **30 秒 pitch 页**：面向 Greelow / NTT DATA / Commerzbank 类 JD 的 Java+MCP+OAuth2 合辑

---

*本文配套完整可运行实现与 26 个测试用例，见 [spring-ai-mcp-enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise)，欢迎 star 与 issue。*