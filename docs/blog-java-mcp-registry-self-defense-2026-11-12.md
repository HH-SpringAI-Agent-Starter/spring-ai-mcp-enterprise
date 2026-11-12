# 注册中心自己被提权了：AWS 两个 CVE 给 Java MCP 治理上的一课

> 掘金 / CSDN 稿件 · 2026-11-12 · 关键词：MCP、MCP Gateway、注册中心、Spring AI、Java、CVE-2026-100307、CVE-2026-125926、企业治理、SSRF、云元数据

前一篇文章我们说「**注解不是安全边界**」：工具自己声明的 `readOnlyHint` / `destructiveHint` 只是 hints，授权必须在服务端做。11 月 11 日 AWS 的两份安全公告把这件事又往前推了一步——

> **负责审批这些工具的注册中心，自己变成了提权入口。**

这篇不聊概念，只讲三件事：两个洞怎么串成一条链、为什么「打补丁」不等于「清干净」、以及用纯 JDK 怎么把这几道闸补上（附可跑的自检）。

---

## 一、两个 8 分以上的洞，其实是一条链

AWS 在 11-11 连发两份 Important 公告（`2026-179-AWS` 12:30 PST、`2026-180-AWS` 13:00 PST），对象是开源项目 `mcp-gateway-registry`——一个集中做 MCP server 发现、访问控制与代理的注册中心。

| CVE | 评分 | 根因 | 能拿到什么 |
| --- | --- | --- | --- |
| `CVE-2026-100307` | 8.8 | `/api/servers/*` 管理端点**只认证、不做角色 / 权限校验** | 提交一个精心构造的 `POST /api/servers/groups/import`，把自己的 IdP group 映射到 `mcp-registry-admin`；**管理员身份按请求派生**，下次请求就生效，连重新登录都不用 |
| `CVE-2026-125926` | 8.5 | 注册 / 修改 API 不校验上游目标 | 把网关上游指向内网服务甚至**云实例元数据端点**，部分场景能读回响应 |

关键在于：**第一个洞发出去的，正好是第二个洞需要的「注册 / 修改」权限**。拿到管理员之后，还能通过管理员可达路由读走注册表里**读时解密**的各 server 凭据与自定义 header。

同周还有两条补刀：`CVE-2026-109492`（Microsoft `mcp-gateway`，IDOR / CWE-639，8.8）和 `CVE-2026-125271`（AWS API MCP Server，**操作名归一化错误导致安全策略绕过**）。再算上 11-11 有人实测 fast-agent 的修复版：默认绑了 `127.0.0.1`，但同机免鉴权照样能跑 shell，`--host 0.0.0.0` 只打印一句警告又恢复远程访问。

一句话：**传输层、网关层、注册表层，在同一周一起出问题。**

---

## 二、最容易被忽略的一句：补丁不清理存量

官方修复说明里有一段比漏洞本身更值得抄进设计文档：

> 1.35.0 **只在注册 / 修改时**校验上游地址，**升级前已存储的 server 配置保持不变**；升级也不会撤销已创建的 group 映射，不会使攻击者读过的凭据自动失效。

所以官方给的处置动作是「**逐个复查所有已注册 server 的上游地址** + 回溯受影响版本窗口内的异常注册与变更日志」。

翻译成工程语言，就是三条硬约束：

1. **认证 ≠ 授权**，而且授权判定不能从「注册表里可写的数据」派生；
2. **注册时批准过 ≠ 永久可信**，任何升级 / 策略变更后必须有一次显式的存量全量复验；
3. **凭据读取要有独立权限**，管理员身份不能隐含「能看所有密钥」。

只做第 1 条的产品，打完补丁仍然可能带着一个管理员后门在跑。

---

## 三、Java 侧怎么补：四段关键代码

下面代码来自本项目的 `examples/registry-admin-guard/McpRegistryAdminGuard.java`，**纯 JDK 零依赖**，不进 Maven reactor，可以直接 `javac` 跑。

### 3.1 授权源独立于可写注册表数据

