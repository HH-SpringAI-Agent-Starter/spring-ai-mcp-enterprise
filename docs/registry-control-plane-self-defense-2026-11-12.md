# 注册中心信任边界：12 项管理面验收清单（V1.70 · 2026-11-12）

> 用途：**可直接进标书 / 售前现场验收**的一页纸。
> 触发事件：2026-11-11 AWS 两份 Important 公告把「负责审批工具的注册中心」自己变成了提权入口。
> 一句话：**V1.69 证明「注解不是安全边界」，V1.70 证明「注册中心自己也不是安全边界」。**

---

## 0. 为什么这张清单现在值钱

| 旧假设 | 11-11 被否掉的证据 | 买方现在会问什么 |
| --- | --- | --- |
| 注册表是治理的信任根，天然可信 | `CVE-2026-100307`：`/api/servers/groups/import` 只认证不授权，低权限用户自授予 `mcp-registry-admin` | 「**谁在管注册表自己？**」 |
| 注册时校验过上游就安全 | `CVE-2026-125926`：注册 / 修改 API 可指向内网与云元数据 | 「上游地址怎么验、验几次？」 |
| 升级到修复版就干净了 | 官方说明：**升级前已存配置不变 / 已植入映射不撤销 / 已泄露凭据不失效** | 「**你怎么证明存量是干净的？**」 |
| 策略 deny list 写了就生效 | `CVE-2026-125271`：操作名归一化错误导致策略绕过 | 「策略匹配前做不做规范化？」 |
| 绑 localhost 就安全 | Siemens `112136/112137`、Dynatrace `123811`、fast-agent `102610` 11-11 复测 | 「本地监听器还有哪几道闸？」 |

**本期新增的可演示证据**：[`examples/registry-admin-guard/`](../examples/registry-admin-guard/README.md) —— 纯 JDK、`javac -encoding UTF-8` + `main()` 自检 **27 攻击 + 10 对照 + 8 边界 = 45 断言全绿**，其中 14 个是旧基线（只查 http/https 前缀）完全漏掉的增量拦截。

---

## 1. 12 项验收清单（逐条 → 控制点 → 真实文件 → 现场验收方式）

| # | 验收项 | 控制点 | 仓库真实文件 | 现场怎么验 |
| --- | --- | --- | --- | --- |
| 1 | **管理 API 认证 ≠ 授权**：每个 `/api/servers/*` 操作显式判定 required permission | `authorize()` | `McpRegistryAdminGuard.java` | 用只带 `SERVER_LIST` 的低权限主体调 `registerServer` → 必须 DENY `MISSING_PERMISSION` |
| 2 | **组导入禁止授予特权 scope**：admin / root / superuser / policy 一律拒绝，含大小写与分隔符变形 | `importGroup()` | 同上 | 提交 `mcp-registry-admin` / `MCP_REGISTRY_ADMIN` / `mcp.registry.admin` 三种 → 全 DENY |
| 3 | **授权源独立于可写注册表数据**：管理员身份不得按请求从 group 映射派生 | `Principal.granted`（不可变） | 同上 | 导入后比对授权集合引用与内容不变；尝试 `add()` → `UnsupportedOperationException` |
| 4 | **管理员身份不隐含凭据读取**：`CREDENTIAL_READ` 必须单独授予 | `readCredentials()` / `has()` | 同上 | bootstrapAdmin 主体读凭据 → 必须 DENY；`vault-svc` 主体 → ALLOW |
| 5 | **凭据读取审计脱敏**：只记字段名，不记值 | `log()` / `auditSnapshot()` | 同上 | 遍历审计快照，搜索 `user:pass` 等明文 → 0 命中 |
| 6 | **上游 scheme 与 userinfo**：仅 https，禁 `@` 欺骗与控制 / 反斜杠字符 | `validateUpstream()` | 同上 | 跑 `https://api.example.com@169.254.169.254/mcp` → DENY |
| 7 | **上游主机准入**：loopback / 私网 / link-local / `169.254.169.254` / metadata 域 / IPv6 ULA / 十进制与八进制 IP 字面量 | `forbiddenHost()` / `forbiddenIpv4()` | 同上 | 14 个攻击 URL 全 DENY；`203.0.113.10` 与 `mcp.vendor.example.com:8443` 放行 |
| 8 | **重定向逐跳重校验**：每一跳重新跑全量准入，不复用上一跳结论 | `validateRedirectHop()` | 同上（兑现 V1.69 待办） | 同域相对路径 → ALLOW；跳到 `169.254.169.254` → DENY |
| 9 | **Host / SNI 一致性**：跨主机重定向默认拒绝 | `validateRedirectHop()` | 同上（兑现 V1.69 待办） | `api.example.com → evil.example.net` → DENY `HOST_SNI_MISMATCH_ON_REDIRECT` |
| 10 | **修改即重新批准**：改上游地址重跑准入并重签指纹 | `modifyUpstream()` | 同上 | 改后指纹变化、旧批准不复用 |
| 11 | **★ 存量全量复验**：升级 / 策略变更后对全部注册项重验 + 指纹漂移比对，不通过即隔离，且**幂等** | `reverifyAll()` | 同上（本期最核心） | 带外污染一个已注册项 → 第一次复验抓到并 quarantine、第二次不再重复报 |
| 12 | **操作名归一化**：策略匹配前去非字母数字 + 小写 | `canonicalizeOperation()` | 同上（对位 `125271`） | `get_user` / `GetUser` / `Get__User` 三者同一策略键；`Export__Credentials` 命中 deny list |

