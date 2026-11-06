# 一个字符的 Bearer Token 打穿 MCP 网关：Wiz 90 天蜜罐实录，与 Java/Spring 该把责任放在哪一层

> 适合发布：掘金 / CSDN / 知乎 / 公众号（技术管理向）
> 关键词：MCP Server 安全、MCP Gateway 鉴权绕过、Wiz 蜜罐、LiteLLM CVE、Spring AI 2.0、Java 企业级治理、广发证券 MCP 招标
> 配套开源项目：[spring-ai-mcp-enterprise](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise)（Apache-2.0，21 模块）
> 数据截止：2026-11-06

---

## 先说结论

这周有两件事同时发生，放在一起看才有意思：

1. **攻**：Wiz.io 放了 90 天蜜罐，模拟 LiteLLM / MCP / LangChain / Flowise / Langflow / OpenWebUI / Node-RED，记录到**定向且持续**的攻击。最扎心的一条是 `CVE-2026-59822` —— LiteLLM 的 **MCP Gateway 鉴权绕过，一个字符的 bearer token 就能通过 MCP 函数校验**。配合测试端点的命令注入和 Starlette host-header 绕过，串成**完全未认证的 RCE**，外部研究者把这条链归到了 **Qilin 勒索团伙**。
2. **买**：**广发证券在 3 天里连发两个把 MCP 写进核心建设内容的供应商征集**（11-04 企业微信项目把「MCP Server 设计与开发适配能力」写进供应商资质门槛，11-06 智能金融资讯数据服务平台把「MCP 工具协议 + Skill + 子 Agent 编排 + 混合检索」写成券商级平台底座，**11-13 17:30 截止**）；山东重工集团级大模型平台二次招标（11-20 截止）明确要「统一入口、统一服务、统一管理、**统一安全管控**」；重庆 3.4412 亿 AI 大单 11-03 定标，范围含智能体应用场景。

**一边是攻击者把 MCP 网关当云入口打，一边是券商把 MCP 当平台底座买。中间缺的那一层，就是治理。**

更巧的是，11-06 一篇讲 Spring AI 2.0 三条落地路径的文章里有句话，我觉得是今年最值得抄进架构评审纪要的一句：

> **「选型的第一原则不是哪个框架功能强，而是哪条路径把责任放在我能治理的那一层。」**

这篇文章就做三件事：**把攻击路径拆开 → 逐条映射到 Java/Spring 能落的控制点 → 给出能直接抄的配置与代码骨架**。

---

## 一、90 天蜜罐到底打到了什么

Wiz 的蜜罐不是扫端口，是完整模拟了生产部署，所以看到的是**成体系的入侵链**，不是噪点。

| 阶段 | 攻击者做了什么 | 为什么能成 |
| --- | --- | --- |
| ① 进门 | `CVE-2026-59822`：MCP Gateway 接受**一个字符**的 bearer token | 网关把「有 token」当成了「token 有效」，没有 fail-closed 的强校验 |
| ② 落地 | `CVE-2026-42271`：伪造一份 MCP server 配置，`command` 字段塞进 Python downloader + 矿机；**连通性测试照样返回正常握手** | 「注册/测试」这一步被当成可信运维操作，没做参数校验和沙箱 |
| ③ 提权 | 串 `CVE-2026-48710`（Starlette host-header 绕过）→ 未认证 RCE | Host/Origin 校验本该在网关层，业务层兜不住 |
| ④ 捞凭据 | 在已控主机上**直接查询运行中的 Python 进程**，取回 LiteLLM proxy master key —— 磁盘上根本没有这个文件 | 长驻主密钥 + 集中式代理 = 一个高价值凭据仓库 |
| ⑤ 横向 | 列后端模型，挑 API-key 盗窃或盗用付费模型额度 | 一个代理集中了多家模型厂商的访问权限与云权限 |
| ⑥ 隐蔽 | 盲提示注入诱导 agent 执行 shell，DNS 带外回调确认；Base64 + Pastebin 降可见性；XMRig 藏在 `.claude/unicorn` 里伪装 AI 开发工具 | 监控只看 HTTP 状态码，没看 shell 派生、异常子进程、出站连接 |