```java
public static final class Principal {
    public final String id;
    public final Set<Permission> granted;   // 来自独立、只读、带版本的授权源
    public final boolean bootstrapAdmin;

    boolean has(Permission p) {
        // 凭据读取是独立权限，管理员身份不得隐含
        if (p == Permission.CREDENTIAL_READ) return granted.contains(p);
        return bootstrapAdmin || granted.contains(p);
    }
}

public Verdict importGroup(Principal caller, boolean authenticated,
                           String groupName, String targetScope) {
    Verdict pre = authorize(caller, authenticated, Permission.GROUP_IMPORT, "importGroup");
    if (!pre.allowed) return pre;                       // 认证 != 授权
    String scope = canonicalizeOperation(targetScope); // 去非字母数字 + 小写
    if (scope.contains("admin") || scope.contains("root")
            || scope.contains("superuser") || scope.contains("policy")) {
        return Verdict.deny("PRIVILEGED_SCOPE_MAPPING_FORBIDDEN");
    }
    return Verdict.allow("GROUP_IMPORTED_NON_PRIVILEGED");
}
```

注意 `canonicalizeOperation()`：`mcp-registry-admin`、`MCP_REGISTRY_ADMIN`、`mcp.registry.admin` 三种写法必须落到同一个判定上——这正是 `CVE-2026-125271` 的教训，策略匹配前不做归一化，deny list 写了也白写。

### 3.2 上游地址准入：别只挡 `127.0.0.1`

```java
public Verdict validateUpstream(String rawUrl) {
    if (rawUrl.indexOf('\\') >= 0 || rawUrl.indexOf('\r') >= 0) {
        return Verdict.deny("AMBIGUOUS_URL_CHARS");   // 反斜杠 / 控制字符
    }
    URI u = new URI(rawUrl);
    if (!"https".equalsIgnoreCase(u.getScheme())) return Verdict.deny("SCHEME_NOT_HTTPS");
    if (u.getRawUserInfo() != null) return Verdict.deny("USERINFO_FORBIDDEN");
    if (forbiddenHost(u.getHost())) return Verdict.deny("FORBIDDEN_UPSTREAM_HOST");
    return Verdict.allow("UPSTREAM_OK");
}
```

`forbiddenHost()` 要覆盖的远不止 loopback：`169.254.169.254`（云元数据）、`10/8`、`172.16/12`、`192.168/16`、`100.64/10`、`::1`、`fd00::/8`、`fe80::/10`、`*.internal`、`metadata.google.internal`，还有 `2130706433`（十进制 IP）、`0177.0.0.1`（八进制）这类**解析器与 HTTP 客户端解释不一致**的字面量。

> 这里有个真实踩坑：我第一版用「标准点分四组」正则，`0177.0.0.1` 因为首段是 4 位数字没匹配上，被当成合法域名放行了。**自检里的对照组抓到了它**。所以最后的写法是：只要主机是纯数字和点、又不是标准点分四组，一律拒绝。

### 3.3 重定向逐跳重校验 + Host/SNI 一致性

```java
public Verdict validateRedirectHop(String previousUrl, String locationHeader) {
    URI prev = new URI(previousUrl);
    URI next = new URI(locationHeader);
    if (!next.isAbsolute()) next = prev.resolve(next);
    if (!prev.getHost().equalsIgnoreCase(next.getHost())) {
        return Verdict.deny("HOST_SNI_MISMATCH_ON_REDIRECT");
    }
    return validateUpstream(next.toString());   // 每一跳重新跑全量准入
}
```

不要复用上一跳的准入结论。跨主机重定向默认拒绝是**保守策略选择**，生产按业务配显式 allowlist 就行，但要写进方案里，别默默放行。

### 3.4 存量全量复验：本期最核心的一段

```java
public List<String> reverifyAll() {
    List<String> quarantinedIds = new ArrayList<>();
    for (Registration r : store.values()) {
        Verdict v = validateUpstream(r.upstreamUrl);
        boolean drift = !fingerprint(r.upstreamUrl).equals(r.approvedFingerprint);
        boolean bad = !v.allowed || drift;
        if (bad && !r.quarantined) {
            r.quarantined = true;
            quarantinedIds.add(r.id);
            log("REVERIFY", "system", r.id, "QUARANTINE_" + (drift ? "FINGERPRINT_DRIFT" : v.reasonCode));
        }
    }
    return Collections.unmodifiableList(quarantinedIds);
}
```

