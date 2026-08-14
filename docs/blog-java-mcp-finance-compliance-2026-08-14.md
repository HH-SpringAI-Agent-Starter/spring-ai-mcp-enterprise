# 金融 Agent 的合规与风控底座：用 Java 打造企业级 MCP Server 的合规日历与风险评分工具

> 作者：HH-SpringAI-Agent-Starter | 日期：2026-08-14 | 适合发布：掘金 / CSDN / InfoQ
> 关键词：MCP Server、Java、Spring Boot、Spring AI、金融合规、风险评分、智能体

---

## 一、为什么金融行业最需要"企业级" MCP

IDC 最新数据显示，中国企业级 AI 智能体市场 2025 年已达 212 亿元，2026 年预计增至 449 亿元。在所有行业中，**金融是最特殊的一个**：它不仅需要智能体"能干"，更需要"可追溯、可审计、守规矩"。

当投研机器人调用一个财务指标计算工具时，它需要回答三个问题：
1. 这个工具是谁提供的？权限边界在哪？（**身份与授权**）
2. 每次调用留下了什么记录？（**审计日志**）
3. 工具的输入输出是否符合监管要求？（**合规可解释**）

这正是"企业级 MCP Server"与"个人 MCP Server"的分水岭。上篇文章我们讲了金融 Agent 的算力底座，今天落地两件具体的事：**合规日历**和**风险评分**。

## 二、合规日历：让 Agent 懂"披露窗口"

A 股上市公司有严格的定期报告披露时限：年报 4 月 30 日前、中报 8 月 31 日前、三季报 10 月 31 日前，加上业绩预告/快报等节点。投研 Agent 如果不懂这些窗口，给出的建议就可能踩监管红线。

在 MCP Enterprise Server 中，我们以 SPI 方式新增 `FinanceComplianceExecutor`（工具名 `finance_compliance`），支持两种操作：

**① 生成指定月份合规日历**——输入 `year + month`，返回该月全部披露节点：

```json
{
  "action": "calendar",
  "year": 2026,
  "month": 8
}
```

返回：中报业绩预告截止（7/15）、中报法定披露截止日（8/31）等结构化事件清单，Agent 可直接消费。

**② 查询报告期披露截止日**——输入 `period`（annual/q1/interim/q3），返回截止日与剩余天数：

```json
{
  "action": "deadline",
  "year": 2026,
  "period": "annual"
}
```

返回：`disclosureDeadline: 2026-04-30`、`daysRemaining: N`、`overdue: false`。

## 三、风险评分：五维加权，一个数字说话

`FinanceRiskExecutor`（工具名 `finance_risk`）把信贷审批、投顾风控最关心的五个维度合成 0-100 风险分：

| 维度 | 权重 | 关键输入 |
|------|------|----------|
| 偿债风险 | 30% | 资产负债率 |
| 流动性风险 | 20% | 流动比率 |
| 盈利风险 | 20% | 净利率 |
| 成长风险 | 15% | 营收增速 |
| 现金流风险 | 15% | 经营现金流/净利润 |

```json
{
  "action": "score",
  "params": {
    "debtRatioPct": 85, "currentRatio": 0.6,
    "netMarginPct": -8, "revenueGrowthPct": -20,
    "ocfToNetProfit": -0.5
  }
}
```

返回 `riskScore: 91.2`、`riskLevel: 高风险`，以及每个维度的分项得分与输入回显——**大模型可以直接把这份结构化结果转述给用户，并附上可解释的分项依据**。同时提供单维度 `diagnose` 模式，快速给出某一维度的风险结论与建议。

## 四、企业级 MCP 的工程范式（为什么用 Java/Spring）

这两只工具不是孤立的 `main()` 脚本，而是挂在企业级框架上的插件：

- **SPI 扩展**：实现 `McpToolExecutor` 接口 + `@Component` 即注册，注册中心自动发现
- **安全内置**：每个工具声明 `requiredRoles`（如 `admin,user`），RBAC 拦截在先
- **审计内置**：每次调用自动落审计日志，满足"可追溯"
- **限流内置**：声明 `rateLimitPerSecond`，防止 Agent 高频调用打爆后端
- **超时控制**：声明 `timeoutMs`，保障长任务不拖垮调用方

```java
@Component
public class FinanceRiskExecutor implements McpToolExecutor {
    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
            "finance_risk", "风险评分", "五维财务风险综合评分",
            "finance", "1.0.0", null, true,
            "admin,user", 5000, 20,
            schema, null);
    }
    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> params) {
        // 纯函数式实现，无副作用，天然可测试
    }
}
```

配合 Spring AI Alibaba（DashScope/DashVector），Java 开发者可以一条龙打通"工具定义 → 注册 → 安全管控 → 被 LLM 调用"。

## 五、给 Java 开发者的机会

2026 年 MCP 已不是新鲜事，但**"企业级 MCP 服务化"供给严重不足**：大厂在做平台（飞书 aily、阿里云 One Key MCP），企业需要自建或采购底座。而国内 MCP 生态以 Python/Node 为主，**Java + Spring + AI 的组合恰恰是稀缺供给**——这正是开源项目 [spring-ai-mcp-enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise) 的定位。

如果你正在做金融/制造行业的 Agent 项目，欢迎来仓库看看金融场景模板（财务指标、合规日历、风险评分），或者提 PR 共建下一个行业模板。

---

**延伸阅读**
- [金融 Agent 的"算力底座"：为什么投研机器人需要企业级 MCP Server（Java 版）](blog-java-mcp-finance-2026-08-13.md)
- [架构说明](architecture.md) | [快速上手](quickstart.md) | [API 文档](api-docs.md)