Wiz 给防守方的结论很直白：**把任何 AI 代理/网关当作「凭据与云权限存储」对待** —— 强制认证、最小权限、限制出站、快速打补丁、监控 shell 派生 / 压缩包下载 / 异常子进程。

请注意第 ② 步：**握手是正常的**。这类攻击不会在你的错误日志里留下痕迹，只有「工具注册审没审」「配置参数验没验」能拦住它。

### 同一周 NVD 补了四刀

| CVE | 发布 | 根因 | CVSS |
| --- | --- | --- | --- |
| `CVE-2026-123272` / `123271` | 11-05 | mcp-go / ChatCLI **OAuth resource confusion**：客户端让服务端自己指定 token 的 `resource`，没做 RFC 9728 §3.3 的身份校验 → 恶意 server 拿同授权服务器下另一 resource 的 token 重放 | 8.0 |
| `CVE-2026-111769` | 11-05 | 官方 TS SDK `<1.32.0` 跨源**重定向保留自定义头与 `mcp-session-id`**，307/308 还重发 body（含 `refresh_token` / `client_secret`） | 6.5 |
| `CVE-2026-121458` | 11-03 | Instatic：MCP 内容读取工具**跳过**主 HTTP API 已实现的对象级授权，低权用户读他人私有草稿 | 7.1 |
| `CVE-2026-121419` | 11-03 | SurrealDB 3.1.0~3.3.0：MCP 会话指纹按「access method 名」而非用户推导 → 同方法用户会话可互相劫持 | 8.6 |

再算上 IBM ContextForge MCP Gateway 近 90 天的 **7 个 CVE**（含默认凭据认证绕过 **9.8 critical**），可以得出一个比单个漏洞更有用的结论：

> **MCP 是「审核更少的那条接口」。** 主 API 早就做对的检查——对象级授权、会话绑定用户、audience 校验、同源校验——在 MCP 通道里被重新实现了一遍，然后漏了。
> 这不是编码疏漏，是**规范阅读习惯**问题：实现者接好了 RFC 9728 discovery 和 RFC 8707 resource indicator，唯独漏了「验证元数据描述的是我实际访问的那台 server」——因为测试环境里元数据永远是对的。

所以「升级官方 SDK ≠ 安全」这句话，到这周已经可以说得更精确：**升级官方 SDK 只能修实现层的洞，修不了「谁负责治理」这件事的归属。**

---

## 二、买方其实早就把答案写出来了

过去一年我们论证「企业需要 MCP 治理」，靠的是 CVE 数字和合规条文。这周不用论证了，**采购方和雇主自己把它写进文件了**：

- **广发证券（11-04 征集）**：MCP Server 五件套 —— 工具定义 / 参数校验 / **鉴权限权** / 调用限流 / 日志可观测，加上多租户隔离与**防篡改留痕**，直接作为供应商资质门槛。
- **广发证券（11-06 征集，11-13 17:30 截止）**：MCP 工具协议 + Skill 技能包 + 子 Agent 编排 + 长期/会话记忆 + 混合检索，做**券商级**智能金融数据服务平台，还要求「能力组件可外部产品化」。
- **山东重工（11-20 截止）**：集团级大模型平台底座，「统一入口、统一服务、统一管理、**统一安全管控**」，模块里明列**大模型安全护栏**。
- **周大福珠宝集团（中国香港团队）招聘 JD**：「Develop a shared tool layer using MCP ... with consistent **identity propagation, access controls and audit trails**」+ 模型网关按数据地域路由 + 配额成本控制。**identity propagation / access controls / audit trails 三个词，就是治理层的三个模块。**
- **Spring AI 2.0 三条路径文章（11-06）**列出的生产约束：工具级授权、按租户/用户/工具/模型分别限流、成本按调用链归集可对账、提示词/检索片段/工具入参出参/决策依据**全留痕**、分级超时、降级出口、幂等键与人工复核。

