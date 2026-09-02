# 增收报告 2026-09-02 —— V1.17 A2A OAuth2 Bearer 强制鉴权

> 版本：V1.17 | 发布日期：2026-09-02 | 主题：mcp-auth 深度打通第二步

---

## 一、今日交付

| 交付物 | 类型 | 说明 |
| --- | --- | --- |
| `A2aJwtTokenValidator` | 新类 | HS256 JWT 校验器，与 mcp-auth 令牌互通（密钥派生规则完全一致） |
| `A2aRpcControllerAuthTest` | 新测试（12 用例） | 三模式鉴权、Bearer 有效/过期/伪造、模式推导优先级 |
| `A2aJwtTokenValidatorTest` | 新测试（7 用例） | 互通性/过期/错误密钥/垃圾输入/短密钥补足 |
| `McpA2aProperties` | 改造 | 增加 `jwtSecret`、`resolvedAuthMode()`、`isOAuth2Enabled()` |
| `A2aRpcController` | 改造 | `authorized()` 三模式分派 + RFC 6750 错误语义 + 顺手修 `Map.of(null)` NPE |
| `McpA2aAutoConfiguration` | 改造 | 注入 `A2aJwtTokenValidator` Bean + V1.17 日志 |
| `mcp-a2a/pom.xml` | 改造 | 增加 jjwt 0.12.6 依赖（与 mcp-auth 同版本） |
| `docs/V1.17-release-notes.md` | 新文档 | 发布说明 |
| `docs/blog-java-mcp-a2a-oauth2-2026-09-02.md` | 新文档 | 掘金/CSDN 稿件（3000 字） |
| `docs/market-research-2026-09-02.md` | 新文档 | 市场雷达（8 个高价值 JD + 4 个行业动态） |

## 二、全仓测试

```
mvn test → BUILD SUCCESS
模块：17 个 | a2a 34 用例全绿 | core/auth/tenant/server 等无回归
```

## 三、市场信号与卖点对应

| 市场信号 | V1.17 对应 |
| --- | --- |
| Sumo Logic $207-243K：OAuth/token 交换/多租户隔离/限流配额 | ✅ OAuth2 闭环（V1.17）；多租户（V1.13-14）；限流（已有） |
| Photon/Citi OAuth2+OWASP+MCP client/server | ✅ mcp-auth OAuth2 Client Credentials + A2A Bearer 强制校验 |
| TalentAlly AI Gateway MCP：Entra OIDC/token 生命周期/每用户配额 | ✅ A2A 网关 Bearer 校验 + jwt subject 带入审计 |
| A2A v1.0 认证三层：HTTPS+OAuth2+Signed Card | ✅ OAuth2 强制校验（V1.17）→ Signed Card（V1.18 规划） |
| AAIF 250+ 会员（MCP+A2A 同治理） | ✅ 双协议网关定位与 AAIF RFP 新语言完全对齐 |

## 四、下一步（V1.18）

1. **Signed Agent Card**（A2A v1.2）：密钥对签名 + 域验证，防伪造 Agent Card
2. **OAuth2 scope → MCP 工具级权限**：token scope 映射到 tools:read/tools:write
3. **A2A 推送通知**：task 完成后 Webhook 回调
4. **mcp.so / smithery 注册表提交**：标注 A2A + OAuth2 + SSE 特性