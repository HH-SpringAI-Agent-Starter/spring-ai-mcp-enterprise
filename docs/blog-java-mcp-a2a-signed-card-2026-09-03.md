# A2A 安全三部曲终章：签名 Agent Card（Signed Agent Card）实战

> 发布于：2026-09-03 · 作者：MCP Enterprise 开源项目组
> 系列：MCP+A2A 企业级网关实战（V1.15 网关 → V1.16 SSE → V1.17 OAuth2 → V1.18 签名卡片）
> 开源项目：github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise

---

## 引子：一张没人验真的"身份证"

企业部署 A2A（Agent2Agent）时，编排器靠什么发现能力？答案是 **Agent Card**——发布在 `/.well-known/agent-card.json` 的 JSON 文档，声明"我是谁、我能干什么、怎么鉴权"。

但这里有一个被绝大多数教程忽略的问题：**Agent Card 默认不签名。**

攻击者注册一个相似域名、发布一张伪造的卡片，宣称自己是指数计算工具。编排器动态发现能力时若不做来源校验，就会把真实任务负载路由到恶意服务器——这正是 BeyondScale《A2A Security: CISO Guide》列出的五大攻击模式之首：**Agent Card Spoofing & Impersonation**。

A2A v1.0 GA（Linux Foundation，2026-04）把 **Signed Agent Cards（JWS + JSON Canonicalization）** 列为官方特性，签名卡片从"可选加固"变成了"企业部署的供应链安全基线"。不签名 = 供应链漏洞。

本文用一个开源项目（Spring AI MCP Enterprise）的 V1.18 实现，讲清楚三件事：

1. 签名 Agent Card 到底签的是什么、怎么签
2. 和 OAuth2 网关鉴权是什么关系（为什么不是二选一）
3. 客户端如何一行代码验签

## 一、签的是什么：能力声明的完整性 + 真实性

Agent Card 是 JSON 文档，签名要解决两个问题：

- **完整性（Integrity）**：卡片在传输途中被篡改（比如把 endpoint 换成恶意地址）
- **真实性（Authenticity）**：卡片确实出自持有密钥的一方（防伪造卡片）

实现上采用 **JWS Compact Serialization（RFC 7515）**：

```
base64url(header) . base64url(canonicalCardJson) . base64url(HMAC-SHA256(signingInput, key))

header = {"alg":"HS256","typ":"JWS","kid":"mcp-a2a-1"}
```

关键细节是 **规范化（Canonical JSON）**：签名前必须确定性序列化（sorted keys + 无空白），否则"同一张卡片"在不同语言里序列化结果不同，签名就无法跨语言复现。我们的实现里用 Jackson 的 `ORDER_MAP_ENTRIES_BY_KEYS` 保证这一点，并用固定时钟的单测验证"两次签名逐字节一致"。

## 二、和 OAuth2 的关系：三层安全，缺一不可

A2A 的安全模型是三层：**HTTPS 基线 + OAuth 2.0 授权 + 签名 Agent Card**。很多人问：有了 OAuth2 为什么还要签名卡片？

答案是两者管的东西不同：

| 层 | 解决的问题 | 谁验证 |
| --- | --- | --- |
| OAuth2 (Bearer JWT) | **调用方**是不是有权限调我的 RPC | 被调方（网关） |
| Signed Agent Card | **能力声明**是不是真的出自这个 agent | 调用方（编排器） |

V1.17 我们实现了网关强制校验 `Authorization: Bearer <JWT>`（RFC 6750），解决"编排器调网关"；V1.18 的签名卡片解决"编排器信任网关的声明"。一进一出，闭环才完整。

而且——项目里这一把钥匙可以三用：`mcp-auth` 的 Client Credentials 端点用它签发 JWT，A2A 网关用它验 Bearer，Agent Card 用它签名。配置同值即可：

