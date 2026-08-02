# 🏪 MCP Marketplace Deployment Guide

> Deploy spring-ai-mcp-enterprise to major MCP ecosystem platforms
> Passive income target: $200-800/month

---

## 📊 Platform Comparison

| Platform | URL | Revenue Share | MAU | Difficulty | Recommended |
|----------|-----|---------------|-----|------------|-------------|
| MCP Marketplace (Nacos) | mcp.nacos.io | TBD | Growing | ⭐⭐ | 🔥🔥🔥 |
| Apify Store | apify.com/store | 80% dev | 500K+ | ⭐⭐ | 🔥🔥🔥 |
| Smithery | smithery.ai | 80-90% | 30K+ | ⭐ | 🔥🔥 |
| MCP Market | mcpmarket.com | TBD | New | ⭐ | 🔥 |
| Self-hosted | Custom | 100% | Depends | ⭐⭐⭐⭐ | 🔥🔥🔥🔥 |

---

## Platform 1: MCP Marketplace (mcp.nacos.io) 🔥🔥🔥

**Why first?** Nacos-backed, Alibaba ecosystem, first-mover advantage for Chinese devs.

### Publish Steps

**1. Build Docker image**
```bash
docker build -t spring-ai-mcp-enterprise:1.0.0 .
docker tag spring-ai-mcp-enterprise:1.0.0 ghcr.io/hh-springai-agent-starter/spring-ai-mcp-enterprise:1.0.0
docker push ghcr.io/hh-springai-agent-starter/spring-ai-mcp-enterprise:1.0.0
```

**2. Deploy to Cloud (recommended: Alibaba Cloud SAE / Tencent CloudBase)**
- Minimum: 1C2G instance, ~¥50-100/month

**3. Register on MCP Marketplace**
- Visit https://mcp.nacos.io, register, submit server.json
- Endpoint: `https://your-domain.com/api/mcp`
- Verify discover endpoint: `GET /api/mcp/discover`

**4. Pricing Strategy**
```yaml
Free: 1000 calls/month (lead gen)
Basic: ¥50/month - 10000 calls
Pro: ¥200/month - 50000 calls + priority support
Enterprise: ¥500/month - unlimited + private deployment
```

---

## Platform 2: Apify Store 🔥🔥🔥

### Publish Steps

1. Create Apify Actor with metadata
2. Configure Dockerfile for Apify compatibility  
3. Set pricing: Free 100/mo → Basic $19/mo → Pro $49/mo → Enterprise $199/mo
4. Expected monthly: $100-400 (first 3 months)

---

## Platform 3: Smithery 🔥🔥

```bash
npm install -g @smithery/cli
smithery deploy  # smithery.yaml already in project root
```

---

## Self-hosted (Cloud Run) 🔥🔥🔥🔥

```bash
gcloud run deploy mcp-enterprise \
  --image gcr.io/PROJECT/spring-ai-mcp-enterprise:1.0.0 \
  --platform managed --region asia-east1 \
  --memory 512Mi --cpu 1 --min-instances 0 --max-instances 5
```
Monthly cost: $0-15 (pay-per-use, scale to zero)

---

## 🎯 Recommended Execution

```
Week 1: Deploy to Apify Store (fastest ROI)
Week 2: Deploy to MCP Marketplace (Nacos)  
Week 3: Sync to Smithery
Week 4: Evaluate self-hosted
Month 2: Start charging
Month 3: Target $200-800/month passive income
```
