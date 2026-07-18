# Kubernetes 部署指南

## 前置条件
- Kubernetes 1.24+
- kubectl 配置已连接集群
- (可选) Ingress Controller (nginx-ingress)
- (可选) cert-manager (HTTPS)

## 部署

```bash
# 一键部署全部资源
kubectl apply -k k8s/

# 或按顺序部署
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml
kubectl apply -f k8s/hpa.yaml
```

## 验证

```bash
# 查看 Pod 状态
kubectl get pods -n mcp-enterprise

# 查看 Service
kubectl get svc -n mcp-enterprise

# 测试健康检查
kubectl port-forward -n mcp-enterprise svc/mcp-server 8081:8081
curl http://localhost:8081/api/admin/health
```

## 扩缩容

```bash
# 手动扩容
kubectl scale deployment mcp-server -n mcp-enterprise --replicas=5

# 查看 HPA 状态
kubectl get hpa -n mcp-enterprise

# 查看 HPA 详细
kubectl describe hpa mcp-server -n mcp-enterprise
```

## 卸载

```bash
kubectl delete -k k8s/
```