**幂等**、**对全量**、**比对批准时指纹**、**不通过即隔离**，四件事缺一不可。把它挂到升级流水线或每次策略变更之后，才能回答买方那句「你怎么证明存量是干净的」。

---

## 四、自检结果：别只看「拦得更多」

```
self-test OK (27 attack cases + 10 control cases + 8 boundary assertions)
total assertions: 45
incremental blocks over legacy baseline: 14
```

- 27 个攻击用例：提权 scope 四种变形、云元数据 / 私网 / IPv6 / userinfo 欺骗 / 十进制与八进制 IP、重定向逐跳、凭据越权、策略名变形。
- 10 个合法对照：普通组导入、合法注册与修改、公网显式端口、同域相对路径重定向等**全部放行**。
- 8 个边界断言，其中最重要的一条是「**不得劣于既有基线**」：旧基线只检查 `http/https` 前缀，14 个攻击 URL 全漏，新实现 14/14 拦下；同时合法流量一个都没误杀。

> 开发过程中还抓到另一处：管理员身份一度隐含了 `CREDENTIAL_READ`，等于自己把攻击链的最后一环复现了一遍。**只看「拦得更多」会选错方案，只看「误报更少」也会选错方案。**

---

## 五、9 条落地清单（可直接贴到评审意见里）

1. 管理 API 每个操作显式判权限，别用「登录了就能管」。
2. 组 / 角色导入禁止写入任何特权 scope，判定前做归一化。
3. 授权源独立、只读、带版本，**绝不由注册表可写数据派生**。
4. 凭据 / 自定义 header 读取设独立权限，审计只记字段名不记值。
5. 上游地址准入覆盖私网、link-local、云元数据、IPv6、进制伪装；只挡 `localhost` 不够。
6. 重定向**逐跳**重校验，跨主机默认拒绝。
7. 修改上游地址 = 重新注册，重签指纹。
8. **升级 / 策略变更后全量复验存量**，幂等 + 漂移检测 + 隔离 + 留痕。
9. 主机字面量检查要与 **DNS 解析后真实 IP 校验**叠加，否则挡不住 DNS rebinding。

---

## 六、诚实边界

- 本文示例是 `examples/` 下的**参考实现，未接入主模块**；`simulateOutOfBandTamper()` 仅供演练，生产不应暴露。
- 主机判定是字面量检查，不做 DNS 解析；策略 deny list 是启发式示例，不是完整授权模型；`fingerprint` 只覆盖上游 URL。
- 公开来源对受影响版本区间表述不一致（一说 privesc 修于 1.25.0，一说 SSRF 修于 1.35.0，GHSA 均 11-11 才公布），**版本级判断以 AWS 官方公告 / GHSA 原文为准**，本文不做符合性断言。
- 本框架未依赖 AWS / Microsoft 的 registry 与 gateway 组件，**不受上述 CVE 直接影响**。
- 同期 `CVE-2026-102610` 的 11-11 复测是第三方实验室结论，不是官方公告。

---

## 七、延伸阅读（本仓库）

- [注册中心信任边界：12 项管理面验收清单](registry-control-plane-self-defense-2026-11-12.md)
- [财富机会雷达 11-12](earnings-report-2026-11-12.md)
- [V1.70 Release Notes](V1.70-release-notes.md)
- [V1.69 · 注解不是安全边界](tool-annotation-integrity-governance-2026-11-11.md)
- [`examples/registry-admin-guard/README.md`](../examples/registry-admin-guard/README.md)

---

*如果这篇对你有用，欢迎到 [GitHub](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise) 点个 Star；项目是 Spring AI MCP Enterprise 企业级 MCP Server 框架，21 个模块，含 RBAC / 限流 / 审计 / API Key / 工具注册中心 / REST API。*
