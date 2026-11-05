# Uber 管着 800 个 MCP Server，却在"请求体大小"上翻了车？——从官方 Java SDK 的 4MiB 漏洞聊透企业级 MCP 治理

> 适合人群：Java / Spring Boot 后端、在做 Agent 平台或要把存量业务系统接给大模型的团队
> 关键词：MCP Server｜Spring AI｜Java SDK｜CVE-2026-108395｜MCP Gateway｜治理｜RBAC｜审计｜Uber
> 首发建议：掘金 / CSDN / 公众号同步，标题可 A/B：「Uber 800+ MCP Server 治理实录」vs「官方 Java SDK 也有洞：一个 4MiB 引发的 MCP 治理思考」

---

## 一、这周有两条新闻撞在了一起

**第一条是好消息**：11 月 2 日前后，Uber 工程团队的 MCP Gateway 实践被 InfoQ 和一堆行业媒体集中转载。不是 Demo，是跑了几个月的生产系统——**一个网关托管 800+ 个 MCP Server、5,000+ 个工具**，统一做发现、授权、协议转换和执行。

**第二条是坏消息**：11 月 3 日，一个「无界请求体」DoS 被扒出来是**跨所有主流 MCP SDK 的同一类设计缺陷**，其中 **CVE-2026-108395 命中的正是官方 Java SDK 的 `mcp-core` HTTP transport**。TypeScript SDK 月下载约 2.57 亿、Python SDK 约 2.35 亿。更尴尬的是：其中两条 SDK CVE **只在仓库级 advisory 里，没进 GitHub Advisory Database**，所以 `pip-audit`、`osv-scanner`、Dependabot 扫一个有漏洞的 server，**全部返回 clean**。

把这两条放一起看，结论其实挺刺眼：

> 大厂已经把 MCP 治理做成了 800 Server 规模的控制平面；而协议与 SDK 层，连"请求体最多能读多大"都还没写进 best practices。

这篇文章就干一件事：把 Uber 那套治理设计拆开，再用这个 CVE 说明**为什么"升级 SDK"永远补不上治理这一层**，最后给 Java/Spring 工程师一份能直接落地的清单。

---

## 二、Uber 的 800 Server 是怎么管的

先别急着看代码，看组织设计。Uber 那篇里最值钱的不是 Omni MCP 的四个元工具，是这几条**治理约定**：

| 设计 | Uber 的做法 | 为什么必须这样 |
| --- | --- | --- |
| **默认禁用** | 每个 server / tool 注册进来**起始就是 disabled**，归属团队审核后才显式启用；且与运行时权限检查**分离** | 注册 ≠ 授权。否则任何一个团队推个 server 上线，全公司 Agent 立刻能调 |
| **按调用者身份授权** | 依 caller 是 **human / service account / autonomous agent**，套不同的 per-server charter policy 与 per-tool override | 同一个"退款"工具，人点、定时任务调、自治 Agent 自己决定调，风险完全不是一个量级 |
| **请求路径上做治理** | 授权判定和 **PII 脱敏在 request path 执行**；还专门提醒"发现端点要不要过滤掉未授权工具"这个细节不能含糊 | 工具名本身就是信息泄露；脱敏放响应侧已经晚了 |
| **懒加载 schema** | Omni MCP 四个元工具：`discover_server` → `discover_tools` → `get_tool_schema` → `invoke_tool` | 5,000 个工具定义全塞进 prompt，用户还没提问上下文就爆了 |
| **Response Projection** | 类 GraphQL 的字段裁剪，遗留服务的大 JSON 先瘦身再回给模型 | 省 token，也缩小幻觉面 |
| **可观测串得起链路** | 必须能从 `discover_server` 一路追到 `invoke_tool`，否则策略错配无法定位 | 治理出了问题是"静默失败"，没链路等于没治理 |

Uber 自己给的那句总结我特别认同：**MCP Gateway 不只是协议路由器，它是产品管理平台**——工具所有者变成发布者，安全团队变成审批者，Agent 开发者变成消费者。

顺带一提，另一篇 11 月的企业网关横评里，Bifrost 的 virtual key 也是 **deny-by-default：没有 `mcp_configs` 的 key 拿到 0 个工具**。两家独立实现撞出同一个共识，说明这不是 Uber 的特殊癖好，是规模化的必要条件。

