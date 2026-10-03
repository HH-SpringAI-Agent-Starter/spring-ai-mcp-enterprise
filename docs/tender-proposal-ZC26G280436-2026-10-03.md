# 📑 投标技术方案模板：AI 网关、MCP 注册、多模态平台（ZC26G280436）

> **适用招标**：中国航空工业集团·西安飞行自动控制研究所
> **招标编号**：ZC26G280436｜ **截标**：2026-10-20｜ **数量**：1 套（含 2 台智算服务器）
> **要求**：国产商业平台 + 多模态组件 + MCP 组件；合同签订后 3 个月内到货；**不接受联合体**
> **本模板定位**：把 [Spring AI MCP Enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise) 直接映射为投标技术底座，**非投标文件本身**，需由具备资质的主体填充商务章后提交。

---

## 1. 招标需求 ↔ 本框架模块映射（技术响应矩阵）

| 招标技术规格 | 本框架对应能力 | 证据（模块 / 文档） |
|-------------|---------------|---------------------|
| **AI 工具平台（国产）** | 开源、可私有化部署、适配国产操作系统/JDK | `mcp-server` + `mcp-spring-boot-starter`（Spring Boot 3.x，支持国产 JDK/TongWeb） |
| **多模态组件** | 通义千问多模态（文本/图像）+ 工具编排 | `mcp-alibaba`（DashScope 兼容，已就绪） |
| **MCP 组件（核心）** | MCP Server 框架 + 工具注册中心 + 联邦网关 | `mcp-core` + `mcp-registry` + `mcp-gateway` |
| **安全 / 审计（央企硬要求）** | RBAC + 审计 JDBC 可取证 + HITL 审批 + 限流 | `mcp-governance`（66 测试全绿，等保合规对照见 §5） |
| **多租户 / 隔离** | 租户级工具/数据隔离 | `mcp-tenant` |
| **监控 / 可观测** | Prometheus + Grafana + traceId 链路追踪 | `mcp-monitor` + `mcp-governance`（V1.31 W3C traceparent） |

---

## 2. 总体架构（一页图，文字版）

```
[智算服务器 ×2]  ──部署──>  [Spring AI MCP Enterprise 平台]
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
   [mcp-registry]         [mcp-gateway]          [mcp-governance]
   工具/技能注册中心        联邦网关(统一入口)        安全治理(RBAC/审计/HITL)
        │                       │                       │
   [mcp-alibaba]          [业务 MCP Server 群]     [mcp-monitor]
   通义千问多模态           (存量 Spring Cloud/      Prometheus/Grafana
   (多模态组件)             Dubbo 零改造接入)
```

**关键设计**：
- **入口收敛**：所有 Agent / 上游系统只连 `mcp-gateway` 一个端点，工具按命名空间隔离（`hr__search` vs `erp__search`）。
- **注册即治理**：工具注册到 `mcp-registry` 即进入版本管理（灰度 + 一键回滚），并自动套用 `mcp-governance` 的 RBAC/审计。
- **存量零改造**：已有 Java 微服务通过 Higress/网关代理 1 行配置发布为 MCP 工具，保护既有投资。

---

## 3. MCP 注册中心设计（对标"MCP 注册"核心诉求）

- **注册模型**：Skill/Spec 注册（注册即激活）+ 语义化版本（历史快照/裁剪）+ 显式激活 + **故障回滚** + **灰度路由（权重 0-100）**。
- **持久化**：JDBC（`store=jdbc`，opt-in），重启/滚动发布后版本、ACTIVE、灰度权重自动恢复；`SkillRegistryStore` SPI 支持国产数据库（达梦/人大金仓）方言扩展。
- **管理 API**：`/api/admin/skills`（注册/激活/回滚/灰度）+ 客户端发现 `/api/mcp/skills`。
- **对标 Nacos MCP Registry**：本注册中心额外提供**企业安全治理层**（Nacos 无原生 RBAC/审计/HITL），可作为 Nacos 的治理增强。

---

## 4. AI 网关设计（联邦网关）

- **能力**：一个入口聚合任意多个下游 MCP Server，工具自动命名空间隔离，复用全部治理能力（RBAC/RateLimit/审计/工具级 Scope/健康检查/统计）。
- **上游配置示例**：
```yaml
mcp:
  enterprise:
    gateway:
      sync-on-startup: true
      upstreams:
        - name: hr
          url: http://hr-mcp:8080/mcp
          api-key: ${HR_MCP_API_KEY:}
          enabled: true
        - name: erp
          url: http://erp-mcp:8080/mcp
          enabled: true
```
- **下线机制**：`enabled: false` 一键下线某上游工具（收购/遗留系统风险敞口可控）。

---

## 5. 等保 / 合规对照表（央企标书必填）

| 等保 2.0 要求 | 本框架实现 | 模块 |
|--------------|-----------|------|
| 身份鉴别（a） | API Key + RBAC 角色 | `mcp-auth` + `mcp-governance` |
| 访问控制（b） | 工具级 Scope + 命名空间隔离 | `mcp-governance` |
| 安全审计（c） | 治理判定 JDBC 落库 + CSV 导出 + TTL 保留 | `mcp-governance`（V1.29/30） |
| 入侵防范（e） | RateLimit + deny-tiers 硬闸门 + Fail-closed | `mcp-governance` |
| 数据保密（d） | 敏感数据脱敏（身份证/银行卡/手机号/密钥） | `mcp-governance` |
| 集中管控（集中审计） | W3C traceparent 链路追踪 + SIEM 流导出 | `mcp-governance`（V1.31/32） |

---

## 6. 交付与验收（对齐"3 个月到货"）

| 阶段 | 周期 | 交付物 |
|------|------|--------|
| 环境部署 | 第 1 月 | 平台容器化部署（Docker/K8s）+ 2 台智算服务器纳管 + 高可用验证 |
| 功能联调 | 第 2 月 | MCP 注册中心 + 联邦网关 + 通义千问多模态打通 + 3 个示范工具上线 |
| 等保测评 | 第 3 月 | 治理/审计/脱敏验收 + 等保二级/三级测评支持材料 + 培训移交 |

---

## 7. 商务提示（非技术，需资质主体填写）

- **不接受联合体** → 需以单一法人主体投标，提供 2025 年度审计财报。
- **智算服务器** → 需提供制造商授权书（同一品牌同型号仅 1 个代理商）。
- **信用** → 信用中国 / 国家企业信用公示 / 中国执行信息公开网无失信记录。
- **业绩** → 准备"MCP / 时序数据 / AI 平台"类合同作为类似业绩证明（参考长庆油田 130 万档要求）。

---

## 8. 为什么选本框架（差异化一句话）

> **"Nacos/Higress 做路由，我们做治理与可观测"** —— 开源、可私有化、国产大模型兼容、等保合规就绪，把 Spring AI Alibaba 1.0 GA 的企业级 MCP 方案补成**生产级可信系统**。

---
_模板生成日期 2026-10-03｜本文件为技术方案草案，不构成投标承诺；正式投标请以招标方发出的招标文件为准。_
