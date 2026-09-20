# MCP Governance 治理模块指南（V1.26）

> 一句话：**在企业 MCP Server 上加一道「人类在环（Human-in-the-Loop）」安全闸门** ——
> 高风险工具（删库、转账、发信、部署）默认**不能由 Agent 直接执行**，
> 必须先经过管理员审批；所有判定与调用都带**风险分级**与**敏感数据脱敏审计**。

本模块对齐 **OWASP MCP Governance & Risk Project** 的五级风险模型，
并直接回应企业采购中最常被问的一句话：**"Agent 调用我们数据库/财务工具，谁来负责？"**

---

## 1. 为什么需要 Governance

| 没有治理的 MCP Server | 有治理的 MCP Server |
|---|---|
| Agent 拿到 API Key 就能调 `finance_transfer` | 转账工具等级 T4 → 自动进入人工审批队列 |
| 审计日志记录完整参数（含手机号/身份证/密钥） | 审计前先脱敏：`138****8000`、`apiKey=***` |
| 工具删库失败 = 不可逆事故 | `db_delete` 命中 deny-tiers 直接拒绝，物理不可达 |
| "谁批准的这次调用？" 无答案 | 每个审批请求有 id/审批人/时间/理由/一次性令牌 |

**Fail-closed 原则**：配置歧义或审批服务不可用时，默认**拒绝**而非放行。
状态机不允许静默覆盖——非法流转（对已批准记录再批准）直接抛异常。

---

## 2. 核心概念

### 2.1 风险分级（T0–T4，对齐 OWASP 五级模型）

| 等级 | 含义 | 典型工具 | 默认策略 |
|---|---|---|---|
| T0 | 公开数据只读 | 天气 / 汇率 | 放行 |
| T1 | 内部数据非敏感读 | 搜索 / 目录列举 | 放行 |
| T2 | 敏感数据读 | CRM / 用户 / 财务指标查询 | 放行（默认等级） |
| T3 | 写操作 / 有副作用 | 创建工单、发邮件、退款 | **需人工审批** |
| T4 | 特权 / 关键操作 | 删库、权限变更、部署、转账 | **需人工审批** |

工具等级判定优先级（高 → 低）：

1. **显式配置** `tool-tiers`（生产建议必配，最可靠）；
2. **分类语义**：`ToolDefinition.category`（system→T3，finance→T2，search→T1…）；
3. **关键词启发式**：工具名含 `delete/drop/truncate` → T4；`create/update/transfer` → T3；
   含 `password/payment/financ` → T2；含 `get/list/query` → T1；
4. **默认等级** `default-tier`（安全默认 T2）。

### 2.2 审批生命周期

```
PENDING ──approve──▶ APPROVED ──consume──▶ CONSUMED（一次性，防重放）
   │                    │
   ├─reject──▶ REJECTED  └─ 工具不一致/调用方不一致/过期 → 校验失败，不消费
   └─过期──▶ EXPIRED（15 分钟默认，sweep 惰性清理）
```

**一次性令牌**：批准后调用方在重试请求中携带 `X-MCP-Approval-Id: <id>`，
校验通过即消费置为 `CONSUMED`——同一个审批令牌**不能**用于第二次调用（防重放）。

### 2.3 敏感数据脱敏

- **内置规则**（按固定顺序）：邮箱 → 密钥类键值（apiKey/secret/password/token/credential）→
  身份证（18 位）→ 银行卡（16–19 位）→ 手机号（1[3-9]xxxxxxxxx）；
- **Map 键名识别**：参数里 `{"password": "hunter2"}` 这种整键打码 → `{"password": "***"}`；
- 审批队列与审计事件中的参数**全部为脱敏后内容**；
- 关闭方式：`mcp.enterprise.governance.redaction.enabled=false`（不推荐）。

---

## 3. 快速开始

### 3.1 默认配置（灰度模式，零侵入）

```yaml
mcp:
  enterprise:
    governance:
      enabled: true        # 模块总开关
      enforce: false       # 灰度模式：只登记风险与审批请求，不拦截调用
      require-approval-tiers: [T3, T4]
      deny-tiers: []
```