---

## 三、CVE-2026-108395：一个"没上限"引发的连锁反应

漏洞本身朴素到有点好笑：HTTP transport 读请求体时**没有大小上限**，一个超大或永不结束的请求就能把进程打挂，无需认证、无需凭据、无需用户交互，可脚本化反复打。纯可用性攻击，但对一个被其他系统依赖的 Agent 后端来说，"随时能被打下线"就是全部问题。

修复方式各家不约而同：**transport 安全层加 4 MiB 默认上限**（`DEFAULT_MAX_REQUEST_BODY_SIZE` / `DefaultMaxRequestBodyBytes`）。同族编号还有 Py 的 CVE-2026-109527、npm mcp-framework 的 CVE-2026-39313，以及 `rmcp`、`mcp-go`、TypeScript SDK——**同一个洞，被独立重新发现并各修了六七遍**。

为什么会这样？因为 **MCP 规范层从来没规定请求体上限**。截至 11 月 3 日，官方 security best-practices 文档里依然没有这一条；NSA / CISA 那份 MCP 信息表也只把"资源耗尽 DoS"当一般风险提一句，指不到具体控制项。规范没写，SDK 就各修各的、默认值还不一样。

Red Hat 把它评为 Important、评估了 44 个产品、发了 3 个 errata 和机器可读 VEX，Debian 标 grave，10 月起几百个 "bump mcp" 的 PR 陆续合入。**但开发者侧的工具链是瞎的**：两条 SDK CVE 从未晋升到 GitHub Advisory Database → `pip-audit`、`osv-scanner`、Dependabot、以及包着 pip-audit 的 MCP 专用扫描器，扫出来全是 clean。只有吃自家 CNA/NVD 源的企业发行版跟踪器才看得到。

这周的另外三条也顺手记一下，全在"官方实现层"：

- **CVE-2026-115563 / CVE-2026-104850（均 CVSS 7.5）**：TS/Py SDK 的 OAuth 发现不校验 **issuer**，恶意 server 用伪造 metadata 把 client secret、refresh token、authorization code 导向攻击者端点。无人值守流最危险——没有人类会去瞄一眼那个可疑 URL。Python SDK 1.30.0 / 2.2.0 用 `validate_metadata_issuer()` 先算 expected issuer 再比对。
- **CVE-2026-116635 / GHSA-r6mm-377j-qg8m**：AWS Serverless MCP Server 路径穿越，能影响 Agent 的人可越目录读 JSON，开写权限可越界覆写文件。**官方明确无 workaround，只能升 v0.2.1**。
- **存量史**：`mcp-remote` CVE-2025-6514（437K+ 下载、CVSS 9.6）；Postmark 恶意 MCP server 静默 BCC 每封邮件；Smithery 路径穿越泄露 3,000+ 托管 server 凭据；GitHub MCP server 被注入把私有仓数据写进公开 PR。

---

## 四、为什么"升级到最新 SDK"补不上这一层

这是我想讲的核心。把上面所有洞摊开看，它们其实分布在**三个不同的层**：

```
第 1 层  协议/SDK 实现   → 无界请求体、issuer 未校验、路径穿越
                          ✔ 官方会修，升版本即可（如果扫描器能提醒你）
第 2 层  规范空白        → best-practices 没写 body 上限；
                          两条 CVE 没进 Advisory DB → 工具链看不见
                          ✘ 升版本解决不了，得自己补基线
第 3 层  企业治理        → 谁可以注册 server？默认能不能调？
                          按人/服务账号/Agent 分别给什么权限？
                          写操作要不要人签字？日志能不能防篡改、留 180 天？
                          ✘ 协议里根本没有这些概念，SDK 永远不会有
```

有一份 11 月的企业 Agent 治理指南把第 3 层讲得很直白：**IAM 验不了 Agent 二进制有没有被换、DLP 没有"工具调用 / 决策链 / stdio 本地调用"的原语、API 网关看不到 MCP 工具参数的语义、代码扫描查不出权重篡改和数据集后门**。Agent 是非确定性的、常走 stdio、一个会话里还能换角色——它把传统安全栈的三个前提全打破了。

