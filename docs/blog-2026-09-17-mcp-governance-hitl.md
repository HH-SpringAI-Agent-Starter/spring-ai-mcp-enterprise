# 企业级 MCP Server 的「安全最后一块拼图」：人类在环审批（HITL）实战

> 标题备选：`MCP Server 要不要让人审批？企业安全评审必问的三句话，用 Java 一次答完`
> 发布渠道：掘金 / CSDN / InfoQ 社区
> 作者：HH-SpringAI-Agent-Starter 开源社区

---

## 一、先抛三个问题

企业采购 MCP 平台时，安全评审会上几乎**必问**三句话：

1. **「高权限工具——转账、删库、发信、部署——Agent 能直接调？」**
2. **「你们的审计日志里，会不会有手机号、身份证、API Key？」**
3. **「这次危险调用，到底是谁批准的？有记录吗？」**

如果答不上来，再炫酷的 Agent 平台也过不了关。
这不是假设——9 月上旬企业采购 MCP Server 的选型指南里，"写操作需要**明确审批/业务工作流**"、
"实时审计"、"幂等防重复执行"已经被列为**硬性验收项**；海外服务商甚至把
**Human-in-the-Loop checkpoint（人类在环检查点）**单独列为 $15K-$40K 档的服务能力。

结论：**治理能力 = 企业 MCP 的交付门槛，也是溢价点。**

## 二、什么是「人类在环审批」（HITL）？

核心思想一句话：**风险越高的工具，越不应该由 Agent 一言而决。**

我们把工具按副作用分成五档（对齐 OWASP MCP Governance & Risk Project 的风险模型）：

| 等级 | 含义 | 例子 | 默认策略 |
|---|---|---|---|
| T0 | 公开只读 | 天气、汇率 | 放行 |
| T1 | 内部非敏感读 | 搜索、列表 | 放行 |
| T2 | 敏感读 | CRM、用户、财务查询 | 放行 |
| T3 | 写/有副作用 | 建工单、发邮件、退款 | **需人工审批** |
| T4 | 特权/关键 | 删库、权限变更、部署、转账 | **需人工审批** |

调用链变成：

```
Agent → tools/call(finance_transfer)          [等级 T4]
        ↓ 拦截，返回 approval_required + approvalId
管理员 → POST /approvals/{id}/approve
        ↓ 颁发一次性令牌
Agent → tools/call(finance_transfer) + X-MCP-Approval-Id
        ↓ 校验通过 → 执行（令牌一次性，防重放）
```

关键设计是**一次性令牌**：同一个审批 ID 只能用一次，且校验工具名与调用方身份——
审批的是 `finance_transfer`，拿来调 `db_execute`？拒绝。

## 三、Fail-closed：宁可错杀，不可放行

治理模块最容易被攻击的点是**配置歧义**。我们的原则：

- 命中 deny-tiers（硬闸门）→ 直接拒绝，连审批机会都不给；
- 需审批但审批服务不可用 → **拒绝**（降级放行 = 安全事故）；
- 非法工具等级配置 → 警告 + 忽略该项，走启发式兜底，**绝不静默降级安全级别**；
- 模块总开关关闭 → 全部放行（向后兼容，灰度用）。

## 四、审计之前先脱敏

安全团队要审计日志，但日志里不能有明文 PII。内置脱敏规则按固定顺序执行：

```
邮箱 → apiKey/secret/password/token 键值 → 身份证(18位) → 银行卡(16-19位) → 手机号
```

Map 参数支持整键打码：`{"password": "***"}` → `{"password": "***"}`。
审批队列与审计事件里的参数**全部是脱敏后的**，日志可以放心导出给合规。

## 五、灰度优先：先登记，后强制

出厂默认 `enforce=false`：模块只做**登记 + 审批入队 + 审计**，**不拦截任何调用**。
企业在真实流量下跑一周，用策略视图核查分级是否准确，再一键 `enforce=true`。

## 六、Java 实现长什么样（Spring Boot 生态）

以我们的开源项目 Spring AI MCP Enterprise 的 V1.26 为例，一个模块解决：

```
mcp-governance/
├── RiskTier                   # T0–T4 分级（宽容解析 T3_WRITE / 3）
├── McpToolRiskClassifier      # 显式 > 分类语义 > 关键词 > 默认
├── McpApprovalService         # 创建/批准/拒绝/一次性消费/过期清理
├── McpGovernanceGuard         # 判定核心（Fail-closed）
├── SensitiveDataRedactor      # PII/密钥脱敏
├── McpGovernanceFilter        # JSON-RPC 入口过滤器
├── McpGovernanceAdminController # 管理 REST API
└── McpGovernanceAutoConfiguration # 自动装配
```

**28 个测试覆盖**：分级优先序、一次性消费防重放、工具/调用方不匹配、过期、sweep、
deny 硬拒、Fail-closed、脱敏递归、配置绑定。全仓 21 模块构建通过。

## 七、给读者的落地建议

1. **先定等级再看工具**：删库类直接进 deny-tiers，不给审批机会；
2. **审批人必须是真人**：接钉钉/企微/飞书审批流，别让另一个 Agent 批；
3. **审计入仓**：审计出口换成 Kafka/JDBC，满足取证；
4. **与权限体系叠加**：Scope 管「谁有权限调」，治理管「调了要不要人批」。

---

**开源项目**：spring-ai-mcp-enterprise（GitHub: HH-SpringAI-Agent-Starter 组织下）
V1.26 治理模块 + 完整中文文档 + 可运行示例，欢迎 Star / Issue / PR。