`enforce=false` 时，治理过滤器**不拦截任何调用**，
但会：登记每个 tools/call 的等级判定、把 T3/T4 工具调用写入审批队列、输出审计事件。
**建议先在灰度模式跑一周**，确认分级准确后切 `enforce=true`。

### 3.2 开启强制模式

```yaml
mcp:
  enterprise:
    governance:
      enforce: true
      tool-tiers:                    # 生产必配：显式覆盖比启发式更可靠
        finance_transfer: T4
        db_execute: T4
        user_create: T3
        weather_get: T0
      approval:
        enabled: true
        ttl-seconds: 900             # 审批有效期 15 分钟
```

### 3.3 完整调用闭环

```bash
# 1) Agent 调用高等级工具 → 返回 approval_required（HTTP 200，JSON-RPC 错误码 -32092）
curl -s -X POST http://localhost:8080/api/mcp/message \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"finance_transfer","arguments":{"to":"acct_2","amount":100000}}}'

# 响应：
# {"jsonrpc":"2.0","id":1,"error":{"code":-32092,"message":"工具等级 T4 需人工审批后执行",
#   "data":{"approvalId":"7f2c…","tier":"T4","status":"PENDING",
#           "expiresAt":"2026-09-17T13:45:00Z","approvalHeader":"X-MCP-Approval-Id"}}}

# 2) 管理员审批
curl -s -X POST http://localhost:8080/api/admin/governance/approvals/7f2c…/approve \
  -H "Content-Type: application/json" \
  -d '{"decidedBy":"ops-lead","reason":"月度对公转账，业务单已确认"}'

# 3) Agent 携带审批令牌重试 → 放行并执行
curl -s -X POST http://localhost:8080/api/mcp/message \
  -H "Authorization: Bearer <token>" \
  -H "X-MCP-Approval-Id: 7f2c…" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"finance_transfer","arguments":{...}}}'

# 4) 审计复核
curl -s http://localhost:8080/api/admin/governance/audit?limit=10
curl -s http://localhost:8080/api/admin/governance/stats
```

---

## 4. 管理 REST API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/governance/approvals?status=PENDING` | 审批列表（可按状态过滤） |
| GET | `/api/admin/governance/approvals/{id}` | 审批详情 |
| POST | `/api/admin/governance/approvals/{id}/approve` | 批准 `{decidedBy, reason}` |
| POST | `/api/admin/governance/approvals/{id}/reject` | 拒绝 `{decidedBy, reason}` |
| GET | `/api/admin/governance/stats` | 审批统计（total/pending/approved/rejected/expired/consumed） |
| GET | `/api/admin/governance/audit?limit=50` | 最近治理审计事件 |
| GET | `/api/admin/governance/policy` | 当前生效策略视图 |

> ⚠️ 与其它 `/api/admin/*` 一样，**必须**置于 mcp-auth / 网关鉴权之后，绝不能公网裸奔——
> 批准即授权执行高等级工具。

---

## 5. JSON-RPC 错误码

| 错误码 | 场景 | data 字段 |
|---|---|---|
| `-32092` | `approval_required`：需人工审批 | approvalId / tier / status / expiresAt / approvalHeader |
| `-32093` | `governance_denied`：命中 deny-tiers 硬拒 | errorCode / tier |

错误响应保持 **HTTP 200 + JSON-RPC error**（协议正确，MCP 客户端可正常解析）。

---

## 6. 模块结构

```
mcp-governance/
├── RiskTier                          # T0–T4 风险分级（OWASP 对齐，宽容解析 T3_WRITE/3）
├── McpGovernanceProperties           # 配置：mcp.enterprise.governance.*
├── McpToolRiskClassifier             # 工具分级：显式 > 分类语义 > 关键词 > 默认
├── GovernanceDecision                # 判定结果（ALLOW/REQUIRE_APPROVAL/DENY）
├── ApprovalRequest / ApprovalStore   # 审批模型 + 存储 SPI
├── InMemoryApprovalStore             # 有界内存实现（默认）
├── JdbcApprovalStore                 # V1.28: JDBC 实现（多实例共享审批状态，方言无关）
├── McpApprovalService                # 审批服务：创建/批准/拒绝/一次性消费/过期清理
├── McpGovernanceGuard                # 判定核心（Fail-closed）
├── SensitiveDataRedactor             # PII/密钥脱敏（递归 Map/List）
├── McpGovernanceAuditSink            # 审计出口 SPI（默认内存环形缓冲 + SLF4J）
├── McpGovernanceFilter               # JSON-RPC 入口过滤器（tools/call 判定）
├── McpGovernanceAdminController      # 管理 REST API（/api/admin/governance）
└── McpGovernanceAutoConfiguration    # Spring Boot 自动装配（灰度/强制开关 + store 选择）
```

