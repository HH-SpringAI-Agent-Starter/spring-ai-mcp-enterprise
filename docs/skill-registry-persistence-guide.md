# Skill Registry 持久化指南（V1.22）

> 目标读者：需要在生产环境运行 Skill Registry、并希望「重启 / 滚动发布 / 故障回滚」后状态不丢的团队。
> 关联：`docs/skill-registry-guide.md`（V1.21 基础用法）、`docs/V1.22-release-notes.md`。

---

## 一、三分钟上手

### 1. 打开 JDBC 持久化

```yaml
mcp:
  enterprise:
    registry:
      skill:
        store: jdbc                 # 默认 memory；改为 jdbc 即启用持久化
        table: mcp_skill_registry   # 可选，默认 mcp_skill_registry
        init-schema: true           # 启动时自动建表；用 Flyway/Liquibase 时置 false
spring:
  datasource:                       # 任意的 Spring 数据源即可
    url: jdbc:mysql://localhost:3306/mcp?useUnicode=true&characterEncoding=utf8
    username: mcp
    password: ******
```

> 若配置了 `store=jdbc` 但容器内没有 `DataSource`，框架会打印 WARN 并**自动降级为内存模式**，应用照常启动。这样避免"少配一个数据源就起不来"。

### 2. 验证持久化生效

```bash
# 1) 注册并激活一个版本
curl -s -XPOST localhost:8081/api/admin/skills -H 'Content-Type: application/json' -d '{
  "name":"finance_indicator","version":"1.2.0","description":"财务指标",
  "category":"finance","owner":"platform-team",
  "inputSchema":{"type":"object","properties":{"symbol":{"type":"string"}}}
}'

# 2) 再注册一个版本，然后回滚
curl -s -XPOST localhost:8081/api/admin/skills -H 'Content-Type: application/json' -d '{
  "name":"finance_indicator","version":"1.3.0","description":"有问题的版本","category":"finance"}'
curl -s -XPOST localhost:8081/api/admin/skills/finance_indicator/rollback

# 3) 重启进程后查询——版本历史与 ACTIVE 版本应仍然存在
curl -s localhost:8081/api/admin/skills/finance_indicator/versions
```

**内存模式（V1.21）在第 3 步会 404；V1.22 的 jdbc 模式会返回完整历史与当前 ACTIVE 版本。**

### 3. 灰度权重同样持久化

```bash
curl -s -XPOST localhost:8081/api/admin/skills/finance_indicator/gray \
  -H 'Content-Type: application/json' -d '{"version":"1.3.0","weight":30}'
# 重启后 route() 仍按 30% 命中 1.3.0，其余回退到 ACTIVE 版本
```

---

## 二、各数据库 DDL

`init-schema: true` 时框架执行的建表语句（`VARCHAR(4000)` 版本，跨库通用）。若由 DBA / 迁移工具托管，可直接采用下面任一版本。

### MySQL / MariaDB

```sql
CREATE TABLE IF NOT EXISTS mcp_skill_registry (
  skill_name   VARCHAR(128) NOT NULL,
  version      VARCHAR(64)  NOT NULL,
  description  VARCHAR(1024),
  category     VARCHAR(128),
  owner        VARCHAR(128),
  status       VARCHAR(32),
  input_schema TEXT,
  active       TINYINT NOT NULL DEFAULT 0,
  gray_weight  INT,
  created_at   BIGINT,
  updated_at   BIGINT,
  PRIMARY KEY (skill_name, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### PostgreSQL

```sql
CREATE TABLE IF NOT EXISTS mcp_skill_registry (
  skill_name   VARCHAR(128) NOT NULL,
  version      VARCHAR(64)  NOT NULL,
  description  VARCHAR(1024),
  category     VARCHAR(128),
  owner        VARCHAR(128),
  status       VARCHAR(32),
  input_schema TEXT,
  active       SMALLINT NOT NULL DEFAULT 0,
  gray_weight  INT,
  created_at   BIGINT,
  updated_at   BIGINT,
  PRIMARY KEY (skill_name, version)
);
```

### SQL Server

```sql
IF OBJECT_ID('mcp_skill_registry','U') IS NULL
CREATE TABLE mcp_skill_registry (
  skill_name   NVARCHAR(128) NOT NULL,
  version      NVARCHAR(64)  NOT NULL,
  description  NVARCHAR(1024),
  category     NVARCHAR(128),
  owner        NVARCHAR(128),
  status       NVARCHAR(32),
  input_schema NVARCHAR(MAX),
  active       INT NOT NULL DEFAULT 0,
  gray_weight  INT,
  created_at   BIGINT,
  updated_at   BIGINT,
  PRIMARY KEY (skill_name, version)
);
```

> 提示：`input_schema` 在自动建表时用 `VARCHAR(4000)`（全库兼容）。生产若单个 Skill 的 JSON schema 可能超过 4000 字符，请按上表改为 `TEXT` / `CLOB` / `NVARCHAR(MAX)`，并设置 `init-schema: false` 由迁移工具托管。

---

## 三、行为与语义

| 动作 | 内存变更 | 落库 | 失败处理 |
| --- | --- | --- | --- |
| `register` | 注册并激活 | upsert + setActive + clearGray | 仅 WARN，不回滚内存 |
| `activate` | 切换 ACTIVE | setActive + clearGray | 仅 WARN |
| `rollback` | 退回上一版本 | setActive + clearGray | 仅 WARN |
| `gray` | 记录权重 | setGrayWeight | 仅 WARN |
| `remove` | 清空该 Skill | removeSkill | 仅 WARN |
| 启动 | `reload()` | load* 四个查询 | WARN 后以空表启动 |

**可用性优先于持久性**：内存变更总是先成功，落库失败只记 WARN。这样数据库瞬时抖动不会中断技能治理操作。若你的场景要求强一致（落库失败即失败），可以自定义 `SkillRegistryStore` Bean 覆写默认装配（`@ConditionalOnMissingBean` 会尊重你的定义）。

### `active` 标记的保留语义

`upsert` 时会读取已有行的 `active` 并原样写回，因此：
- 已激活版本被再次 upsert（更新 description / owner 等）时，**不会被误置为非激活**；
- 新插入的版本初始 `active=0`，随后由 `setActive` 精确置位——保证「同一 Skill 任意时刻只有一个 ACTIVE 版本」。

---

## 四、常见问题

**Q：多个副本怎么办？**
A：V1.22 保证**重启后可恢复**；多副本一致性（一个副本灰度、另一个副本不知情）是 V1.23 的候选（乐观锁 CAS 或 Redis 广播刷新）。当前建议灰度治理通过 `reload()` 定时刷新或发布期单副本操作。

**Q：会拖慢工具调用吗？**
A：不会。落库只发生在**治理动作**（注册/激活/回滚/灰度/删除），不在热路径 `route()` 上；`route()` 仍是纯内存计算。

**Q：`init-schema` 能关吗？**
A：能。用 Flyway/Liquibase 或 DBA 建表时设 `false`，框架不再执行任何 DDL。

**Q：和 ToolScope（V1.19）是什么关系？**
A：ToolScope 管「谁能调用哪个工具」，Skill Registry 管「当前服务哪个版本、如何灰度」。两者正交，可叠加。V1.23 计划做联动（灰度版本携带 scope）。
