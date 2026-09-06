# 🛠️ Skill Registry 技能注册表指南（V1.21）

> 企业 MCP 平台治理层：Skill/Spec 注册 → 语义化版本管理 → 显式激活 → 故障回滚 → 灰度路由
> 对齐企业真实需求：沃尔玛中国"Skill、Spec Registry 服务"、禾蛙 MCP 平台工程师"Skill 注册、版本管理、灰度发布、故障回滚"

---

## 为什么需要 Skill Registry？

企业 MCP Server 从"一个工具"变成"一批工具"后，会面临三个现实问题：

1. **谁在用哪个版本？** AI Agent 调用 `finance_indicator` 时，可能同时存在 1.0.0 / 1.1.0 / 2.0.0 三个版本——没有版本戳，审计都不知道结果来自哪个实现。
2. **上线/回滚太慢？** 新版工具出问题，最理想是"一条命令回滚到上一版"，而不是改代码重新发布。
3. **如何灰度？** 新版本想先放 10% 流量验证，再全量——需要一个轻量的灰度路由。

`mcp-registry` 模块（纯内存、零外部依赖、线程安全）为以上场景提供开箱即用的治理能力，可嵌入任意 Spring Boot 应用。

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.mcp.enterprise</groupId>
    <artifactId>mcp-registry</artifactId>
    <version>1.1.0</version>
</dependency>
```

### 2. 配置（默认启用，无需任何配置即可使用）

```yaml
mcp:
  enterprise:
    registry:
      skill:
        enabled: true            # 总开关（默认 true）
        max-versions-per-skill: 20  # 每个 Skill 保留的历史版本数
```

### 3. 注册一个 Skill 版本

```bash
curl -X POST http://localhost:8081/api/admin/skills \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-admin-key" \
  -d '{
    "name": "finance_indicator",
    "description": "财务指标计算（CAGR/ROE/PEG）",
    "version": "1.0.0",
    "category": "finance",
    "owner": "platform-team",
    "inputSchema": {
      "type": "object",
      "properties": {
        "symbol": {"type": "string"},
        "metric": {"type": "string", "enum": ["CAGR", "ROE", "PEG"]}
      },
      "required": ["symbol", "metric"]
    }
  }'
# → 201 {"registered":true,"skill":{...,"status":"ACTIVE"}}
```

> 注册即激活：首个版本自动成为 ACTIVE，后续新版本注册后同样立即生效（部署=激活）。

## 核心操作（curl 速查）

| 操作 | 命令 | 说明 |
| --- | --- | --- |
| 列表 | `GET /api/admin/skills` | 所有 ACTIVE Skill，按名称排序 |
| 详情 | `GET /api/admin/skills/{name}` | 当前 ACTIVE 版本 |
| 版本史 | `GET /api/admin/skills/{name}/versions` | 全部历史版本（新→旧）+ activeVersion |
| 注册 | `POST /api/admin/skills` | 注册新版本（自动激活）|
| 激活 | `POST /api/admin/skills/{name}/activate` `{"version":"1.0.0"}` | 显式切换到历史版本 |
| 回滚 | `POST /api/admin/skills/{name}/rollback` | 一键退回到上一个已部署版本 |
| 灰度 | `POST /api/admin/skills/{name}/gray` `{"version":"1.1.0","weight":10}` | 10% 流量走新版本 |
| 删除 | `DELETE /api/admin/skills/{name}` | 移除整个 Skill（含版本史与灰度配置）|
| 发现 | `GET /api/mcp/skills` | 客户端只读发现（含 version/status）|

### 版本管理

```bash
# 注册 v2（自动激活）
curl -X POST http://localhost:8081/api/admin/skills -H "Content-Type: application/json" \
  -d '{"name":"finance_indicator","version":"2.0.0","description":"v2 with new metrics","category":"finance"}'

