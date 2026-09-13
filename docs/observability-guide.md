# MCP Enterprise Server — 可观测性指南（V1.24）

> 面向 SRE / 平台工程师：如何把 MCP Enterprise Server 接入企业既有的 Prometheus + Grafana 体系，配置告警，并用 SLI/SLO 衡量 MCP 工具网关的健康度。

---

## 1. 为什么 MCP Server 需要专门的可观测性

MCP Server 的调用方是 **AI Agent**，而不是人。这带来三个新问题：

1. **非确定性调用**：Agent 何时调用、调用哪个工具不可预测，靠人工发现故障不现实；
2. **链式放大**：一次 Agent 任务可能触发几十次工具调用，单个工具慢 200ms 会被放大成分钟级超时；
3. **信任边界**：工具是 Agent 访问企业系统的入口，错误率/异常调用需要可审计、可告警。

因此我们把「指标 + 告警 + 看板」作为一等公民内置进框架，而不是让用户自己拼。

---

## 2. 指标体系（OpenMetrics 文本格式）

`mcp-monitor` 通过 `GET /api/monitor/metrics/prometheus` 暴露 **OpenMetrics 文本格式**指标，**无需引入 `micrometer-registry-prometheus`**，零额外依赖。

| 指标 | 类型 | 标签 | 含义 |
|---|---|---|---|
| `mcp_tool_invocations_total` | counter | `tool` | 各工具累计调用次数（保留窗口内） |
| `mcp_tool_errors_total` | counter | `tool` | 各工具累计错误次数 |
| `mcp_tool_latency_ms` | gauge | `tool` | 各工具平均延迟（毫秒） |
| `mcp_gateway_invocations_total` | counter | `method`, `name` | 网关路由（Mcp-Method / Mcp-Name 头维度）累计调用 |
| `mcp_gateway_errors_total` | counter | `method`, `name` | 网关路由累计错误 |
| `mcp_gateway_latency_ms` | gauge | — | 网关平均延迟（毫秒） |
| `mcp_build_info` | gauge | `version` | 构建信息，用于探测重启/发布 |

> 说明：调用/错误/延迟是**保留窗口内的聚合值**（见 `mcp.enterprise.monitor.retention`，默认 1 小时滚动窗口）。若需要严格意义上的单调 counter，请调大保留窗口或对接企业 TSDB 长期存储。

---

## 3. Prometheus 接入

### 3.1 内置抓取配置

`config/prometheus/prometheus.yml`（docker-compose `--profile monitoring` 已自动挂载）：

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - /etc/prometheus/alerts.yml

scrape_configs:
  - job_name: "mcp-enterprise-server"
    metrics_path: "/api/monitor/metrics/prometheus"
    static_configs:
      - targets: ["mcp-server:8081"]
```

### 3.2 接入企业现有 Prometheus

只需追加一个 scrape job：

```yaml
scrape_configs:
  - job_name: "mcp-enterprise"
    metrics_path: "/api/monitor/metrics/prometheus"
    static_configs:
      - targets: ["mcp-server.your-domain:8081"]
    # 如需鉴权（V1.8+ Bearer/API Key）：
    # authorization:
    #   type: Bearer
    #   credentials_file: /etc/prometheus/mcp-token
