# OAuth2 Client Credentials + EMA 指南（V1.8 → V1.9）

> 企业 AI Agent / 系统间（M2M）调用 MCP 的短时凭证方案，替代长期共享 API Key。
> 对齐 MCP EMA（Enterprise-Managed Authorization）方向：统一鉴权 + 最小权限 + 集中授权。

## 为什么需要它

企业采用 MCP 时，最核心的摩擦点是**鉴权与治理**：
- API Key 长期有效、难以最小化权限、无法自动过期
- 社区 17,000+ MCP Server 大多「可用但非企业就绪」，缺少统一鉴权/RBAC/审计
- EMA 扩展（基于企业 IdP 的集中授权）已被 Anthropic/Microsoft/主流 SaaS 采用

V1.8 提供标准的 **OAuth2 `client_credentials`** 流程 + 可选的 **EMA 外挂接口**。

## 快速开始

### 1. 注册客户端（一次性返回明文 secret）
```bash
curl -s -X POST "http://localhost:8081/oauth2/clients" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "clientId=mcp-agent-1&owner=data-team&roles=user&scopes=tools:read tools:call"
```
返回：
```json
{
  "client_id": "mcp-agent-1",
  "client_secret": "aabbccddeeff...",   // 仅此一次明文，请妥善保存
  "note": "client_secret 仅此一次明文展示，服务端仅存散列"
}
```

### 2. 换取短时 access_token
```bash
curl -s -X POST "http://localhost:8081/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=mcp-agent-1&client_secret=<SECRET>&scope=tools:read tools:call"
```
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "tools:read tools:call"
}
```

### 3. 网关 / 资源服务器校验（RFC 7662 内省）
```bash
curl -s "http://localhost:8081/oauth2/introspect?token=<ACCESS_TOKEN>"
```
```json
{
  "active": true,
  "client_id": "mcp-agent-1",
  "scope": "tools:read tools:call",
  "exp": 1755700000,
  "iat": 1755696400,
  "sub": "data-team",
  "roles": ["user"]
}
```

### 4. 用 Bearer 令牌调用 MCP 工具
```bash
curl -s -X POST "http://localhost:8081/api/mcp/v2/tools/call" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -d '{"name":"system_info","arguments":{}}'
```

## 配置（application.yml）
```yaml
mcp:
  enterprise:
    oauth2:
      enabled: true
      signing-key: ${MCP_OAUTH2_SIGNING_KEY:change-me-in-production}
      token-ttl-seconds: 3600
```
> ⚠️ 生产环境务必通过环境变量/KMS 注入 `signing-key`，切勿使用默认值。

## Java 侧调用

```java
McpOAuth2Manager oauth2 = new McpOAuth2Manager("your-signing-key");
// 注册客户端（只在初始化/后台）
var reg = oauth2.registerClient("agent-1", "data-team", Set.of("user"), Set.of("tools:read", "tools:call"));
// 签发令牌（含 refresh_token）
var token = oauth2.issueClientCredentialsToken("agent-1", reg.clientSecret(), null);
// 校验令牌（网关过滤器中调用）
McpOAuth2Manager.TokenInfo info = oauth2.validateToken(token.accessToken());
```

## V1.9：Refresh Token 轮换 + 重用检测

长期驻留的 AI Agent（如定时任务、后台机器人、企微机器人）持有一个 access_token 跑一周会过期，
每次重新用 secret 换 token 又等于把长期密钥暴露在每台机器上。**Refresh Token 轮换**解决这个问题：

```bash
# 1. client_credentials 签发：响应里带 refresh_token
curl -s -X POST 'http://localhost:8080/oauth2/token' \
  -d 'grant_type=client_credentials&client_id=agent-1&client_secret=<SECRET>' \
  -d 'scope=tools:read tools:call'
# → { "access_token": "...", "refresh_token": "...", "expires_in": 3600, ... }

# 2. access_token 过期后，用 refresh_token 轮换换发（新的 access + 新的 refresh）
curl -s -X POST 'http://localhost:8080/oauth2/token' \
  -d 'grant_type=refresh_token&client_id=agent-1&client_secret=<SECRET>&refresh_token=<REFRESH>'
```

**安全语义：**
- **轮换**：每次刷新换发全新 access+refresh 对，旧 refresh 立即作废——攻击者拿到旧 refresh 也换不到新 token
- **重用检测**：被轮换的 refresh token 再次出现 = 令牌已泄露，**整族吊销**（该家族所有已签发 token 全部失效）
- **哈希存储**：refresh token 仅存 SHA-256 散列，数据库泄露也无法反推
- **吊销**：`POST /oauth2/revoke`（RFC 7009）可主动吊销 access 或 refresh token

## V1.9：网关 Bearer 自动校验（Fail-Closed）

开启后所有非公开路径强制校验 `Authorization: Bearer`，未带/无效一律 401：

```yaml
mcp:
  enterprise:
    security:
      oauth2:
        enforce-bearer: true   # 关闭时（默认）保持 V1.9 前的 X-API-Key 行为，平滑迁移
```

- 公开路径自动放行：`/oauth2/**`、`/api/mcp/health`、`/actuator/**`、`/error`、OPTIONS 预检
- 平滑迁移：未带 Bearer 但带 `X-API-Key` 的存量客户端仍可调用
- 校验通过后，`McpOAuth2Manager.TokenInfo`（clientId / scopes / roles）写入 `request attribute: mcp.tokenInfo`，
  下游 Controller 可做 scope/RBAC 级二次鉴权
- 401 响应带 `WWW-Authenticate: Bearer realm="mcp-enterprise"`（符合 RFC 6750）

## EMA：接入企业 IdP 做集中授权
实现 `McpOAuth2Manager.TokenIntrospector` 并注入，即可把所有令牌校验委托给企业现有身份提供方（OIDC/OAuth2），实现 EMA 集中管理：

```java
oauth2.setExternalIntrospector(accessToken -> {
    // 调用企业 IdP 的 introspection/userinfo 端点
    // 有效返回 TokenInfo，无效返回 null
    return myIdpIntrospect(accessToken);
});
```

## 安全设计
- client_secret 仅存 SHA-256 散列，不落明文
- access_token 为 HMAC-SHA256 签名，防篡改、带过期时间 + 全局唯一 jti（防碰撞/防重放）
- scope 收敛：只授予该客户端已授权的 scope，防止越权
- 令牌可吊销，吊销后立即失效
- refresh token 轮换 + 重用检测，泄露后整族吊销
- EMA 外挂后全部校验委托企业 IdP 集中鉴权
