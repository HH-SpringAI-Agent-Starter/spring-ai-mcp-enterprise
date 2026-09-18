# 发布 V1.27：Java 开源 MCP 企业框架的「交付工程」修炼 —— Docker/CI 修复 + Go 客户端 + 市场雷达

> 投稿：掘金 / CSDN · 2026-09-18 · 项目：github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise

## 为什么这次不发新功能，先修「门面」？

开源项目做到 V1.26，框架能力已经覆盖：认证（OAuth2/API Key/JWT）、鉴权（RBAC + 工具级 Scope）、
限流、审计、多租户（行级 + 实例级 + 生命周期）、技能注册表（版本治理/灰度/回滚）、联邦网关、
人类在环审批（HITL）、敏感数据脱敏、A2A 双协议…… 26 个版本，300+ 测试。

但上周 V1.26 埋了一个「隐形炸弹」：新增 `mcp-governance` 模块后，`mcp-server` 开始依赖它，
而 **Dockerfile 里的 pom 拷贝清单没有同步** —— 意味着 `docker build` 会直接失败。
CI 的产物清单同样漏掉了 V1.25/V1.26 的四个新模块。

**"README 说支持 Docker 部署，但 docker build 是坏的" —— 这是开源影响力最大的杀手。**

所以 V1.27 的主题是：**让「克隆即用」的承诺重新成立**。

## 一、Dockerfile 修复：一个 COPY 都不许少

多阶段构建的错误很隐蔽：`COPY pom.xml` 列表少了一行，编译阶段才报 "Child module ... does not exist"，
而且只在 `docker build` 时触发，本地 `mvn` 完全正常。

```dockerfile
# 修复前：缺 mcp-governance / mcp-gateway / mcp-springai-tools
COPY mcp-integrations/mcp-a2a/pom.xml mcp-integrations/mcp-a2a/

# 修复后：依赖链完整
COPY mcp-integrations/mcp-springai-tools/pom.xml mcp-integrations/mcp-springai-tools/
COPY mcp-gateway/pom.xml mcp-gateway/
COPY mcp-governance/pom.xml mcp-governance/
```

教训：**每次新增 Maven 模块，必须同步检查 Dockerfile 的 COPY 列表和 CI 的 artifact 清单。**
项目组可以加一条 release checklist（本仓库已有 enterprise-rfp-checklist，可以继续沉淀）。

## 二、CI 产物补齐：Release 附件不再缺模块

`.github/workflows/maven-ci.yml` 的 upload-artifact 和 gh-release 文件清单，
补上了 `mcp-gateway` / `mcp-governance` / `mcp-a2a` / `mcp-springai-tools` 四个 JAR。
现在打 tag 发布，附件是全量 19 个模块产物，购买方/面试官下载即可用。

## 三、Go 客户端示例：四种语言，一个协议

官方 SDK 生态里 Python/TypeScript 是主流，但企业 Java 侧用户需要自家客户端的参照实现。
仓库原有 Java（2 个）/ Node.js（2 个）/ Python（2 个）客户端示例，V1.27 补上 **Go**：

- 零第三方依赖：仅标准库 `net/http` + `encoding/json`，go 1.21+；
- 与其余语言同构的完整演示：health → connect → list_tools → get_tool → invoke_tool → stats → disconnect；
- 为什么是 Go：本周市场窗口里，Upwork 官方（Lifted #132811）的岗位就是 **Java/Go 二选一**，
  Ciena 的后端 Co-op 也点名 Java/Python/Go —— Go 是「企业 MCP 工程」的高频第二语言。

```go
client := NewMcpEnterpriseClient("http://localhost:8081", os.Getenv("MCP_API_KEY"))
conn, _ := client.connect("go-demo")
tools, _ := client.listTools()
result, _ := client.invokeTool("web_search", map[string]any{"query": "MCP 2026"})
```

## 四、市场雷达 09-18：Java 侧 MCP 需求正在放量

本周 web_search 扫描（原始数据见 docs/market-research-2026-09-18.md）：

| 岗位 | 要求 | 价格 |
|---|---|---|
| OneSeven Technology（远程） | Java + Spring Boot/WebFlux + **MCP 生产经验**，要求 GitHub 作品 | $4,000-5,000/月 |
| Upwork 官方 Lifted #132811 | **Java 或 Go**，构建/运营 MCP 服务 | 全职合同至 2027-03 |
| 沃尔玛中国 | AI/MCP 网关 + MCP Server + **Skill/Spec Registry** | ¥30K-55K/月 |
| Micro1 / PARA AI Labs | C++/Python/**Java**/Go/TS，MCP RL 评测环境 | $60-120/小时，100 名额 |

价格端三个信号：

1. **Upwork 服务产品明码标价**：生产级 MCP Server $750 / $1,500 / $3,500 三档——市场已经分层；
2. **$41 地板价**（C# 单工具外包）——单工具低端已卷成红海，**企业治理能力才是溢价点**；
3. **固定价项目行情**：单工具 $1K-1.5K、多工具 $3K-5K、全流水线 $5K-10K、
   内部套件 $15K-40K、SaaS 产品化 MCP **$25K-80K+**。

## 五、给你的行动清单（如果你想靠 MCP 挣钱）

1. **简历挂开源仓库**：OneSeven 的 JD 明确要求 "GitHub repository or project examples (required)"，
   一个 26 版本演进史 + 300+ 测试的开源框架，就是最硬的交付物证据；
2. **对标 JD 补能力**：沃尔玛的点名要求（MCP 网关 + Skill Registry + 鉴权限流）+ 治理模块
   （HITL + 脱敏 + 审计）= 本框架 V1.21/V1.25/V1.26 逐条命中——**投递前把 release notes 链接附上**；
3. **避开低价红海**：别接 $41 单工具，定位「Enterprise Java MCP + Governance」，
   参考 $1,500-$5,000 中高段，复杂集成 $10K+；
4. **保持迭代**：下一版把 Governance 的 ApprovalStore 做成 JDBC（多实例审批共享），
   直接对标 $15K-$40K 企业级档位。

---

**关于项目**：Spring AI MCP Enterprise —— 企业级 MCP Server 框架（Java/Spring Boot），
涵盖安全（OAuth2/API Key/JWT/RBAC/Scope）、多租户、联邦网关、治理（HITL/脱敏/审计）、
Skill Registry、A2A、可观测性，Apache-2.0 开源。欢迎 star / issue / PR。