监管那边也在收口：NIST AI RMF、EU AI Act、CMMC L2/3、HIPAA、SR 11-7、21 CFR Part 11 现在都要 provenance + 访问控制 + 人类监督 + 防篡改记录。指南里有句原话很适合贴在标书里：

> **"We trust the model provider" is not an audit response.**

所以定位应该说清楚：**官方 SDK 修的是协议实现层的洞；就算全修完，逐工具授权、默认禁用、HITL、防篡改审计这些仍然是协议之外的独立控制平面**。这也是我一直把这类框架叫「治理增强层」而不是「更安全的 SDK」的原因。

顺带说个市场侧的印证：11 月 5 日同一天，ISI Markets（4,000 万+ 新闻、年 50 万份研报、200+ 经济体宏观数据）和 Agiloft（合同生命周期管理）都上线了 MCP Server。两家发布稿不约而同写的是 **"Governed access"、按客户订阅/数据 entitlements/AI 使用权授权、保留既有 permissions 与 controls、写操作必须显式人工确认**。**卖数据、管合同这种最敏感的资产，没人敢上裸 MCP。**

---

## 五、Java / Spring 工程师的落地清单

下面这些不是某个框架的 API 文档，是**验收清单**。你可以拿它对照自己手上的系统，也可以直接抄进技术方案。

### 1）请求体上限与 fail-closed（对应第 1、2 层）

Spring Boot 侧最省事的做法是双层：容器/反代先挡一道，应用内再兜一道。示意：

```java
// 示意代码：Servlet 层请求体硬上限，避免依赖 SDK 默认值
@Component
public class McpBodyLimitFilter extends OncePerRequestFilter {
    private static final long MAX_BODY = 4L * 1024 * 1024; // 4 MiB，与各 SDK 修复共识一致

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        if (req.getContentLengthLong() > MAX_BODY) {
            resp.sendError(413, "MCP request body exceeds 4 MiB");
            return;                                  // fail-closed，不读、不解析
        }
        chain.doFilter(new SizeLimitedRequestWrapper(req, MAX_BODY), resp);
    }
}
```

> 关键点：**别只信 SDK 默认值**。官方 best-practices 没写这条，各 SDK 默认值还可能变；显式配置 + 反代 `client_max_body_size` 双保险，并把 413 计入失败率告警。

### 2）默认禁用 + 审核启用（对应 Uber / Bifrost 共识）

```yaml
# 示意配置：注册与授权分离
mcp:
  registry:
    onboarding-default: DISABLED      # 注册进来一律不可调
    require-owner-review: true        # 归属团队审核后才 ENABLED
    signed-manifest-required: true    # 供应链验证：来源/扫描/未篡改
  gateway:
    discovery:
      filter-unauthorized-tools: true # 发现端点也不泄露未授权工具
```

### 3）按调用者类型分策略（对应 charter policy）

| caller 类型 | 建议默认 | 依据 |
| --- | --- | --- |
| `HUMAN` | 可读；写操作走 HITL 确认 | 有人在场，可担责 |
| `SERVICE_ACCOUNT` | 白名单工具 + 严格配额 + 短时凭据 | 无交互，最易被滥用 |
| `AUTONOMOUS_AGENT` | **默认只读**；写工具单独申请、预算上限、全程审计 | Uber 明确三者策略不同 |

### 4）其他必须有的（验收时逐条打勾）

- [ ] 每个工具标注 **读 / 写** 属性，写操作单独申请、单独 Scope
- [ ] 用户身份透传到工具调用，禁用共享账号
- [ ] 高危动作（发消息 / 删数据 / 转账 / 批量改）**强制人工确认 + 先预览后写入**
- [ ] 每次写操作进 change journal（前后值），支持 `list_changes` / 撤销
- [ ] 入参 / 出参脱敏（PII、密钥），**在请求路径上做**
- [ ] 用户级 + 工具级配额、超时、熔断，防 Agent 自我循环打爆下游
- [ ] 全量审计入参出参、支持会话回放，**关键日志 ≥180 天且不可篡改**
- [ ] 统一入口网关，**禁止绕过网关直连 Server**（MCPHub CVE-2026-120323 就是网关把调用者凭据透传给了攻击者注册的 server）
- [ ] OAuth **issuer / audience 双校验**、凭据 egress 白名单（对应本周 OAuth 双 CVE）
- [ ] 配置变更走代码评审、版本化、灰度；tool schema 与后端做 CI 契约测试（Uber 点名的版本漂移坑）
- [ ] 可观测能把 `discover_server → invoke_tool` 串成一条链路

