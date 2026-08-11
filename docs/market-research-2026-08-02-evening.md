# 🤑 MCP Market Research — 2026-08-02 晚间增量报告

> 数据窗口: 2026-08-01 ~ 08-02 | Cron 70a53bf4 晚间扫描
> 主题: MCP HTTP 协议转向 + Java MCP 竞争格局 + 挣钱机会拆解
> 状态: V1.0 已发布 · 7 个 Dependabot PR 已合并 · 仓库 CI 全绿

---

## 一、核心增量发现

### 1. 🔥 MCP 协议正式"重回 HTTP"——Streamable HTTP 成为默认

**08-02 动态（InfoQ 译文 / Janakiram MSV）**：MCP 最大更新解读持续发酵，
核心转向 **Streamable HTTP 替代 SSE**：
- SSE transport 被标记 deprecated（Spring AI 2.0.0-M7 同步跟进）
- Streamable HTTP 成为新默认服务端协议（断线重连 + 会话恢复 + 标准 HTTP 语义）
- 对企业价值：**可以直接套用现有网关/负载均衡/CDN/监控体系**，无需专用长连接基础设施

**对我们的意义**：V0.11+ 已实现 `McpStatelessEndpoint`（/api/mcp/v2 无状态端点），
与 Streamable HTTP 理念完全同向。**营销话语必须从 "SSE 流式" 切换为 "Streamable HTTP + 无状态 + 标准 HTTP 语义"**。
这是项目 README / 官网 / 投稿稿当前最大话术差距。

### 2. ⚠️ Java MCP 教程内容开始增多——竞争窗口在收窄

**07-29 动态（CSDN《从零实现 MCP 服务:Spring Boot + MCP Java SDK 实战指南》）**：
- 已出现手把手 Java MCP 教程（JDK17 + Spring Boot 3.2 + MCP SDK 0.10.0）
- 说明 Java 开发者正在批量涌入 MCP 赛道，但教程停留在**单工具玩具级**
- 竞争位差：**教程教"怎么连一个工具"，我们交付"企业级安全框架"（RBAC+限流+审计+注册中心+K8s）**

**判断**：窗口期 = 教程级内容爆发、但企业级框架稀缺的 3-6 个月。
必须加速：①中文 SEO 内容 ②Maven Central 发布 ③企业案例落地。

### 3. 就业市场：Java+AI 岗持续高薪（延续 08-01 快照）

| 公司 | 岗位 | 薪资 | MCP/AI 要求 | 数据日期 |
|------|------|------|------------|---------|
| 阿里 | AI App 开发（Java） | 20-45K×16 | **MCP 协议（硬性）** | 07-25~31 |
| 华为 | AI 平台开发（Java） | 25-45K×15 | MCP 经验优先 | 07-25~31 |
| 字节 | Agent 算法工程师 | 30-60K·14薪 | Agent 平台 | 07-25~31 |
| 腾讯 | 企业微信 AI 搜索 | 30-60K | RAG+向量 | 07-25~31 |

**AI 岗平均月薪 60,738 元；MCP 经验溢价 1.3-1.5x**（市场持续验证）

---

## 二、挣钱机会拆解（Java + Spring + AI 组合卖点）

### 1. 卖点定位（一句话版）

> **"Python 系 MCP 框架管不了中国企业，我们让 Java/Spring 存量系统一天接入 AI。"**

### 2. 四条变现路径 + 定价参考

| 路径 | 状态 | 定价参考 | 目标客户 |
|------|------|---------|---------|
| ① 企业定制开发（MCP Server 私有化） | 🎯 主攻 | 单项目 5-50 万元 | 有存量 Java 系统的中型企业（ERP/OA/供应链） |
| ② MCP Marketplace 被动收入 | 文档已就绪 | $200-800/月 | 全球 MCP 用户 |
| ③ 开源商业化（企业版: 多租户/SSO/高可用） | 规划中 | 年费 2-10 万 | 已有开源用户转化 |
| ④ 技术咨询/培训（Java MCP 落地课） | 可启动 | 2000-8000 元/次 | 企业内训/付费社群 |

### 3. 招投标/Upwork 打法建议

- **国内**：盯「AI 中台」「智能体平台」「大模型应用集成」类招标（国央企 IT 预算充裕），
  关键词：MCP / Agent / 大模型集成 / 工具调用
- **海外**：Upwork 搜索 `MCP server`、`Model Context Protocol`、`Spring AI`，
  单价锚点 $50-150/hr；用本项目开源仓库作为作品集（GitHub 链接直接进 proposal）
- **差异化话术**：竞品 Python 系（FastMCP 等）无法覆盖 Java 存量系统；
  我们的 RBAC/审计/限流 = 企业安全合规刚需，教程级方案给不了

---

## 三、行动清单（08-03 起）

- [ ] README 话术升级: "SSE 流式" → "Streamable HTTP + 无状态"（对齐 MCP 2026-07-28）
- [ ] docs/ 补一篇《Spring AI MCP Server 企业落地实战》SEO 长文（对标 CSDN 教程位差）
- [ ] Maven Central 正式发布（Sonatype 审核中, 检查进度）
- [ ] 中文社区推广: 掘金/CSDN 发布已备稿 3 篇
- [ ] 跟踪 BOSS直聘 "MCP" 关键词岗位快照（每周五更新表格）

---

## 四、仓库运营状态（08-02 晚间）

| 项 | 状态 |
|----|------|
| GitHub stars | 0（待推广启动） |
| Open PR | 0（7 个 Dependabot PR 已全部合并 ✅） |
| CI | maven-ci.yml 五 job（build/quality/integration/docker/release） |
| 版本 | v1.0.1 (tag) / 0.16.0-SNAPSHOT (dev) |
| 测试 | 142 tests 全通过 |

---

*下次扫描: 08-03 21:30 cron*