最后这份清单，几乎就是企业级 MCP 治理框架的功能目录。**不是我们自夸，是第三方按生产约束独立推导出来的同一份清单。**

---

## 三、Java / Spring 落地清单：七条控制点，逐条对上攻击路径

下面每一条都对应上面某个真实攻击，不是 checklist 凑数。完整可参考实现放在仓库 [`examples/governance-hardening/`](../examples/governance-hardening/README.md)（示例片段，不进主 reactor，零构建风险）。

| # | 控制点 | 对应攻击 | 落地位置 |
| --- | --- | --- | --- |
| 1 | **鉴权 fail-closed**：token 必须完整校验签名 / aud / iss / exp / scope，任何异常直接 401；不接受「有 token 就算过」 | ① 一个字符 bearer | `mcp-gateway` + `mcp-auth` |
| 2 | **请求体上限 4 MiB + 拒绝跨源重定向**：`Content-Length` 与流式读取双限，超限 413；客户端禁跟随跨源 307/308 | ③ / 11-05 官方 Java SDK 全 SDK DoS / `111769` | `mcp-gateway` Filter + 客户端配置 |
| 3 | **工具注册默认禁用 + 配置参数校验 + 沙箱**：新 server/tool 进来 `enabled=false`，审核后显式启用；测试端点不允许执行 `command` 类字段 | ② 伪造配置握手正常 | `mcp-registry` + `mcp-governance` |
| 4 | **会话按用户身份绑定**：fingerprint = `tenantId + subject + clientId`，**绝不用 access method 名** | ④ `121419` 会话劫持 | `mcp-tenant` + `mcp-auth` |
| 5 | **工具级 ACL 复用业务授权**：MCP 通道不另写一套宽松逻辑，直接调主 API 同一套对象级授权 | ④ `121458` 越权读草稿 | `mcp-governance` |
| 6 | **按 caller 差异化授权 + HITL**：human / service account / autonomous agent 不同策略；写操作强制人工确认 + 预览 + 结果报告 | Uber 800+ servers 生产实证 / Agiloft 写操作确认 | `mcp-governance` + `mcp-audit` |
| 7 | **可观测覆盖进程行为**：shell 派生、异常子进程、非预期压缩下载、出站连接白名单、凭据访问审计 | ④ 内存抠 master key / ⑥ 隐蔽矿机 | `mcp-monitor` |

### 最小骨架（示意，接口名以实际版本为准）