> 一份中文治理落地指南给的 9 条最小基线和这个几乎一一对应，说明这已经不是"先进实践"，是**基线**。

---

## 六、顺手聊聊这事的"钱景"

如果上面这些让你觉得"那得投入不少"，看看需求侧：

- **招聘**：猎聘 11-01 更新的建发集团 AI 应用架构师 **50–80k（厦门）**，要求"RAG、Skill 开发、**MCP 开发**" + "懂 Java 语法、**Spring Boot/Cloud**"；上海某整车厂 Harness 框架岗 **40–70k·15薪**，职责直接写"**MCP 工具注册 / A2A 协议标准化** + 配额限流 + 隐私权限边界"，要求精通 Java/Kotlin Spring 系。更值得注意的是**金山办公**（武汉）写的是"推进**现有平台能力向 MCP Server 的标准化封装**"、**美团**写的是"**MCP Server 的接入与管理、工具沙箱与权限管控**"——需求已经从"从零搭一个 Server"转向**"把存量 Java 资产安全接给 Agent"**，这正是 Java + Spring 的主场。
- **接单**：Upwork 11-02 上线 Upwork Now 公测，其 MCP server 上线以来**周活涨了 20 倍**；Q3 **AI 技能人才 GSV 同比 +55% 至 1.12 亿美元**，而总 GSV 是 −10% —— 大盘收缩时，这类技能在涨价。乙方真实报价：单独做一个基于既有 API 的 MCP server **$5,000**，含在 MVP 里起步 **$15,000**；带 auth/审计/多租户普遍 **$8K–25K**，多系统写 **$35K–90K**。
- **差异化**：11 月那篇企业网关横评里，Bifrost(Go) / Docker MCP Gateway(MIT) / Microsoft(K8s+APIM) / AWS AgentCore / Kong **没有一家是 Java 栈**，而且 **RBAC、SSO、HMAC 签名审计、guardrails 基本都锁在 Enterprise 付费版**。换句话说，治理是这个行业公认的付费墙；一个开源即给治理能力的 Java/Spring 栈，卡位很舒服。

---

## 七、小结

三句话：

1. **Uber 证明了 MCP 上了规模就必须有控制平面**，且"默认禁用 + 按调用者授权 + 请求路径脱敏 + 懒加载 schema"是跨实现的设计共识。
2. **CVE-2026-108395 证明了协议/SDK 层还会持续出洞**，而且规范空白 + Advisory DB 收录缺失会让你的扫描器"看着很干净"——**升级 SDK 不等于治理**。
3. **11-05 两款受治理 MCP 上线 + 50–80k 的 Java MCP 岗 + Upwork AI 技能逆势 +55%**，说明治理已经从技术选型变成**采购条目、岗位要求和报价分档**。

如果你正在做企业 MCP 平台，建议就从第五节的清单逐条打勾开始——它比选哪个网关更重要。

---

### 相关项目

本系列对应的开源实现：**spring-ai-mcp-enterprise**（Java / Spring Boot 企业级 MCP Server 治理框架，21 模块）
https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise

包含：`mcp-core`（注册/限流/沙箱）、`mcp-governance`（逐工具策略/HITL/参数校验）、`mcp-registry`（准入分级/工具目录）、`mcp-gateway`（egress 白名单/统一入口）、`mcp-auth`（OAuth/RBAC/Scope）、`mcp-tenant`（多租户隔离）、`mcp-audit`（防篡改留痕）、`mcp-monitor`（可观测），以及 Java / Python / Node / Go 四语言 Client SDK、curl 示例、Spring AI Alibaba 兼容层、GitHub Actions CI、Docker Compose 部署与完整中文文档。

> 本文数据抓取窗口 2026-11-03 ~ 2026-11-05，完整来源链接见仓库 `docs/earnings-report-2026-11-05.md`。
