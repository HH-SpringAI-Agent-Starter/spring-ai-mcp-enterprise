# Spring AI MCP Enterprise — 生产部署手册 (V1.0)

> 正式发布版生产部署指南：从开发环境到高可用生产环境的完整路径。
> 适用版本：V1.0+ | Java 17+ | Spring Boot 3.4

---

## 1. 部署架构总览

```
                    ┌─────────────────────────────────────┐
                    │          客户端 / AI Agent           │
                    │  (Claude / 通义千问 / DeepSeek / ...) │
                    └──────────────┬──────────────────────┘
                                   │  MCP 协议 (SSE / Streamable HTTP)
                    ┌──────────────▼──────────────────────┐
                    │         API Gateway / Ingress        │
                    │   TLS 终止 · 速率限制 · WAF · 认证    │
                    └──────────────┬──────────────────────┘
                    ┌──────────────▼──────────────────────┐
                    │        MCP Enterprise Server         │
                    │   mcp-server (SSE端点 + 管理API)      │
                    │   mcp-auth (RBAC + API Key + OAuth)  │
                    │   mcp-tools (database/search/system) │
                    │   mcp-monitor (指标+审计+告警)        │
                    └──────┬───────────────┬──────────────┘
                           │               │
              ┌────────────▼───┐   ┌───────▼────────────┐
              │  MySQL (主从)   │   │ Prometheus + Grafana│
              │  审计日志/配置   │   │ 指标采集 + 可视化    │
              └────────────────┘   └────────────────────┘
```

## 2. 环境要求

| 组件 | 最低要求 | 推荐（生产） |
|------|---------|-------------|
| JDK | 17 | 17 LTS (Temurin/Adoptium) |
| 内存 | 512MB | 2GB+ (Xmx1g~2g) |
| MySQL | 5.7 | 8.0+ (主从复制) |
| 部署方式 | Docker | Kubernetes (HPA) |
| 反向代理 | 任意 | Nginx / Traefik / Ingress |

## 3. 构建与镜像

### 3.1 构建可发布 JAR

```bash
# 全量构建（含测试）
./mvnw clean package -DskipTests=false

# 跳过测试快速构建
./mvnw clean package -DskipTests
```

产物：`mcp-server/target/mcp-server-*.jar`

### 3.2 构建 Docker 镜像

```bash
docker build -t mcp-enterprise:1.0.0 .
# 或使用项目内置 Dockerfile
docker build -f mcp-server/Dockerfile -t mcp-enterprise:1.0.0 .
```

### 3.3 镜像安全扫描（发布前必做）

```bash
# Trivy 漏洞扫描
trivy image --severity HIGH,CRITICAL mcp-enterprise:1.0.0

# 确保无 CRITICAL 漏洞后再发布
```

## 4. 配置管理（生产 Profile）

### 4.1 环境变量配置（推荐）

| 环境变量 | 说明 | 示例 |
|---------|------|------|
| `SPRING_PROFILES_ACTIVE` | 激活环境 | `prod` |
| `MCP_SERVER_PORT` | 服务端口 | `8080` |
| `MCP_AUTH_MODE` | 认证模式 | `api-key` |
| `MCP_API_KEYS` | API Key 列表(逗号分隔) | `key1,key2` |
| `MCP_RATE_LIMIT` | 每IP每分钟请求数 | `60` |
| `MCP_AUDIT_ENABLED` | 审计日志开关 | `true` |
| `MCP_METRICS_ENABLED` | 指标采集开关 | `true` |
| `DB_URL` | MySQL 连接串 | `jdbc:mysql://...` |
| `DB_USERNAME` | 数据库用户 | `mcp_prod` |
| `DB_PASSWORD` | 数据库密码 | *(Secret管理)* |

### 4.2 密钥管理（禁止明文）

- **开发/测试**：`.env` 文件（不入库）
- **生产**：K8s Secret / Vault / 云厂商 KMS

```bash
# K8s Secret 示例
kubectl create secret generic mcp-secrets \
  --from-literal=DB_PASSWORD='xxx' \
  --from-literal=MCP_API_KEYS='xxx' \
  -n mcp-enterprise
```

## 5. 部署方式

### 5.1 Docker Compose（单机生产）

```bash
cd deploy
docker-compose -f docker-compose.yml up -d
# 或使用项目根目录 docker-compose.yml
```

检查：`docker-compose ps` → 全部 healthy

### 5.2 Kubernetes（推荐生产）

项目已提供完整 k8s 清单（`k8s/` 目录）：