# 查看版本历史
curl http://localhost:8081/api/admin/skills/finance_indicator/versions
# → {"activeVersion":"2.0.0","versions":[{"version":"2.0.0",...},{"version":"1.0.0",...}]}
```

### 故障回滚（一条命令）

```bash
# v2 出问题了，立即回滚到 v1
curl -X POST http://localhost:8081/api/admin/skills/finance_indicator/rollback
# → {"rolledBack":true,"skill":{"version":"1.0.0","status":"ACTIVE"}}
```

### 灰度发布

```bash
# 1.1.0 先放 10% 流量
curl -X POST http://localhost:8081/api/admin/skills/finance_indicator/gray \
  -H "Content-Type: application/json" -d '{"version":"1.1.0","weight":10}'

# 观察稳定后全量：把权重调到 100（或直接 activate 1.1.0，灰度配置自动清除）
curl -X POST http://localhost:8081/api/admin/skills/finance_indicator/activate \
  -H "Content-Type: application/json" -d '{"version":"1.1.0"}'
```

> 灰度口径：权重是相对值，总和可为任意值；**未分配权重自动回退到 ACTIVE 版本**（权重 0 = 永远走 ACTIVE）。支持多版本同时按权重分流，同一版本多次配置会覆盖。

## 客户端发现

```bash
# 客户端（或 mcp-alibaba 集成）读取当前服务的 Skill 清单
curl http://localhost:8081/api/mcp/skills
# → {"count":1,"skills":[{"name":"finance_indicator","description":"...","version":"1.1.0",
#    "status":"ACTIVE","category":"finance","inputSchema":{...}}]}

curl http://localhost:8081/api/mcp/skills/finance_indicator
# → 单个 Skill 的 ACTIVE spec
```

## 与 mcp-core ToolRegistry 的分工

| 组件 | 职责 | 粒度 |
| --- | --- | --- |
| `mcp-core` ToolRegistry | 工具注册、发现、执行（运行时唯一实现）| 一个工具 = 一个实现 |
| **`mcp-registry` SkillRegistry** | **Skill 的版本治理、灰度、回滚（上线/运维生命周期）**| 一个 Skill = 多个版本 |

两者互补：Tools 决定"能做什么"，Skill Registry 决定"哪个版本在线上、出问题怎么退"。

## 安全

- `/api/admin/skills/*` 归属管理面，与其它 `/api/admin/*` 一样必须由 mcp-auth 的管理员鉴权/网络策略保护，禁止公网暴露。
- `/api/mcp/skills` 为只读发现端点，供 MCP 客户端与集成模块使用。
- 版本快照为不可变引用（`List.copyOf` / 注册时快照），并发安全：所有写操作 `synchronized`，读操作无锁。

## 模块结构

```
mcp-registry/
├── pom.xml
└── src/main/java/com/mcp/enterprise/registry/
    ├── SkillStatus.java                  # DRAFT/ACTIVE/DEPRECATED/RETIRED 生命周期
    ├── SkillSpec.java                    # 单个版本的 Skill 定义（含 inputSchema）
    ├── SkillRegistry.java                # 核心服务：注册/激活/回滚/灰度/路由（线程安全）
    ├── SkillRegistryProperties.java      # 配置绑定
    ├── SkillAdminController.java         # /api/admin/skills 管理端点
    ├── SkillDiscoveryController.java     # /api/mcp/skills 客户端发现端点
    └── SkillRegistryAutoConfiguration.java  # 自动配置（默认启用）
```

---

## 下一步（V1.22 候选）

- [ ] Skill Registry 持久化（JDBC/MySQL）：跨实例共享版本状态，对齐沃尔玛 Nacos/注册中心叙事
- [ ] 灰度 + Token Scope 联动：`gray` 路由与 `/api/mcp/tools` 工具发现打通（invoke 时先 route 再执行）
- [ ] Skill 审批流：DRAFT → 评审 → ACTIVE 的 API 化（对齐"Skill、Spec Registry 沉淀可评估"要求）