## 7. 审批状态持久化（V1.28：store=jdbc）

### 7.1 为什么需要 JDBC

V1.26 的默认实现 {@code InMemoryApprovalStore} 把审批队列放在单个 JVM 内存里：
单实例演示没问题，但**企业多实例拓扑下 HITL 闭环会断**——
审批人批准了一条请求，Agent 重试时请求可能落到另一个实例，状态不可见。
另外，重启/滚动发布会清空审计与审批记录（合规取证要求留存）。

V1.28 新增 {@code JdbcApprovalStore}：审批请求持久化到单张表，
「创建 → 审批 → 消费」跨实例可见，且 `created_at/decided_at` 全程留痕。

### 7.2 启用方式

```yaml
mcp:
  enterprise:
    governance:
      approval:
        store: jdbc                 # memory（默认）| jdbc
        table: mcp_approval_requests # 可自定义表名
        init-schema: true           # 启动时幂等建表
```

自动配置行为：
- 应用 classpath 上有 `JdbcTemplate`（即已配置 DataSource）→ 自动创建 JDBC store 并建表；
- 配置了 `store=jdbc` 但没有数据源 → 打 WARN 并回退内存实现（进程不挂，可用性优先）；
- 未配置 → 维持 V1.26 内存行为（零依赖）。

### 7.3 表结构（方言无关）

单表 `mcp_approval_requests`，只使用 `CREATE TABLE IF NOT EXISTS` / `INSERT` /
`SELECT` / `UPDATE` 绑定参数，H2（测试）、MySQL/MariaDB、PostgreSQL、SQL Server 直接可跑：

```
id              VARCHAR(64)   NOT NULL PRIMARY KEY
 tool_name       VARCHAR(256)  NOT NULL
 tier_code       VARCHAR(16)   NOT NULL
 arguments       VARCHAR(4000)            -- 脱敏后参数 JSON
 requested_by    VARCHAR(256)
 reason          VARCHAR(1024)
 status          VARCHAR(16)   NOT NULL   -- PENDING/APPROVED/REJECTED/EXPIRED/CONSUMED
 created_at      BIGINT        NOT NULL   -- epoch millis
 expires_at      BIGINT        NOT NULL
 decided_at      BIGINT                   -- 可空
 decided_by      VARCHAR(256)
 decision_reason VARCHAR(1024)
```

### 7.4 一致性语义

- **读穿式**：`get/list` 每次查库，无本地缓存、无一致性窗口，天然多实例共享；
- **Fail-soft 写入**：单条写失败记 WARN 并抛给上层——审批是 Fail-closed 语义，宁可报错不可错放；
- **一次性令牌**：`CONSUMED` 状态落库，重放校验跨实例同样生效（防重放从「单机承诺」升级为「集群承诺」）。

## 8. 审计事件持久化（V1.29：audit.store=jdbc）

### 8.1 为什么需要落库

V1.26 默认审计只活在单个 JVM 内存里（有界环形缓冲 + SLF4J 日志），对企业合规场景有两个缺口：

- **审计轨迹丢失**：重启/滚动发布即清空，无法回答「上周谁在什么时候调过 finance_transfer？」；
- **多实例各自为政**：K8s 多副本下每条事件只存在于处理它的那个 Pod，审计员没有统一的全局视图。

审计日志是合规资产（取证、报表、SIEM 对接），「No logging = no production use」——
落库才谈得上审计。V1.29 新增 `JdbcGovernanceAuditSink`：每条治理判定事件持久化到单张表，
跨实例可见、重启不丢失，支持 SQL 查询与导出。

