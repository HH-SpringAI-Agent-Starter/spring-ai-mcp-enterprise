# 工具级 Scope 授权集成指南（V1.19）

> 版本：V1.19 ｜ 主题：OAuth2 Token Scope → MCP 工具级权限（Tool ACL）
> 相关模块：mcp-core（ScopeMatcher/ToolScopePolicy）、mcp-server（双通道强制）、mcp-spring-boot-starter（自动装配）

---

## 1. 解决的问题

MCP Server 暴露多个工具（财务计算、数据库查询、系统信息……），不同客户端/服务账户不应拥有相同权限：

- 只读分析 Agent → 只能调 `tools:finance:read` 类工具
- 运营机器人 → 只能调数据库查询工具
- 管理员 → 全部

V1.19 之前：令牌带 `scope` claim（V1.8 起），但 scope 不约束工具调用——拿到令牌即可调所有工具。
V1.19 之后：**工具声明所需 scope，令牌 scope 不足 → 403 `insufficient_scope`（RFC 6750），且执行器零调用**。

## 2. 快速上手（3 步）

### Step 1：开启策略

```yaml
# application.yml
mcp:
  enterprise:
    security:
      scope:
        enabled: true
        category-defaults:
          finance: "tools:finance:*"
          database: "tools:database:*"
          search: "tools:search:*"
        tool-overrides:
          system_info: "tools:system:read"
```

### Step 2：工具声明所需 scope（二选一）

**方式 A：代码显式声明（工具作者，优先级最高）**

```java
@Component
public class FinanceIndicatorExecutor implements McpToolExecutor {
    @Override
    public ToolDefinition getDefinition() {
        ToolDefinition def = new ToolDefinition(
            "finance_indicator", "财务指标计算器", "...", "finance",
            "1.0.0", null, true, "admin,user", 5000, 20,
            Map.of("type", "object", "properties", properties,
                   "required", List.of("indicator", "params")), null);
        def.setRequiredScopes("tools:finance:read");   // V1.19: scope 级约束
        return def;
    }
}
```

**方式 B：配置文件覆盖（运维，无需改代码）**

```yaml
mcp:
  enterprise:
    security:
      scope:
        tool-overrides:
          finance_indicator: "tools:finance:*"   # 覆盖/兜底均可
```

优先级：**显式声明 > tool-overrides > category-defaults > 无约束放行**。

### Step 3：客户端拿受限令牌并调用

```bash
# 1) 申请只含 tools:finance:read 的令牌
curl -s -X POST http://localhost:8081/api/auth/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=mcp-service&client_secret=change-me-client-secret&scope=tools:finance:read"

# 2) 调金融工具 → 成功
curl -s -X POST http://localhost:8081/api/mcp/tools/finance_indicator/invoke \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"indicator":"cagr","params":{"beginValue":100,"endValue":200,"years":3}}'

# 3) 调数据库工具 → 403 insufficient_scope（token 无 tools:database:*）
curl -s -i -X POST http://localhost:8081/api/mcp/tools/db_query/invoke \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sql":"SELECT 1"}'
# → HTTP/1.1 403
# → WWW-Authenticate: Bearer realm="mcp-enterprise", error="insufficient_scope", ...
```

## 3. Scope 语法

| 语法 | 示例 | 匹配 |
| --- | --- | --- |
| 精确 | `tools:finance:read` | 仅该 scope |
| 单段通配 `*` | `tools:finance:*` | read / write / admin（同一资源段） |
| 多段通配 `**` | `tools:**` | finance:read、finance:compliance:report…… |
| 全匹配 | `*` | 任意 scope |
| 多模式 | `tools:finance:read,audit:view tools:report:**` | 空格或逗号分隔，任一命中即放行 |

> 段落按 `:` 切分；`*` 只匹配当前段，`**` 匹配当前段及以后所有段。

## 4. 行为矩阵

| 场景 | REST `/invoke` | Streamable HTTP `tools/call` | tasks/create |
| --- | --- | --- | --- |
| 策略关闭（默认） | 正常执行（V1.18 行为） | 正常执行 | 正常执行 |
| 无 Bearer 令牌（X-API-Key 路径） | 正常执行（scope 不拦截） | 正常执行 | 正常执行 |
| 令牌 scope 命中 | 正常执行 | 正常执行 | 入队执行 |
| 令牌 scope 不足 | **403** + `WWW-Authenticate` + 审计失败记录 | **403** + JSON-RPC `-32090` + `WWW-Authenticate` | **预检拒绝**（不入队） |
| 工具无约束 | 正常执行 | 正常执行 | 正常执行 |

**向后兼容设计：**
- `enabled` 默认 `false` → 存量部署零感知
- `ToolDefinition` 12 参构造签名不变，新增字段走 setter
- scope 拦截仅作用于"携带 Bearer 令牌"的调用方——旧版 X-API-Key 客户端不受影响

## 5. 客户端如何知道自己需要什么 scope

**策略启用后**，工具目录自带所需 scope 声明：

```bash
curl -s http://localhost:8081/api/mcp/v2/tools | jq '.result.tools[] | {name, requiredScopes}'
# → {"name":"finance_indicator","requiredScopes":["tools:finance:read"]}
# → {"name":"db_query","requiredScopes":["tools:database:*"]}

# 或查看全局策略
curl -s http://localhost:8081/api/mcp/scope/policy
```

**建议接入流程：** `tools/list` 读 `requiredScopes` → 令牌签发时按需申请 → 调用；被 403 时按 `error_description` 中的 requiredScopes 重新申请。

## 6. 与既有安全能力的关系

| 能力 | 定位 | 与 V1.19 协同 |
| --- | --- | --- |
| RBAC（requiredRoles） | 角色级粗粒度 | scope 提供**令牌级**细粒度，可叠加（AND） |
| RateLimit | 频率控制 | scope 决定"能不能调"，限流决定"调多快" |
| 多租户隔离 | 数据面隔离 | scope 决定"能调哪个工具"，租户决定"看到哪份数据" |
| 审计日志 | 证据链 | 403 拒绝同样落审计（who/what/when） |
| A2A 网关（V1.15–1.18） | 协议互通 | A2A 卡片暴露工具时同样受 scope 策略约束（经 MCP 调用链） |

## 7. 常见问题

**Q：为什么拒绝时不返回 401？**
A：401 = 身份无效/未认证；403 = 身份有效但权限不足。RFC 6750 明确规定 scope 不足用 403 + `insufficient_scope` 错误码。

**Q：令牌申请了多余 scope 有风险吗？**
A：服务端 `McpOAuth2Manager` 签发时按客户端已授权 scope 收敛（intersection），申请超出授权范围的 scope 会被静默裁剪。

**Q：可以按用户（而非客户端）区分 scope 吗？**
A：可以——`McpOAuth2Manager.TokenInfo` 携带 client owner；企业 IdP 场景下可挂 EMA `TokenIntrospector`，把企业侧 per-user scope 直接映射进 TokenInfo，网关统一执行。