```

> 出于安全考虑，生产环境建议把 `/api/monitor/**` 置于内网 / 网关后，或开启 Bearer 鉴权后仅对监控系统放行。

---

## 4. 告警规则（V1.24 新增）

`config/prometheus/alerts.yml` 内置四组规则，与上面的指标一一对应：

| 告警 | 条件 | 级别 | 说明 |
|---|---|---|---|
| `McpToolHighErrorRate` | 工具错误率 > 20%（5m） | warning | 单工具质量下降 |
| `McpToolCriticalErrorRate` | 工具错误率 > 50%（15m） | critical | 工具基本不可用 |
| `McpToolLatencyHigh` | 平均延迟 > 3s（10m） | warning | Agent 链路开始变慢 |
| `McpToolLatencyCritical` | 平均延迟 > 8s（5m） | critical | 大概率触发 Agent 端超时 |
| `McpGatewayHighErrorRate` | 网关路由错误率 > 20%（5m） | warning | 路由/协议层异常 |
| `McpGatewayLatencyHigh` | 网关延迟 > 5s（10m） | warning | 网关成为瓶颈 |
| `McpServerDown` | `up == 0`（2m） | critical | 服务不可达 |
| `McpServerRestarted` | 15m 内 `mcp_build_info` 变化 | info | 探测到重启/发布 |
| `McpTrafficDrop` | 10m 调用速率 < 1h 前同期的 10%（15m） | warning | Agent 客户端异常/路由变更 |

错误率统一用 `rate(errors) / clamp_min(rate(invocations), 1e-9)` 计算，避免除零。

---

## 5. Grafana 看板（V1.24 新增）

`config/grafana/` 下已内置 **自动 Provisioning** 配置与看板，启动即生效，无需手工导入：

```
config/grafana/
├── provisioning/
│   ├── datasources/prometheus.yml     # 自动添加 Prometheus 数据源（uid=prometheus）
│   └── dashboards/dashboards.yml      # 自动加载 dashboards 目录
└── dashboards/
    └── mcp-enterprise-overview.json   # 「MCP Enterprise Server Overview」看板
```

**看板包含 11 个面板：**

- 顶部状态行：存活 / 工具总数 / 累计调用 / 累计错误 / **整体错误率** / 网关平均延迟
- 调用速率（按工具拆分）
- 错误速率（按工具拆分）
- 各工具平均延迟曲线
- 网关调用/错误曲线（按 Mcp-Method 拆分）
- **工具健康明细表**（调用次数 / 错误数 / 平均延迟三联表）

启用方式：

```bash
docker compose --profile monitoring up -d
# Grafana: http://localhost:3000  （默认 admin / admin，可用 GRAFANA_PASSWORD 覆盖）
# Prometheus: http://localhost:9090
```

---

## 6. SLI / SLO 建议

用 MCP 网关的常见 SLO 起点：

| SLI | 定义 | 建议 SLO |
|---|---|---|
| 可用性 | `1 - avg_over_time(工具错误率)` | ≥ 99.5%（30 天） |
| 延迟 | P95 工具延迟 | ≤ 2s |
| 网关错误率 | `rate(gateway_errors)/rate(gateway_invocations)` | ≤ 0.5% |

对应 PromQL：

```promql
# 工具可用性（30d）
1 - sum(increase(mcp_tool_errors_total[30d])) / clamp_min(sum(increase(mcp_tool_invocations_total[30d])), 1e-9)

# 工具错误预算燃尽速率（1h 窗口，SLO=99.5%）
(1 - 0.995) / clamp_min(
  sum(rate(mcp_tool_errors_total[1h])) / clamp_min(sum(rate(mcp_tool_invocations_total[1h])), 1e-9),
  1e-9
)
```

---

## 7. 排查手册（常见告警 → 动作）

| 现象 | 优先排查 |
|---|---|
| `McpToolHighErrorRate` | 查 `/api/monitor/audit/failed` 最近失败记录 → 定位工具参数/下游故障 |
| `McpToolLatencyCritical` | 查该工具下游依赖（DB/HTTP）耗时；确认是否被限流；考虑加超时与熔断 |
| `McpGatewayHighErrorRate` | 查 `Mcp-Method`/`Mcp-Name` 头是否被客户端写错；核对协议版本 |
| `McpServerDown` | 容器/K8s 状态、JVM OOM、端口占用；看 `/actuator/health` |
| `McpTrafficDrop` | 客户端 API Key 过期 / 路由变更 / Agent 侧故障 |

---

## 8. 与内置监控 API 的关系

除了 Prometheus 端点，`mcp-monitor` 还提供 REST 查询接口，便于自建运维台：

| 端点 | 用途 |
|---|---|
| `GET /api/monitor/metrics` | 全量聚合指标 |
| `GET /api/monitor/metrics/{tool}` | 单工具指标 |
| `GET /api/monitor/metrics/gateway` | 网关路由指标 |
| `GET /api/monitor/audit` | 最近审计日志 |
| `GET /api/monitor/audit/failed` | 失败审计记录 |
| `GET /api/monitor/alerts` | 活跃告警 |
| `GET /api/monitor/summary` | 汇总快照 |

---

_最后更新：2026-09-13 · V1.24_
