# McpPersistentMemoryGuard — 持久化记忆 / 学习存储面守卫（V1.80）

> 十二段式收口第十二段：**持久化记忆 / 学习存储 / AgentDB pattern store / 向量库是「跨会话状态」，不是「已被批准的事实」。**

## 为什么需要它

前 11 段守卫（注解→注册中心→只读声明→决策层→配置面→结果面→身份传播面→会话/传输绑定面→执行环境隔离面→浏览器出站响应面→日志/遥测输出面）都守「一次调用自身」或「一次调用的交出物」。本段守一个它们全部漏掉的方向：

**一次写入完全合法，但它把内容存进了跨会话存活的记忆 / 学习存储 —— 注入在 t1，利用在 t2/t3，原始来源早已离开上下文，留下的「记忆」却以「用户告诉我的」身份参与未来所有会话的规划。**

证据链（均已回原文核实）：

| 证据 | 事实 | 对位控制点 |
| --- | --- | --- |
| **CVE-2026-59726**（GitLab/NVD 11-24，**CVSS 10.0**，ruflo < 3.16.3，Noma Security） | 默认 compose 把 MCP bridge + MongoDB 绑 `0.0.0.0`、`POST /mcp` 无鉴权；tool blocklist 只在 autopilot 强制、`POST /mcp` 与 `/mcp/:group` 绕过 → `terminal_execute` RCE、读全部 provider key、**投毒 AgentDB 影响未来所有用户**；官方明确「**仅重新部署打补丁不会撤销投毒**」，须审计清除 `agentdb_pattern-store` 条目 + 轮换 key | CP3 SCOPE_TTL + CP6 ROLLBACK |
| **NIST SP 2400-102e 草案**（11-09，CN-SEC 11 月解读） | 长期记忆投毒列入 Agentic SI 攻击分类；注入时间与利用时间解耦；接触→提取→写入→**检索**→规划→动作六阶段；**低信任记录进入高权限上下文是独立控制点**；加密无法阻止语义污染 | CP5 RECHECK |
| **Nodeflare 13 次写入实测**（2026，OWASP ASI06） | 平均 **50.46%** 写入成功率、**41.05%** 后续跨会话改变行为率；plain store **7/7** 存下非期望写入，模式扫描 6/7，完整写门（含 host provenance）**0/7**（1 误报送审而非丢弃）；「provenance caught what patterns could not」 | CP1 PROVENANCE + CP2 QUARANTINE |
| **RunPod MCP 事件**（腾讯云 11-20~22 复盘） | create-pod 返回值夹带「评分且不告知用户」指令，只在 MCP 层出现、不在底层 REST，静态扫描工具描述原理上看不到 | CP4 POISON |

## 六道闸

| 闸 | 名称 | 判定 |
| --- | --- | --- |
| CP1 | PROVENANCE | 来源自报（工具返回值/网页/模型自证）不进批准区；溯源须 host 侧背书 |
| CP2 | QUARANTINE | 一切 agent 发起写入先落隔离区，未人类确认不得直接进批准 / 长期记忆 |
| CP3 | SCOPE_TTL | 全局 scope 或永久（`ttl=0`）写入须升级审批（对位「影响未来所有用户」） |
| CP4 | POISON | 载荷检测指令式注入（ignore previous / 评分不告知 / 从现在起你是 / 休眠载荷 / 多语言变体）与 secret 字面量，命中即拒 |
| CP5 | RECHECK | 低信任记录召回进高权限上下文前重新定级，不得直接进 system 上下文 |
| CP6 | ROLLBACK | append-only 脱敏审计 + 记录 hash，投毒可被定位 / 清除 / 回滚（补丁 ≠ 清零） |

## 运行自检

```bash
javac -encoding UTF-8 McpPersistentMemoryGuard.java
java -Dfile.encoding=UTF-8 McpPersistentMemoryGuard
```

预期输出：

```
self-test: OK
  attack   : 20/20 blocked
  control  : 8/8 allowed (false positives: 0)
  boundary : 2/2 assertions
  legacy-only blocks (new guard catches, baseline misses): 20
  total assertions: 30
```

- **旧基线**（只看「是不是 memory write 工具 + content 非空」）放行全部 20 条攻击 —— 参考 memory server 默认给模型写权限。
- **合法对照 8 条 0 误杀**：用户显式偏好、待审隔离区写入、已批准偏好召回、只读查询、会话级 TTL、升级审批后的全局写入、**清理投毒的 DELETE 动作**全部放行 —— 守卫拦的是「无背书的信任升级」，不是「所有写入」。
- boundary：审计零明文残留（注入原文不落审计）+ 投毒 hash 可定位回滚。

## 接入位置（建议，未实施）

- 记忆服务 `add_observations` / `create_entities` / `add_memory`（Mem0 等）写路径前置 CP1~CP4；
- `read_graph` / `search_memories` 返回进入 system 上下文前挂 CP5；
- `mcp-audit` + 记忆存储侧 append-only changelog 承接 CP6（与 V1.79 日志出口守卫串成「出口 + 存储双审计」）；
- 与 `examples/gate-orchestration/` 的注册期→配置面→调用期链并接为第五段「写入期」。

## 诚实边界

- 纯 JDK 零依赖、**未接入 21 主模块**，不进 Maven reactor；
- marker 表为词法归一化子串匹配、非语义解析；改写式指令（paraphrase）会绕过模式扫描 —— **真正可靠的控制是 provenance 与隔离区，不是正则**（Nodeflare 原文结论）；
- hash 为演示摘要（`String.hashCode()`）、非密码学签名；隔离区 / 审批流用布尔标志表达，生产须交记忆服务 + 审批工作流 + 不可篡改审计库；
- 本框架**不受** ruflo CVE-2026-59726 直接影响（那是第三方产品）；NIST 草案为 11-09 发布、非近 3 天事件；Nodeflare 数字为其自有实验、非本类实测拦截率。

详见 [`docs/persistent-memory-boundary-2026-11-26.md`](../docs/persistent-memory-boundary-2026-11-26.md)（12 项验收 + 买方必问 5 问 + 三档报价）。