### 8.2 启用方式

```yaml
mcp:
  enterprise:
    governance:
      audit:
        store: jdbc                 # memory（默认）| jdbc
        table: mcp_governance_audit # 可自定义表名
        init-schema: true           # 启动时幂等建表
        log-to-slf4j: true          # 落库同时保留 SLF4J 日志（运维侧零改动）
```

自动配置行为：
- 应用 classpath 上有 `JdbcTemplate`（即已配置 DataSource）→ 自动创建 JDBC sink 并建表；
- 配置了 `store=jdbc` 但没有数据源 → 打 WARN 并回退内存实现（进程不挂，可用性优先）；
- 未配置 → 维持 V1.26 内存行为（零依赖，默认值不变）。

### 8.3 表结构（方言无关）

单表 `mcp_governance_audit`，只使用 `CREATE TABLE IF NOT EXISTS` / `INSERT` /
`SELECT` 绑定参数，H2（测试）、MySQL/MariaDB、PostgreSQL、SQL Server 直接可跑：

```
id          VARCHAR(64)   NOT NULL PRIMARY KEY   -- 事件 ID（时间戳+序号）
seq         BIGINT        NOT NULL               -- 进程内序号（同毫秒内排序 tiebreaker）
ts          BIGINT        NOT NULL               -- epoch millis
tool        VARCHAR(256)  NOT NULL
tier        VARCHAR(16)
caller      VARCHAR(256)
decision    VARCHAR(32)   NOT NULL              -- ALLOW / APPROVAL_REQUIRED / DENY...
approval_id VARCHAR(64)
arguments   VARCHAR(4000)                       -- 脱敏后参数 JSON（值级截断保证合法 JSON）
message     VARCHAR(1024)
```

### 8.4 一致性语义

- **只写不删**：审计日志是合规资产，本实现只 INSERT + SELECT，不提供 DELETE/UPDATE
  （防篡改；物理清理留给企业自己的保留策略任务）；
- **fail-soft 写入**：单条落库失败记录 WARN 并继续——审计是观察者，不是关键路径，
  绝不让审计链路打挂业务调用；
- **近实时双写**：落库的同时保持 SLF4J 输出（可关），grep 日志与 SQL 查询两路并存；
- **值级截断**：字符串参数截断到 400 字符再序列化，保证落库 JSON 始终合法可反序列化，
  不产生截断的半截 JSON。

### 8.5 查询示例

```sql
-- 近 24 小时所有审批类判定
SELECT ts, tool, tier, caller, decision, approval_id
FROM mcp_governance_audit
WHERE ts > (UNIX_TIMESTAMP() - 86400) * 1000
ORDER BY ts DESC;

-- 某个高等级工具的调用历史（合规取证）
SELECT * FROM mcp_governance_audit
WHERE tool = 'finance_transfer'
ORDER BY ts DESC;
```

管理面板仍可用 `GET /api/admin/governance/audit?limit=50`（JDBC 模式读取同一张表，
`count` 统计总数）。

## 9. 生产落地建议


1. **分类先行**：灰度模式观察一周，用 `/api/admin/governance/policy` + audit 校准 tool-tiers；
2. **审批人=真人**：审批端接入钉钉/企微/飞书审批流（ApprovalStore 换实现或轮询 admin API）；
3. **审计入仓**：`McpGovernanceAuditSink` 换成 Kafka/JDBC，满足安全团队取证需求；
4. **与 Scope 配合**：V1.19 工具级 Scope 管「谁有权限调」，Governance 管「调了要不要人批」，
   两者叠加 = 身份权限 + 高风险行为双重防线；
5. **deny-tiers 常开**：`delete/truncate/drop` 类工具建议直接进硬闸门，不给审批机会。

---

*关联文档：[架构说明](architecture.md) · [安全审查清单](security-review-checklist.md) · [V1.26 发布说明](V1.26-release-notes.md) · [V1.28 发布说明](V1.28-release-notes.md) · [V1.29 发布说明](V1.29-release-notes.md)*