```yaml
mcp:
  enterprise:
    auth:
      jwt-secret: ${JWT_SECRET}          # 1. mcp-auth 发证
    a2a:
      jwt-secret: ${JWT_SECRET}          # 2. A2A 网关验签
      card-signing-key: ${JWT_SECRET}    # 3. Agent Card 签名（V1.18）
      card-key-id: mcp-a2a-1
```

密钥派生规则三者完全一致（不足 32 字节补齐到 32 字节，HS256 最小长度），不存在"多把钥匙不同步"的运维噩梦。

## 三、实战：签名、发现、验签

### 1. 服务端返回签名信封

配置好 `card-signing-key` 后，`GET /a2a/agent-card` 返回：

```json
{
  "agentCard": { "name": "MCP Enterprise A2A Gateway", "skills": [...] },
  "signature": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXUyIsImtpZCI6Im1jcC1hMmEtMSJ9.eyJ... .<sig>",
  "algorithm": "HS256",
  "keyId": "mcp-a2a-1",
  "signedAt": "2026-09-03T13:30:00Z"
}
```

同时响应头带 `X-Agent-Card-Signature`，兼容纯 header 传递的消费方。还有个 `GET /a2a/agent-card/verify` 端点做自验证，方便巡检脚本。

安全细节：

- **防算法混淆**：header 里 `alg` 不是 HS256（比如 `alg=none`）直接拒绝
- **常量时间比较**：用 `MessageDigest.isEqual`，防时序侧信道
- **防御性降级**：签名异常时退回原始卡片（fail-open），服务可用性优先

### 2. 客户端一行验签

编排器拿到信封后，用共享密钥验签：

```java
// 静态方法，无需构造签名器
A2aAgentCardSigner.VerificationResult r =
        A2aAgentCardSigner.verify(envelope.signature(), secret);

if (r.valid()) {
    A2aAgentCard card = r.toCard();   // 校验通过才可用
    // 继续正常的 OAuth2 token 交换 → 调 RPC
} else {
    throw new SecurityException("Agent Card 签名校验失败: " + r.error());
}
```

校验通过意味着：卡片内容未被篡改（完整性）、确实出自持有密钥的网关（真实性）、`kid` 告诉我们用哪把钥匙验的（密钥轮换）。

### 3. 测试护航

9 个新增单测覆盖：JWS 三段结构、往返验证、篡改拒绝、错钥拒绝、`alg=none` 拒绝、畸形 JWS 拒绝、确定性签名、短密钥补齐兼容、静态/实例校验一致性。模块 43 用例全绿。

## 四、对外怎么讲（面试 / 投标版）

1. **讲演进，不讲功能**：V1.16 声明 securitySchemes → V1.17 强制校验 Bearer → V1.18 签名卡片。这个"声明 → 强制 → 供应链签名"的路径，正好复刻了整个行业对 A2A 安全的理解过程，讲出来就是"我踩过坑、知道为什么"。
2. **对标规范原文**：A2A v1.0 GA 官方特性清单（多租户、Signed Cards、安全澄清）逐条有落点。
3. **对标 CISO 语言**："Agent cards are unsigned by default" 是攻击面；签名卡片 + mTLS + OAuth2 是企业控制项——你做的不是功能，是**供应链安全基线**。

## 五、下一步

- **工具级 scope 映射**：OAuth2 token scope → MCP 工具级权限（多个 JD 点名 per-user scoping）
- **Upwork MCP Server 示例**（官方 API + OAuth）：职位扫描 + 提案生成
- 提交 mcp.so / smithery 注册表并打标"A2A + OAuth2 + SSE + Signed Card"

---

**项目地址**：github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise（V1.18，43 测试全绿，欢迎 star / issue / PR）

**系列前作**：
- V1.15 双协议网关：MCP 工具注册中心 → A2A Agent Card
- V1.16 A2A SSE 流式：message/stream + task/resubscribe
- V1.17 OAuth2 强制鉴权：RFC 6750，mcp-auth 令牌互通