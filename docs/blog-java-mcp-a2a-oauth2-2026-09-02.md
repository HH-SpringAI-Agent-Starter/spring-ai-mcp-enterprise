# A2A 网关的"假安全"陷阱：从声明 securitySchemes 到强制校验 Bearer JWT（RFC 6750 实战）

> 作者：Spring AI MCP Enterprise 项目组 ｜ 2026-09-02
> 全文约 3000 字，Java + Spring Boot 3.4 实战，无废话

---

## 一、背景：A2A v1.0 的认证信任模型 = 三层

2026 年 A2A（Agent-to-Agent）协议在 Linux Foundation 治理下一周年：150+ 组织投产，Azure AI Foundry、Bedrock AgentCore、Salesforce Agentforce、SAP、ServiceNow 全部原生支持。A2A v1.0 规范对认证的要求非常明确：

> **HTTPS/TLS 1.2 基线 + OAuth 2.0 授权流程 + 签名 Agent Card** 三层信任模型。

其中第二层 OAuth2，对标的正是 OpenAPI security schemes 的语义——**Agent Card 声明什么鉴权方案，网关就必须真正校验什么**。

但实际项目里最常见的坑是：**Agent Card 里声明了 `oauth2`，网关却什么都没校验**——调用方不带任何令牌也能调工具。这在企业安全评审里属于一眼识破的"假安全"，甚至会被写进渗透测试报告。

## 二、问题解剖：为什么"只声明不校验"是漏洞

看一个典型 Agent Card 声明：

```json
{
  "securitySchemes": [
    { "type": "oauth2", "flows": { "clientCredentials": { "tokenUrl": "/oauth2/token" } } }
  ]
}
```

A2A 编排器（比如 Google ADK、Azure AI Foundry）读到这张卡，会乖乖走 OAuth2 Client Credentials 流程去换令牌——**然后带着令牌来调用你的 `/a2a/rpc`**。

如果网关不校验 `Authorization: Bearer <token>`：

1. 编排器换来的令牌形同虚设（安全预算白花）
2. 任何绕过流程的人直接裸调工具（**无差别数据访问**）
3. 审计日志里无法区分调用者身份（谁调的工具说不清）

一句话：**声明是"告诉别人要怎么安全地调我"，校验是"把不安全的人挡在外面"。两者缺一不可。**

## 三、方案：OAuth2 Client Credentials 发证 + 网关 Bearer 强制校验

我们的架构里有两个模块：

- **mcp-auth**：OAuth2 Client Credentials 令牌端点（`POST /api/auth/oauth2/token`），用 `client_id + client_secret` 签发 HS256 JWT，符合 RFC 6749 §4.4
- **mcp-integrations/mcp-a2a**：A2A JSON-RPC 网关（`/a2a/rpc`、`/a2a/rpc/stream`）

V1.17 要做的事很简单也很关键：**A2A 网关按 RFC 6750 校验 `Authorization: Bearer <JWT>`**，密钥与 mcp-auth 同值即互通——"mcp-auth 发证、A2A 网关验证"的闭环。

### 1. 配置（application.yml）

```yaml
mcp:
  enterprise:
    a2a:
      enabled: true
      # V1.17: OAuth2 Bearer 强制鉴权，与 mcp-auth 同密钥即互通
      jwt-secret: ${MCP_A2A_JWT_SECRET:}
      security-scheme: oauth2
```

### 2. 鉴权模式自动推导

| 模式 | 触发条件 | 校验方式 |
| --- | --- | --- |
| `none` | 默认 | 放行（可置于网关层之后） |
| `api-key` | `api-key` 非空 | `X-A2A-Key` 头 |
| `oauth2` | `jwt-secret` 非空 | `Authorization: Bearer <JWT>`（RFC 6750） |

关键设计：**显式 `security-scheme` 声明的优先级最高**，保证"声明什么就校验什么"，杜绝声明与实现漂移。

### 3. 校验器核心代码（HS256，密钥派生规则与 mcp-auth 完全一致）

```java
public class A2aJwtTokenValidator {
    private final SecretKey signingKey;

    public A2aJwtTokenValidator(String jwtSecret) {
        // 与 mcp-auth McpJwtTokenProvider 相同的密钥派生：HS256 至少 32 字节
        byte[] keyBytes = jwtSecret.length() < 32
                ? Arrays.copyOf(jwtSecret.getBytes(StandardCharsets.UTF_8), 32)
                : jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();   // 返回令牌持有者身份
        } catch (ExpiredJwtException e) {
            return null;                  // 过期
        } catch (Exception e) {
            return null;                  // 签名错误 / 格式非法
        }
    }
}
```

### 4. 控制器鉴权分派

```java
private boolean authorized(HttpServletRequest request) {
    String mode = properties.resolvedAuthMode();
    if ("none".equals(mode)) return true;
    if ("oauth2".equals(mode)) {
        String token = A2aJwtTokenValidator.extractBearerToken(
                request.getHeader("Authorization"));
        if (token == null) return false;            // 缺 Bearer 头 → 401
        String subject = jwtValidator.validate(token);
        if (subject == null) return false;          // 令牌无效 → 401
        request.setAttribute("a2a.subject", subject);
        return true;
    }
    // api-key 模式：校验 X-A2A-Key
    return properties.getApiKey().equals(request.getHeader("X-A2A-Key"));
}
```

未授权响应体按模式给出明确提示：

```
// oauth2 模式
{"jsonrpc":"2.0","id":null,
 "error":{"code":-32009,"message":"Authentication required (Authorization: Bearer <JWT> - RFC 6750)"}}
```

## 四、完整流程演示

```bash
# 1️⃣ 向 mcp-auth 换令牌（Client Credentials）
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=my-service&client_secret=change-me-client-secret" \
  | jq -r .access_token)

# 2️⃣ 无令牌调用 → 401
curl -s http://localhost:8081/a2a/health
# → {"jsonrpc":"2.0","error":{"code":-32009,"message":"Authentication required ..."}}

# 3️⃣ 带 Bearer 令牌调用 → 通过
curl -s http://localhost:8081/a2a/health \
  -H "Authorization: Bearer $TOKEN"
# → {"status":"UP","agent":"MCP Enterprise A2A Gateway","authMode":"oauth2",...}

# 4️⃣ 伪造令牌 → 401
curl -s http://localhost:8081/a2a/health -H "Authorization: Bearer fake.token.value"
# → 401
```

## 五、测试（34 用例全绿）

- 互通性：用与 mcp-auth 相同规则签发的令牌 → 通过
- 过期令牌 → 拒绝；错误密钥 → 拒绝；垃圾输入 → 安全拒绝
- RFC 6750 Bearer 头提取：大小写不敏感、空白容忍
- 控制器层面：三模式判定、401 语义、模式推导优先级

## 六、对企业的意义

1. **安全评审能过关**：声明 oauth2 就真的校验 oauth2，渗透测试挑不出"假安全"
2. **审计可追溯**：`a2a.subject` 携带令牌持有者身份，谁调的哪个工具一目了然
3. **与主流编排器互通**：Azure AI Foundry / Bedrock AgentCore 风格的编排器，按 Agent Card 声明走 OAuth2 流程后即可正确调用

---

**项目开源地址**：github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise
**关注我们**：每周发布一个企业级 MCP/A2A 能力版本 + 市场雷达，欢迎 star / issue / PR。