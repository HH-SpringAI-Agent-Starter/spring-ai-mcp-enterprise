# 身份传播守卫（McpIdentityPropagationGuard）— V1.75

> 第七段安全边界：**身份传播面也不是安全边界**。
> 六段式收口（注解 → 注册中心 → 只读声明 → 决策层 → 配置面 → 结果面）之后，
> 这一段管的是「工具真正执行时，被调用方收到的到底是谁的身份」。

## 为什么需要它

| 事实 | 来源 | 边界含义 |
| --- | --- | --- |
| per-server 身份转发配置（user id/email/group/team）在调用工具/读资源时被**忽略**，身份被转发给明确声明「不接收」的上游 server；静态签名 payload 可重放冒充用户（CVSS 7.4，修 v1.1.1） | IBM `CVE-2026-110278`（11-12） | **声明「不接收」不是边界**，必须在出站调用点强制裁剪 |
| 客户端 header 覆盖上游凭据，破坏按用户授权与审计归因 | IBM `CVE-2026-110279` | header 完整性必须校验，不能信任调用方传入的身份 |
| 「governed by existing permissions」+ 敏感字段不交给 AI 助手 | Impartner MCP（11-17） | 买方话术已把「复用已有权限 + 字段最小化」写进产品定义 |
| 一次封装、多处调用；数据按客户经理归属严格隔离 | 券商 MCP 协议战（11-16） | 多租户身份隔离是刚需，跨租户调用必须 fail-closed |

## 六道闸（CP1–CP6）

| 闸 | 控制点 | 对位风险 |
| --- | --- | --- |
| CP1 | server 声明 block 的身份字段一旦被请求 → **拦截**（非静默放行） | `110278` 配置被忽略 |
| CP2 | 只转发任务按需声明且 allowlist 允许的字段，其余**剥离** | 过度身份暴露 / GDPR 数据最小化 |
| CP3 | 拒绝静态/缺失签名；时间戳 5 分钟窗口；nonce 防重放；HMAC 校验 | `110278` 静态签名重放 |
| CP4 | 拒绝客户端覆盖上游凭据/身份的 header（Authorization / X-User-Id / X-Identity …） | `110279` header 覆盖 |
| CP5 | 身份 tenant 必须等于目标 server tenant | 跨租户数据泄露 |
| CP6 | append-only 审计，**email 打码、groups 只记数量**，不落明文 | 审计本身成泄露源 |

## 运行

纯 JDK 17+，零第三方依赖，不进 Maven reactor：

```bash
javac -encoding UTF-8 McpIdentityPropagationGuard.java
java -Dfile.encoding=UTF-8 McpIdentityPropagationGuard
```

预期输出：

```
self-test OK: 10 attack + 3 control + 2 boundary assertions
  legacy-only blocks (new guard catches, baseline misses): 3
  false positives on legal calls: 0
```

自检含三类断言：**攻击拦截**（静态签名/过期/重放/错误 HMAC/header 覆盖/跨租户/block 字段/未知 server/空身份）、**合法对照**（合法调用 0 误杀）、**边界**（CP2 剥离 + CP6 审计脱敏）；并额外验证 `legacyBaseline()`（只信任声明配置）放行的 3 条攻击本守卫全部拦下 —— 即「不得劣于旧基线」。

## 接入生产前的必做项（诚实边界）

- ⚠️ 示例密钥硬编码仅为可运行演示，生产必须走 **KMS 轮换**，签名应由受保护服务统一签发。
- ⚠️ nonce 去重与审计当前在**进程内**，多实例部署须换共享存储（Redis/DB）并做原子去重。
- ⚠️ 身份字段判定是词法白名单非语义解析；`signatureOk` 类判定不应被当作密码学身份证明。
- ⚠️ 本类**未接入 21 个主模块**，是参考实现；接入点评估见 `docs/gate-integration-map-2026-11-14.md`。
- ⚠️ 本框架**不受** IBM/ContextForge 上述 CVE 直接影响；引用的是第三方公开漏洞作为设计依据。

完整的 12 项身份面验收清单、买方必问 5 问、竞品差异化与三档报价见
[`docs/identity-propagation-boundary-2026-11-18.md`](../../docs/identity-propagation-boundary-2026-11-18.md)。