```bash
# 1. 创建命名空间
kubectl apply -f k8s/namespace.yaml

# 2. 创建 Secret（密钥）
kubectl apply -f k8s/secret.yaml   # 需先创建

# 3. 部署核心资源
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml

# 4. 配置自动伸缩 (HPA)
kubectl apply -f k8s/hpa.yaml
```

### 5.3 HPA 自动伸缩策略

```yaml
# k8s/hpa.yaml 核心配置
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: mcp-enterprise-hpa
  namespace: mcp-enterprise
spec:
  minReplicas: 2        # 最小副本（高可用）
  maxReplicas: 10       # 最大副本
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

## 6. 安全加固清单（生产必做）

| # | 项目 | 操作 | 优先级 |
|---|------|------|:------:|
| 1 | TLS 证书 | Ingress 配置 HTTPS (cert-manager) | 🔴 必做 |
| 2 | API Key | 启用 `MCP_AUTH_MODE=api-key`，禁用匿名 | 🔴 必做 |
| 3 | 速率限制 | 设置 `MCP_RATE_LIMIT`，防滥用 | 🔴 必做 |
| 4 | 数据库 | 最小权限账号，禁止 root | 🟠 建议 |
| 5 | 审计日志 | 开启 `MCP_AUDIT_ENABLED=true` | 🔴 必做 |
| 6 | 容器 | 非 root 运行、readOnlyRootFilesystem | 🟠 建议 |
| 7 | 依赖 | 定期 `mvn dependency-check` 漏洞扫描 | 🟠 建议 |
| 8 | WAF | 网关层启用 WAF 规则 | 🟡 可选 |

## 7. 监控与告警

### 7.1 指标端点

- **Prometheus 指标**：`GET /actuator/prometheus`（若启用 actuator）
- **健康检查**：`GET /actuator/health`
- **工具调用统计**：由 `mcp-monitor` 模块提供

### 7.2 Prometheus 采集配置

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'mcp-enterprise'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['mcp-enterprise:8080']
```

### 7.3 关键告警规则

| 告警 | 阈值 | 级别 |
|------|------|------|
| 服务不可用 | 健康检查失败 5 分钟 | 🔴 P0 |
| CPU 持续高 | >85% 持续 10 分钟 | 🟠 P1 |
| 内存持续高 | >90% 持续 10 分钟 | 🟠 P1 |
| 工具调用错误率 | >5% 持续 5 分钟 | 🟠 P1 |
| 5xx 响应 | 单分钟 >10 次 | 🟠 P1 |
| API Key 滥用 | 单 Key 超限 3 次 | 🟡 P2 |

## 8. 升级与回滚

### 8.1 滚动升级

```bash
# K8s 滚动更新
kubectl set image deployment/mcp-enterprise \
  mcp-enterprise=registry.example.com/mcp-enterprise:1.0.1 \
  -n mcp-enterprise

# 监控滚动状态
kubectl rollout status deployment/mcp-enterprise -n mcp-enterprise
```

### 8.2 回滚

```bash
# 回滚到上一版本
kubectl rollout undo deployment/mcp-enterprise -n mcp-enterprise

# 回滚到指定版本
kubectl rollout undo deployment/mcp-enterprise \
  --to-revision=3 -n mcp-enterprise
```

## 9. 发布检查清单（V1.0 Go/No-Go）

- [ ] 全量测试通过 (`./mvnw test`)
- [ ] 镜像漏洞扫描无 CRITICAL
- [ ] TLS 证书配置完成
- [ ] API Key 认证启用并验证
- [ ] 速率限制生效
- [ ] 审计日志验证（关键操作可追溯）
- [ ] 监控指标采集正常
- [ ] 告警规则已配置并测试
- [ ] 备份策略就绪（数据库 + 配置）
- [ ] 回滚方案演练通过

---

## 10. 故障排查速查

| 症状 | 排查方向 |
|------|---------|
| 连接超时 | 网络/防火墙/Ingress 路由 |
| 401 Unauthorized | API Key 错误/过期/未配置 |
| 429 Too Many | 速率限制触发，调大 `MCP_RATE_LIMIT` |
| 500 错误 | 查看日志 `kubectl logs` / Docker logs |
| 工具调用失败 | 检查目标数据库/API 连通性 |
| 内存溢出 | 调大 Xmx，检查工具是否泄漏连接 |

---

<p align="center">
  <b>Spring AI MCP Enterprise · V1.0 Production Ready</b>
</p>