```java
/**
 * MCP 网关前置过滤器：演示 4 MiB 上限 + fail-closed + 同源校验。
 * 参考实现，非生产代码；完整版见 examples/governance-hardening/
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class McpGatewayGuardFilter extends OncePerRequestFilter {

    private static final long MAX_BODY_BYTES = 4L * 1024 * 1024; // 4 MiB，对齐官方 SDK 修复口径
    private final TokenVerifier verifier;      // 校验签名/aud/iss/exp/scope
    private final CallerPolicy  policy;        // 按 caller 差异化授权
    private final AuditSink     audit;         // 全留痕

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {

        // (1) 请求体硬上限：声明值与实际读取都要拦，避免流式绕过
        if (req.getContentLengthLong() > MAX_BODY_BYTES) {
            audit.reject(req, "BODY_LIMIT", "declared=%d", req.getContentLengthLong());
            resp.sendError(413, "MCP request body exceeds 4 MiB");
            return;                                   // fail-closed
        }

        // (2) 同源：不接受跨源 Host / 不跟随跨源重定向
        if (!SameOrigin.isAllowed(req)) {
            audit.reject(req, "CROSS_ORIGIN", "host=%s", req.getHeader("Host"));
            resp.sendError(403, "cross-origin MCP request denied");
            return;
        }

        // (3) 鉴权必须完整校验，任何异常一律 401，不降级放行
        Principal caller;
        try {
            caller = verifier.verify(req.getHeader("Authorization")); // sig/aud/iss/exp/scope
        } catch (AuthException e) {
            audit.reject(req, "AUTH_FAIL", e.getMessage());
            resp.sendError(401, "invalid bearer token");
            return;
        }

        // (4) 会话指纹必须绑到「租户 + 用户主体」，而非 access method 名
        String sessionKey = caller.tenantId() + ":" + caller.subject() + ":" + caller.clientId();

        // (5) 工具级决策：默认禁用 → 审核启用；缺 scope 直接拒绝
        String tool = req.getHeader("X-MCP-Tool");
        if (!policy.isToolEnabled(tool) || !policy.hasScope(caller, tool)) {
            audit.reject(req, "TOOL_DENIED", "tool=%s caller=%s", tool, caller.subject());
            resp.sendError(403, "tool not enabled or scope missing");
            return;
        }

        // (6) 写操作强制 HITL：先落待审批，不直接执行
        if (policy.isWrite(tool) && !policy.approvalGranted(req)) {
            String ticket = audit.pending(req, tool, caller);      // 生成审批单
            resp.setStatus(202);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"status\":\"PENDING_APPROVAL\",\"ticket\":\""
                                   + ticket + "\"}");              // 审批流回调后放行
            return;
        }

        try {
            chain.doFilter(new BoundedRequestWrapper(req, MAX_BODY_BYTES, sessionKey), resp);
        } finally {
            audit.record(req, tool, caller, resp.getStatus());     // 入参/出参/决策依据全留痕
        }
    }
}
```

> ⚠️ 上面是**结构示意**（`TokenVerifier` / `CallerPolicy` / `AuditSink` 为项目内接口，需按实际模块注入）。重点是三件事：**超限/异常一律 fail-closed**、**工具默认禁用**、**会话绑到用户主体**。完整参考片段与 `application.yml` 见仓库示例目录。

对应的配置片段：

```yaml
mcp:
  gateway:
    auth:
      mode: bearer
      fail-closed: true            # 校验异常绝不降级放行
      verify: [signature, audience, issuer, expiry, scope]
      same-origin-only: true       # 禁跟随跨源 307/308
    limits:
      max-request-body: 4MiB       # 对齐 CVE-2026-108395 统一修复口径
      read-timeout: 30s
  registry:
    default-enabled: false         # 新 server/tool 默认 disabled，审核后显式启用（对标 Uber）
    validate-config: true          # 禁止 command / shell 类字段进入测试端点
  governance:
    tool-policy:
      enforce: true                # 缺 scope 直接拒绝
      write-requires-approval: true
    session:
      fingerprint: tenant+subject+clientId   # 绝不按 access method 名
  monitor:
    watch: [shell-spawn, child-process, outbound-egress, credential-access]
```

---

## 四、这套东西值多少钱

不只是「安全做得好」，是**有人正在按这个维度付钱**：

| 场景 | 价格带 | 出处 |
| --- | --- | --- |
| 广发证券智能金融资讯数据服务平台（供应商征集） | 11-13 17:30 截止，预征集未公开 | 广发证券采购平台 |
| 山东重工集团级大模型平台（二次招标） | 11-20 截止，接受联合体 | 必联网 |
| 重庆 AI 大单（含智能体应用场景，11-03 定标） | **3.4412 亿元** | 中标候选公示 |
| 央企「MCP 注册中心」标（ZC26G280436，已落地） | **683 万**，治理子项约 20% ≈ 137 万 | 中标公告 |
| Upwork 企业 MCP 网关交付 | **$15K–40K**；定制平台 $30K–80K | Upwork 官方定价页 |
| MCP 实现时薪 | **$150–300/hr** | UpHunt 2026 指南 |
| 国内 Java+MCP 岗 | 建发 **50–80k**、上海整车厂 **40–70k·15 薪**、猎聘 **35–55k·16 薪** | 猎聘/智联 11 月 |

