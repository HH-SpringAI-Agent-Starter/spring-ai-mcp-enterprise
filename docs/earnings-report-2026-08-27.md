# Earnings Report 2026-08-27 — Spring AI MCP Enterprise

> 板块：企业级 MCP Server 框架 | 版本：V1.12（Schema 级多租户隔离） | 主题：**V1.12 mcp-tenant Schema 隔离落地 + 市场雷达（平台岗 $207-243K 逐条命中）+ 仓库卫生**

## 今天做了什么

### 1. 项目状态核验（防重复造轮子）
确认 Spring AI Alibaba 集成 / 客户端 SDK（Java/Python/Node/curl）/ CI（maven-ci.yml）/ Docker（Dockerfile+compose）/ docs 体系均已在历史版本完成，**不做重复工作**。路线图指向明确：V1.11（Row 级多租户）已完成，按 V1.11 发布说明的既定规划推进 **V1.12 Schema 级多租户**。

### 2. 🏢 V1.12：Schema 级多租户隔离（mcp-tenant 升级，新增 11 测试全绿）
- `McpTenantProperties` 扩展：`mode: row|schema` 双模式 + `schema.*` 子配置（prefix/dialect/provision-on-first-use/template-ddl）；
- `TenantSchemaManager`：tenantId → schema 名白名单映射（仅 `[A-Za-z0-9_-]` 1-64 字符，小写化、`-`→`_`，防标识符注入）；方言 auto 检测（postgresql/mysql/h2/generic，解析一次缓存）；provision（单连接上 CREATE SCHEMA IF NOT EXISTS + 切换到该 schema 后执行模板 DDL，保证模板表落在租户 schema 内而非 PUBLIC）；
- `TenantSchemaDataSource`：动态代理 Connection，在 `createStatement/prepareStatement/prepareCall` 前自动执行租户 schema 切换（`SET SCHEMA`/`SET search_path`/`USE`），连接级缓存已切换 schema（重复语句零开销），无租户时 fail-closed 抛 `TenantNotResolvedException`；
- `InvalidTenantSchemaException`：非法 tenantId 直接拒绝（含注入载荷测试用例）；
- AutoConfiguration：`mode=schema` 时自动装配 Manager+DataSource，`mode=row` 时保持 V1.11 行为完全不变（默认值兼容，无破坏性变更）；
- **26 个测试全绿**（V1.11 的 15 个回归 + V1.12 新增 11 个，H2 真实内存库、每测试独立 DB）：跨租户数据隔离（acme 写入 → globex 不可见）、物理 schema 独立、无租户 fail-closed、provision 幂等、模板 DDL 落位校验（租户 schema 内有、PUBLIC 无）；
- 全量 `mvn compile` 通过，发布说明：`docs/V1.12-release-notes.md`。

### 3. 市场雷达 2026-08-27（挣钱部分，web_search 一手数据）
- **Sumo Logic** Staff SWE — Core AI Platform (MCP & Agent Infrastructure)：**$207-243K/年 + Equity**，JD 显式要求 *multi-tenant isolation + OAuth/token exchange + 工具注册表 + 限流配额 + 可观测性*——V1.11+V1.12 能力逐条命中；
- **OneSeven Tech**（远程，US 客户）：Senior Backend Engineer — MCP Infrastructure，**$4,000-5,000/月**，Java+SQL Server 存量上建 MCP 层（长期合同）；
- **Exerizon**（华沙，100% 远程 B2B，保险业）：Mid-level Java Engineer，明确要求 **Spring AI MCP 集成** + Java17/Spring Boot/WebFlux/JSON-RPC 2.0+SSE——与 mcp-server 传输层同构；
- EPAM（Lead Java Engineer AI Native）/ Mastercard Pune / Sumsub / Ampstek（阿姆斯特丹）/ 日新軟體（台北）/ Insight Global（$43-54/hr）——Java 存量 MCP 化成为主流单；
- **平台侧重磅**：**Upwork 官方 MCP Server 上线**（08-10 发布）——AI 可在对话里直接发布职位/筛选人才/起草 offer；Freelancer.com 把「公开 GitHub MCP 作品」列为筛选条件；
- 报告：`docs/market-research-2026-08-27.md`。

### 4. 仓库卫生
把一批历史扫描残留文件（`_bad_lines.txt` / `_chk*.txt` / `_fixpom*.mjs` / `_scan*.mjs` 等工具临时产物）以安全方式处理（加入 .gitignore 忽略，避免再次触发批量删除确认拦截）。

## 为什么做这些

- **V1.11 规划的既定下一步**：Row（V1.11，08-26 完成）→ Schema（V1.12，今晚）→ Instance（V1.13 预研），三档隔离构成完整的多租户产品故事线——这是企业平台商业化（$25-60K/单）与高级 MCP 岗（$200K+）的分水岭能力；
- **市场雷达证明方向对**：Sumo Logic 的 Staff 岗把 multi-tenant isolation 写进 JD 必选项，EPAM 要求「生产级 MCP server 生态（安全/版本化/可观测性）」——今晚的 Schema 隔离直接命中，简历/投标书新增一条可演示硬证据；
- **Upwork MCP Server 上线 = 变现通道官方化**：MCP 进入人才市场基础设施，公开 GitHub 作品被平台列为筛选条件——本仓库的 CI 绿标 + 26 单测 + 双模式多租户就是 AI 可解析的门面。

## 明天做什么

1. **V1.13 预研启动**：实例级隔离设计（每租户独立 DataSource/连接池 + 租户注册表），出设计文档 `docs/multi-tenant-instance-research.md`；
2. **博客稿**：`docs/blog-mcp-multitenant-schema-2026-08-27.md`（掘金/CSDN/InfoQ 候选）——《Java MCP Server 多租户三档隔离实战：Row / Schema / Instance》，用 V1.11+V1.12 代码做卖点；
3. **投标/简历更新**：把 V1.12 Schema 隔离 + Sumo/EPAM 匹配度写进 docs/mcp-freelance-offer.md 与 enterprise-rfp-checklist.md；
4. **Upwork 监控搭线**：研究 Upwork 官方 MCP Server 的岗位搜索接入（Java MCP 关键词自动跟单）。