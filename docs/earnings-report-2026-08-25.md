# Earnings Report 2026-08-25 — Spring AI MCP Enterprise

> 板块：企业 MCP Server 框架 | 版本：V1.10（增量推进中）| 主题：**Dify 集成 + 多租户预研 + MCP 岗位薪酬带雷达 + Registry 收录行动**

## 今天做了什么

### 1. 项目状态核验（防重复造轮子）
确认 V1.9 稳定；任务清单中 Alibaba 集成 / 客户端 SDK（Java/Python/Node/curl）/ CI / Docker 均已在历史版本完成，**今晚不做重复工作**，聚焦真正缺口。

### 2. Dify 集成示例（V1.10 候选 #2 落地）
- `docs/dify-integration-guide.md` — Dify 挂载本框架为 MCP 工具的分步指南（Streamable HTTP + Bearer 鉴权 + 生产注意事项）；
- `mcp-examples/dify/dify-mcp-tool.json` — Dify 自定义工具导入模板；
- `mcp-examples/dify/workflow-export.yml` — 企业数据问答 Agent 示例工作流；
- README 新增「Dify 工作流集成」小节。

### 3. 多租户隔离技术预研（V1.11 候选）
- `docs/multi-tenant-research-2026-08-25.md` — 三种隔离模型对比（Row-level / Schema / 实例）、与现有 OAuth2/EMA 模型的叠加设计、三步落地路径（V1.11→V1.13）、对报价单档位的解锁意义（企业平台 $25-60K 档）。

### 4. 市场雷达 2026-08-25（挣钱部分，web_search 一手数据）
- `docs/market-research-2026-08-25.md` — **MCP 已形成独立薪酬带**：
  - Anthropic MCP 工程师 $300K-560K；MCP Server Developer $150K-250K；Senior MCP Engineer $175K-220K；合同 $50-82/hr；
  - 在招企业：Anthropic / OpenHands（$170-275K 远程）/ Airbyte / Docker / Coinbase / Brex / ServiceNow / Mixpanel / Talan（欧洲可办签证）等；
  - Skillenai 量化：90 天 1,139 个 MCP 岗位；
  - **卖点**：Java+Spring+AI 组合 = 买方（企业 Java 存量）与卖方（Python 生态）错位 → 稀缺溢价；本项目功能矩阵（OAuth2/EMA/审计/限流）正好对位监管行业 MCP 岗硬技能要求。
- 博客稿 `docs/blog-mcp-salary-java-2026-08-25.md` — 掘金/CSDN 发布用稿件。

### 5. MCP Registry 收录核验（V1.10 候选 #1）
- **核验结果：mcp.so 搜索 0 结果，项目未进入任何公开目录**（Smithery/官方 Registry/Glama 均未收录）；
- `docs/mcp-registry-submission-2026-08-25.md` — 提交清单（mcp.so 表单 → Smithery CLI → 官方 Registry PR → Glama）+ 公网端点前置问题分析；
- 待办：需用户确认公网部署方案后执行提交。

### 6. 清理
发现根目录 20 个历史扫描残留文件（_chk*.txt / _fixpom*.mjs / _scan*.mjs 等），删除操作被安全策略拦截（需用户二次确认），**明天需手动确认删除**。

## 为什么做这些

- V1.9 功能已满，本周增量转向「把能力变成钱」：Dify 示例=获客内容（社区热度高），多租户预研=解锁中档报价的技术准备，薪酬带雷达=求职/接单锚点，Registry 收录=可见性缺口（确认 0 收录）；
- 市场雷达发现最强信号：**MCP 岗位薪酬带已独立成型且企业级岗位（Anthropic/Coinbase/Docker）在增加**——「Java+Spring 稀缺」叙事获得一手数据支撑。

## 明天做什么

1. **Registry 提交执行**：mcp.so 表单提交（10 分钟可完成）；确认公网端点方案后推进 Smithery/官方 Registry；
2. **删除残留文件**（用户确认后）：根目录 _chk*/_scan*/_fixpom* 等 20 个文件；
3. **多租户 Row-level 隔离编码启动**（V1.11）：mcp-tenant 模块 TenantContext + TenantAwareJdbcTemplate 骨架；
4. 视时间：GitHub star 增长复盘。