**叠加项（已有资产，本次不需新增但验收时必须一起出示）**：DNS 解析后真实 IP 校验与防 rebinding 见 [`connect-path-guard/McpResolvedIpGuard.java`](../examples/connect-path-guard/McpResolvedIpGuard.java)；本地监听器绑定 / Content-Type / Origin / Host / wildcard CORS 见 [`local-listener-guard/`](../examples/local-listener-guard/README.md)；4MiB 请求体上限与 fail-closed 见 [`governance-hardening/`](../examples/governance-hardening/README.md)；注册期注解完整性 14 项见 [`tool-annotation-integrity-governance-2026-11-11.md`](tool-annotation-integrity-governance-2026-11-11.md)。

---

## 2. 买方必问 5 问（话术，逐字可用）

| 买方的问法 | 答法（不要绕） |
| --- | --- |
| 「我们用了 AWS / 微软的网关和注册表，还需要你们吗？」 | 需要的是**独立于这些组件的一层**。11-11 AWS 自己的 `mcp-gateway-registry` 与 11-10 Microsoft `mcp-gateway` 都出了 8.8 分漏洞；它们是被治理对象，不是治理的信任根。我们不替换上游组件，我们管住它们的**管理面**和**存量**。 |
| 「打了补丁是不是就不用做了？」 | 官方修复说明写得很清楚：1.35.0 **只在注册 / 修改时**校验上游，**升级前已存配置不变**，也不撤销已植入的 group 映射、不使已泄露凭据失效。补丁修的是写入路径，**复验与举证是补丁之外的工作**。 |
| 「怎么证明存量是干净的？」 | 现场跑 `reverifyAll()`：对全量注册项重跑准入 + 配置指纹漂移比对，输出隔离清单与 append-only 审计；幂等，可挂到每次策略变更 / 升级的 CI 步骤里。**这一条是可验收的，不是承诺。** |
| 「会不会把正常业务拦掉？」 | 自检里 10 个合法对照全部放行，并专门设「不得劣于既有基线」断言；跨主机重定向默认拒绝是**显式策略选择**，可按业务配 allowlist。我们不拿「拦得更多」当卖点。 |
| 「你们是 Java 栈，跟我们 Python / Go 的网关怎么配合？」 | 治理判定放在**调用路径之外**的一层：Java/Spring 负责注册期与审批期，网关只负责执行；存量 Java 权限体系（RBAC / 租户 / 审计）直接复用，不要求重写权限模型 —— 这也是 11-12 OnePlan 官方稿强调的「respects existing permissions」。 |

---

## 3. 竞品差异化（2026-11-12 口径）

| 维度 | 云厂商 / 官方注册表 | 本方案 |
| --- | --- | --- |
| 定位 | 发现层 / 代理层，Official MCP Registry 的 Access Control Model 标注 **None** | **治理增强层**，默认不信任何上游组件 |
| 授权 | RBAC / IAM 产品层（Bifrost / JFrog / Docker / Stacklok 各自实现） | 认证与授权分离 + **授权源不可由注册数据派生** |
| 存量 | 官方处置建议「逐个人工复查已注册 server 上游」 | **一次性自动复验 + 指纹漂移 + 隔离 + 幂等** |
| 语言栈 | Go / Python / 容器目录为主 | **Java / Spring**，对齐存量企业权限与审计体系 |
| 举证 | 产品文档 / 合规声明 | 8 组可 `javac` 编译 + `main()` 自检的**可运行验收证据** |

---

## 4. 三档报价模板（报价为销售口径估算，非成交价）

| 档 | 交付物 | 适用 | 参考区间 |
| --- | --- | --- | --- |
| **A · 评估** | 12 项验收清单现场跑一遍 + 存量注册表体检报告 + 差距矩阵 | 预征集 / POC 入场 | 沿用 `mcp-governance-pricing-anchor-2026-11-09.md` 低档 |
| **B · 落地** | 管理面自防护 + 存量复验接入 CI + 凭据读取独立授权 + 审计工件 | 已选定技术路线 | 生产级 $80K–$220K 区间下沿 |
| **C · 合规包** | B + 26 类攻击面逐条验收 + 法规映射 + 私有化 / 等保配套工件 | 金融 / 央企投标 | 企业网关 $250K 起区间 |

> ⚠️ 三档均为**卖方销售页公开价与项目估算**，不是成交价；本项目**无商业合同、无中标、无金融案例、无等保测评 / 算法备案**。

---

## 5. 诚实边界

- 12 项验收点**当前是 `examples/` 参考实现**，尚未接入 `mcp-registry` 主模块；接入是跨期待办，不能宣称已交付。
- 主机准入是**字面量检查**，不做 DNS 解析；必须与 `McpResolvedIpGuard` 叠加才能覆盖 DNS rebinding。
- 策略 deny list 为启发式示例；`fingerprint` 只覆盖上游 URL，不覆盖工具元数据或实现代码。
- 跨主机重定向默认拒绝是保守策略，生产需按业务配显式 allowlist。
- 不对 AWS / Microsoft 组件做版本级符合性断言；affected range 在不同二手来源表述不一致，以官方公告 / GHSA 原文为准。**本框架不受上述 CVE 直接影响**。
