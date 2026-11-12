# 注册中心管理面自防护 + 存量复验（V1.70 参考实现）

> 纯 JDK 零依赖，**不进 Maven reactor**，零构建风险。
> 对位 2026-11-11 AWS `mcp-gateway-registry` 双 CVE、2026-11-10 Microsoft `mcp-gateway` IDOR、
> 2026-11-11 `CVE-2026-125271` 操作名归一化策略绕过。

## 为什么要有这个示例

V1.69 把治理起点推回「工具注册进来的那一刻」，解决的是**注册进来的工具**可不可信。
11-11 AWS 的两份公告把问题再推进一层：**负责审批这些工具的注册中心自己，被当成了提权入口**。

| CVE | 根因 | 后果 |
| --- | --- | --- |
| `CVE-2026-100307`（8.8） | `POST /api/servers/groups/import` **只认证不授权**；低权限用户把自己的 IdP group 映射到 `mcp-registry-admin`；管理员身份**按请求派生**，下次请求即生效 | 拿到全部已注册资产控制权，含读时解密的 server 凭据与自定义 header |
| `CVE-2026-125926`（8.5） | 注册 / 修改 API 可把网关上游指向内网与**云实例元数据端点**并读回响应 | SSRF → 云凭据泄露 |
| `CVE-2026-109492`（8.8，Microsoft） | `mcp-gateway` IDOR / CWE-639 用户可控键越权 | 网关自身越权 |
| `CVE-2026-125271`（AWS API MCP Server） | 操作名归一化错误 → 安全策略绕过 | 策略名变形即可绕过 deny list |

**最关键的一句**（来自官方修复说明，也是本示例的设计原点）：

> 1.35.0 只在**注册 / 修改时**校验上游地址，**升级前已存配置不变**；升级也**不会撤销**已植入的 group 映射或使已泄露凭据失效。

也就是说：**注册时批准过 ≠ 永久可信；打补丁 ≠ 清干净了**。必须有一次显式的「存量全量复验」。

## 四个相互独立的控制点

| # | 控制点 | 实现 | 对位 |
| --- | --- | --- | --- |
| A | **管理面授权** | `authorize()` 逐操作显式判权限，认证 ≠ 授权；`importGroup()` 拒绝任何 admin/root/superuser/policy scope 映射；授权集合来自只读不可变授权源，**永不由可写注册表数据派生** | `100307` |
| B | **上游地址准入** | `validateUpstream()`：仅 https、禁 userinfo、禁 loopback / 私网 / link-local / `169.254.169.254` / metadata 域 / IPv6 ULA / 十进制与八进制 IP 字面量 / 反斜杠歧义；`validateRedirectHop()` **逐跳重校验 + Host/SNI 一致性** | `125926`，并兑现 V1.69 跨期待办 |
| C | **存量全量复验** | `reverifyAll()` 对全部已注册项重跑准入 + 配置指纹漂移比对，不通过即 quarantine，**幂等**；`modifyUpstream()` 视同重新注册并重签指纹 | 官方修复边界 |
| D | **凭据读取** | `readCredentials()` 需独立 `CREDENTIAL_READ`，管理员身份**不隐含**该权限；审计只记字段名不记值 | `100307` 攻击链终点 |
| E | **操作名归一化** | `canonicalizeOperation()` 去非字母数字 + 小写，`get_user` / `GetUser` / `Get__User` 落到同一策略键 | `125271` |

审计为 append-only 不可变快照（`auditSnapshot()`）。

## 运行

```bash
javac -encoding UTF-8 -d out McpRegistryAdminGuard.java
java -Dfile.encoding=UTF-8 -cp out com.example.mcp.registry.McpRegistryAdminGuard
```

实测（JDK 17.0.8，2026-11-12）：

```
self-test OK (27 attack cases + 10 control cases + 8 boundary assertions)
total assertions: 45
incremental blocks over legacy baseline: 14
```

- **27 个攻击用例**：组导入提权 4 种变形、认证不授权、云元数据 / 私网 / loopback / IPv6 / userinfo 欺骗 / 十进制与八进制 IP / 重定向逐跳 / 凭据越权 / 策略名变形绕过。
- **10 个合法对照**：普通组导入、合法注册与修改、公网显式端口、相对路径重定向、凭据读取等**全部放行**。
- **8 个边界断言**：含「**不得劣于既有基线**」——旧基线只检查 `http/https` 前缀，14 个攻击 URL **全部漏过**，新实现 14/14 拦下；授权源不可变、复验幂等、审计不含明文值。

> 开发过程中自检抓到并修正两处逻辑错误：① 管理员身份一度隐含了 `CREDENTIAL_READ`，等于复现了提权后读凭据的攻击链；② `0177.0.0.1` 这类八进制 IP 字面量因不匹配标准点分四组而被判为合法域名。**只看「拦得更多」会选错方案，只看「误报更少」也会选错方案**——本项目工程约定再验。

## 诚实边界（务必保留）

- 这是 `examples/` 下的**参考实现**，未接入 `mcp-registry` 主模块；`simulateOutOfBandTamper()` 仅供演练，生产不应暴露。
- 主机判定是**字面量检查**，不做 DNS 解析后真实 IP 校验（解析后 IP 与 DNS rebinding 见 [`connect-path-guard/McpResolvedIpGuard.java`](../connect-path-guard/McpResolvedIpGuard.java)）；两者需叠加使用。
- **跨主机重定向默认拒绝**是保守策略选择，生产需按业务配显式 allowlist。
- 策略拒绝列表是启发式示例，不是完整授权模型；`fingerprint` 只覆盖上游 URL，不覆盖工具元数据或实现代码。
- 示例不对 `mcp-gateway-registry` 做任何版本级符合性断言；受影响版本区间在不同公开来源中表述不一致，见 [V1.70 release notes](../../docs/V1.70-release-notes.md) 口径说明。
- 本框架**不受上述 CVE 直接影响**（未依赖 AWS / Microsoft 的 registry 与 gateway 组件）。
