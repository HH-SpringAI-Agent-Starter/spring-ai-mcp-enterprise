# Earnings Report 2026-08-21 — Spring AI MCP Enterprise

> 板块：企业 MCP Server 框架 | 版本：V1.9（Token Rotation）| 提交：待推送

## 今天做了什么

### 1. 项目现状核查
- 确认 V1.8 已完整落地（OAuth2 Client Credentials + EMA + /oauth2 端点），工作树干净，origin 已同步
- 昨晚任务清单中的 Alibaba 集成 / 客户端 SDK（Java/Python/Node/curl）/ CI / Docker / 文档**均已完成**，无需重复造轮子
- 定位今晚增量：**V1.9 = 「令牌轮换 + 网关强制校验」**（昨天排期中的差异化功能候选）

### 2. 新增 V1.9 功能：Refresh Token 轮换 + 网关 Bearer 自动校验

**mcp-core `McpOAuth2Manager`（+6 测试）：**
- `issueClientCredentialsToken` 同步签发 `refresh_token`（默认 30 天，SHA-256 散列存储）
- 新增 `refreshClientCredentialsToken()`：**轮换**（每次换发全新 access+refresh 对）+ **重用检测**（已轮换的 refresh 再次出现 → 整族吊销，符合 RFC 9700 BCP）
- 新增 `revokeRefreshToken()`（RFC 7009 语义）+ `getRefreshTokenCount()` 统计
- **附带安全修复**：access_token 增加全局唯一 `jti` 声明——修复「同一秒内为同一 client 签发 token 字节相同」的碰撞/重放缺陷

**mcp-server：**
- `McpOAuth2Endpoint`：token 端点支持 `grant_type=refresh_token`；新增 `POST /oauth2/revoke`（RFC 7009，恒返 200 防探测）
- **新增 `McpBearerAuthFilter`**（+7 测试）：`enforce-bearer` 开启后所有非公开路径强制 Bearer 校验（Fail-Closed，401 + WWW-Authenticate）；TokenInfo 写入 `mcp.tokenInfo` attribute 供下游 scope/RBAC 二次鉴权；公开路径自动放行；存量 X-API-Key 平滑迁移

**配置**：`mcp.enterprise.security.oauth2.*` 新增 `refresh-token-ttl-seconds` / `enforce-bearer`

**测试结果**：mcp-core **100 tests 全绿**（+6）、mcp-server Bearer 过滤器 **7/7**、全量 reactor **15 模块 BUILD SUCCESS**

### 3. 文档与内容（SEO/影响力）
- `README.md`：新增「OAuth2 / EMA 企业授权」章节（端点表格 + 配置 + curl 全流程）
- `docs/oauth2-guide.md`：V1.8→V1.9，补 Refresh Token 轮换 / 重用检测 / Bearer 过滤器 / RFC 6750/7009 语义
- `docs/V1.9-release-notes.md`：版本说明
- **`docs/blog-mcp-oauth2-refresh-ema-2026-08-21.md`：掘金/CSDN 投稿稿**（OAuth2 令牌轮换 + 网关强制鉴权实战，含安全清单 + 岗位与薪资数据钩子）
- `examples/curl-examples.sh`：追加步骤 21-23（轮换 / 重用检测演示 / RFC 7009 吊销）

### 4. 市场调研（docs/market-research-2026-08-21.md，今日雷达）
- **岗位**：诺亚财富 MCP Platform Architect（上海，金融级，JD 与本项目功能逐条对应）；上海投资机构 MCP 平台开发工程师 **80-120 万/年 + 股票**；杭州 Spring-AI-Alibaba MCP 岗；海外 Senior MCP Engineer $175-220K
- **报价**：个人接单 $1K-10K/单（Upwork $75-200/hr）；外包公司 $8K-180K；**安全/鉴权/审计是「涨 30-50% 成本」的加价项——正是本项目卖点**
- **结论**：Java+MCP+Spring 是供需缺口；金融合规方向溢价最高；「开源框架展示 + 定向接单变现」双轮策略成立

### 5. 代码推送
- 全量构建通过 → commit（V1.9）→ push 到 GitHub（HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise）

## 为什么做这些

- **V1.9 Token Rotation**：企业 M2M 凭证的「最后一公里」。客户问「access_token 过期了怎么办」时，没有轮换 = 无法生产落地。这是从「演示级」到「生产级」的分水岭
- **网关强制校验**：安全不能靠业务代码自觉；Fail-Closed 过滤器是商业网关产品的核心能力，直接对标 MintMCP 等收费产品
- **jti 修复**：真实安全缺陷（同秒签发 token 相同），修复它让令牌体系达到可审计标准
- **投稿稿 + README**：V1.8 排期中的「掘金/CSDN 投稿」今天落地，SEO 关键词（MCP Server、OAuth2、Refresh Token、Spring Boot），放大开源影响力
- **市场调研**：持续校准「哪些企业在招/什么价」——今天的数据进一步确认 Java 系 MCP 岗位在中国金融/投资圈与欧洲的稀缺性

## 明天（明天 21:30 cron）

1. **V1.10 功能候选**：
   - OAuth2 授权码 + PKCE（浏览器/交互式场景）
   - EMA 企业 IdP 演示适配器（Keycloak/Auth0 一键对接）
   - Redis 化令牌存储（多实例共享签发/吊销，`RedisRateLimiter` 有先例）
2. **投稿发布**：把 `docs/blog-mcp-oauth2-refresh-ema-2026-08-21.md` 实际发布到掘金（若可自动发布则尝试）/CSDN，挂项目链接
3. **Maven Central 推进**：检查 sonatype release profile 状态（docs/sonatype-publishing-guide.md 已有），尝试 `mvn deploy`
4. **变现跟进**：Upwork 挂档（$75-120/hr，MCP Server 开发）；定向投递诺亚（上海）/上海投资机构（80-120 万档）；整理「MCP 接单服务页」素材（对标 $1K-10K 三档定价）
5. **雷达迭代**：追踪 MintMCP 类「MCP 网关治理」创业公司动态，用于商业对标