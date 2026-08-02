# Spring AI MCP Enterprise — 运维手册 (V1.0)

> 面向 SRE / 运维人员的日常操作指南：启动、健康检查、日志、备份、扩容、故障恢复。

---

## 1. 服务生命周期

### 1.1 启动

```bash
# Docker Compose
docker-compose up -d

# K8s
kubectl rollout status deployment/mcp-enterprise -n mcp-enterprise

# 直接 JAR（临时/单机）
java -jar mcp-server/target/mcp-server-*.jar \
  --spring.profiles.active=prod
```

### 1.2 停止

```bash
# 优雅停止（等待进行中请求完成）
docker-compose down
# 或
kubectl scale deployment/mcp-enterprise --replicas=0 -n mcp-enterprise
```

### 1.3 重启

```bash
kubectl rollout restart deployment/mcp-enterprise -n mcp-enterprise
```

## 2. 健康检查

### 2.1 端点

```bash
# 基础健康
curl -s http://localhost:8080/actuator/health

# 预期输出
{"status":"UP"}
```

### 2.2 就绪/存活探针（K8s 已配置）

| 探针 | 路径 | 用途 |
|------|------|------|
| livenessProbe | `/actuator/health/liveness` | 存活检测，失败重启 |
| readinessProbe | `/actuator/health/readiness` | 就绪检测，失败摘流量 |

## 3. 日志管理

### 3.1 日志查看

```bash
# Docker
docker logs -f mcp-enterprise --tail 200

# K8s
kubectl logs -f deployment/mcp-enterprise -n mcp-enterprise --tail=200

# 按时间过滤
kubectl logs deployment/mcp-enterprise -n mcp-enterprise \
  --since=1h
```

### 3.2 日志采集（生产建议）

- **方案**：EFK (Elasticsearch + Fluentd + Kibana) 或 Loki + Grafana
- **审计日志**：独立索引 `mcp-audit-*`，保留 ≥180 天
- **应用日志**：JSON 格式输出（便于结构化检索）

## 4. 数据库运维

### 4.1 备份（每日）

```bash
# MySQL 逻辑备份
mysqldump -u mcp_prod -p mcp_enterprise \
  > /backup/mcp_enterprise_$(date +%Y%m%d).sql

# 保留 30 天
find /backup -name "*.sql" -mtime +30 -delete
```

### 4.2 恢复

```bash
mysql -u mcp_prod -p mcp_enterprise < /backup/mcp_enterprise_20260101.sql
```

### 4.3 主从配置建议

- 主库负责写入（审计日志等）
- 从库负责只读查询
- 通过 `DB_URL` 指向主库；从库用于报表/分析

## 5. 扩容与缩容

### 5.1 手动扩容

```bash
kubectl scale deployment/mcp-enterprise --replicas=5 -n mcp-enterprise
```

### 5.2 自动伸缩（HPA）

```bash
# 查看当前 HPA 状态
kubectl get hpa mcp-enterprise-hpa -n mcp-enterprise

# 调整阈值
kubectl patch hpa mcp-enterprise-hpa -n mcp-enterprise \
  --type=merge -p '{"spec":{"metrics":[{"type":"Resource","resource":{"name":"cpu","target":{"type":"Utilization","averageUtilization":60}}}]}}'
```

## 6. 配置变更

### 6.1 ConfigMap 更新（K8s）

```bash
# 修改 configmap.yaml 后
kubectl apply -f k8s/configmap.yaml

# 使配置生效（滚动重启）
kubectl rollout restart deployment/mcp-enterprise -n mcp-enterprise
```

### 6.2 环境变量热更新说明

- 认证配置（API Key 列表）变更**需要重启**生效
- 速率限制阈值变更**需要重启**生效
- 监控开关变更**需要重启**生效

## 7. 常见故障恢复

| 故障场景 | 恢复步骤 |
|---------|---------|
| Pod 频繁重启 | `kubectl describe pod` 查看 OOMKilled/探针失败 → 调资源/调探针 |
| 数据库连接池耗尽 | 检查连接泄漏 → 重启服务 → 定位工具模块 |
| 磁盘写满 | 清理日志 → 配置日志轮转（logrotate） |
| 证书过期 | 更新 cert-manager 证书 → `kubectl rollout restart ingress` |
| API Key 泄露 | 立即吊销 → 更新 Secret → 重启 → 审计日志排查 |

## 8. 安全事件响应

1. **发现异常**：立即吊销可疑 API Key
2. **取证**：从审计日志导出该 Key 的全部调用记录
3. **止血**：更新 Secret + 重启
4. **复盘**：更新告警规则，补充 WAF 规则

---

<p align="center">
  <b>Spring AI MCP Enterprise · Ops Ready</b>
</p>
