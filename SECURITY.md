# 安全策略 | Security Policy

## 支持的版本 | Supported Versions

| 版本 | 支持状态 |
|------|---------|
| 0.12.x | ✅ 支持 |
| < 0.12 | ❌ 不推荐 |

## 报告漏洞 | Reporting a Vulnerability

我们非常重视安全性。如发现安全漏洞，请通过以下方式报告：

1. **创建安全 Issue**：[GitHub Security Advisories](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise/security/advisories)
2. **发送邮件**：hh.springai@gmail.com（48小时内回复）
3. **敏感问题**：使用 PGP 加密（公钥见 docs/gpg-public-key.asc）

### 安全措施

**MCP Enterprise 内置多层安全机制：**

- **API Key 认证**：所有 MCP 工具调用需携带 X-API-Key Header
- **SQL 注入防护**：DatabaseQueryExecutor 仅允许 SELECT/WITH 只读查询
- **IP 白名单**：McpSecurityManager 支持 IP 地址过滤
- **速率限制**：内置 RateLimiter 防止滥用
- **审计日志**：所有工具调用记录到 McpAuditLogger

### 安全配置建议

```yaml
mcp:
  enterprise:
    security:
      api-key-enabled: true
      api-keys:
        - key: ${MCP_API_KEY}
          role: admin
      ip-whitelist:
        - "127.0.0.1"
        - "10.0.0.0/8"
      rate-limit:
        enabled: true
        max-requests-per-second: 100
      audit-log:
        enabled: true
```

## 安全更新

安全更新会通过 GitHub Releases 发布，建议订阅仓库更新。

---

*最后更新: 2026-07-24*