**报价方法论比单价更重要**：Upwork Q3 财报显示 AI 技能人才 GSV **+55% 至 $112M**，而总 GSV **−10%** —— 大盘在缩，钱只往「帮客户把 AI 塞进业务流程并验收」集中。**按「编排者」报价，不按「写代码的」报价，同样工作量差 2–3 倍。**

还有一条来自本地财富机会雷达的判断，和今天所有信号能对上：**「不能上云的钱最好赚」**。私有化 + 等保 + 密评 + 信创已经是政务/金融标的标配预算项，而这类标恰恰是 **Java + Spring AI 的主场**——Python 系在这类交付里反而是弱项。

---

## 五、写在最后

MCP 的标准化争论其实已经结束了。Snowflake 跑托管 MCP Server 并专门收购了一家 MCP 治理公司，Dremio 用 MCP 暴露受治理的 Iceberg 数据，A2A 1.0 有 150+ 生产组织。**现在的批评不再是「MCP 会不会赢」，而是「赢了之后的上下文膨胀、注入面、治理归属」。**

所以别再纠结「用哪个 SDK」了。真正该问的是那句话：

> **哪条路径，把责任放在我能治理的那一层？**

如果你现在的 MCP Server 是「官方 SDK + 一个 API Key」直接挂在内网/公网，建议今晚就做三件最小的事：**给请求体加 4 MiB 上限、把鉴权改成 fail-closed、把新注册工具改成默认禁用**。这三件不依赖任何框架升级，一个 Filter 就能落地。

项目地址（Apache-2.0，21 模块，含 Docker Compose 与 CI）：
**https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise**

---

### 参考来源（11-03 ~ 11-06）

- Wiz.io 90 天 AI 基础设施蜜罐研究：https://secnews.in/article/article-hackers-target-ai-infrastructure-rce-and-credential-theft/
- OAuth resource confusion（mcp-go / ChatCLI）：https://al-ice.ai/posts/2026/11/mcp-oauth-resource-confusion-mcp-go-chatcli-november-2026
- Instatic / SurrealDB MCP 授权 CVE：https://al-ice.ai/posts/2026/11/instatic-surrealdb-mcp-auth-cve-2026-121458-cve-2026-121419-november-2026
- TS SDK 跨源重定向 CVE-2026-111769：https://pulse.kalir.io/en/cves/CVE-2026-111769
- IBM ContextForge MCP Gateway 漏洞快照（11-05）：https://junglewise.ai/threats/technologies/contextforge-mcp-gateway
- 广发证券智能金融资讯数据服务平台征集：http://gfjc.gf.com.cn/gyszjgg/13321.jhtml
- 广发证券 2026 企业微信持续建设项目征集：http://gfjc.gf.com.cn/gyszjgg/13322.jhtml
- 山东重工人工智能算力及平台建设二次招标：https://www.ebnew.com/businessShow/710582880.html
- Spring AI 2.0 + MCP + Agent 三条路径：https://devpress.csdn.net/aibjcy/6aeb1a0f40858c2d88188388.html
- Agentic AI Standards 2026（MCP / A2A / WebMCP / OSI）：https://dev.to/alexmercedcoder/the-state-of-agentic-ai-standards-in-2026-mcp-a2a-webmcp-osi-and-the-protocol-stack-taking-3o2l
- Upwork Q3 财报与 MCP Server 周活 ×20：https://pivotnews.ai/talent/upwork-ceo-says-ai-skilled-talent-volume-rose-55-to-112
- Upwork 费率与 Work Now 细则：https://digitalsolohub.com/ai-automation-jobs-on-upwork / https://earninger.com/store/upwork-freelance-marketplace
- Agiloft 受治理 MCP 与 HITL 写操作：https://www.agiloft.com/news/execution-focused-ai-agents
- 完整情报与出处：[`docs/earnings-report-2026-11-06.md`](earnings-report-2026-11-06.md)
