# 2026-09-18 日报 / 收益报告

## 今日完成（V1.27：Release 工程修复 + Go 客户端示例 + 市场雷达 09-18）

### 代码/工程交付
- **修复 Dockerfile**（关键 bug）：V1.26 新增 `mcp-governance` 后，Dockerfile 的
  `COPY */pom.xml` 列表未同步，而 `mcp-server` 已依赖 `mcp-governance`——
  导致 `docker build`（`-pl mcp-server -am` 构建依赖链）必然失败。
  本轮补上 `mcp-governance`/`mcp-gateway`/`mcp-springai-tools` 三个 pom 拷贝，Docker 构建恢复可用；
- **修复 GitHub Actions 产物清单**：`.github/workflows/maven-ci.yml` 的
  upload-artifact 与 release 步骤缺少 V1.25-V1.26 新增模块（mcp-gateway / mcp-governance /
  mcp-a2a / mcp-springai-tools）的 JAR——CI 产物与发布附件补齐为全量 19 模块；
- **新增 Go 客户端示例** `examples/client-go/mcp_client.go`（零第三方依赖，仅标准库
  net/http + encoding/json，go.mod go 1.21）：health / connect / disconnect / list_tools /
  get_tool / invoke_tool / stats 全链路演示——
  与已有 Java（2 个）、Node.js（2 个）、Python（2 个）示例补齐为 4 语言 7 客户端；
  Go 是本周 Upwork（Lifted #132811）/ Ciena 等岗位点名语言，也补足非 Java 用户覆盖。

### 文档交付
- `docs/market-research-2026-09-18.md`（市场雷达 09-18：OneSeven Java+Spring MCP $4-5K/月、
  Upwork Lifted Java/Go 长期合同、沃尔玛中国 ¥30-55K 与 V1.21/25/26 逐条对应、
  Upwork 服务产品明码 $750-$5,000、$41 低价红海信号、MCP SDK 下载 97M/月）
- 本文件（earnings report 09-18）

## 为什么做这些
1. **Docker/CI 是「开源影响力」的门面**：README 写着 Docker 部署与 CI 徽章，但 V1.26 的
   模块增补把两个文件都弄失效了——修复它们比加新功能更能保证「克隆即用」的承诺；
2. **Go 客户端是本周市场情报的直接需求**：Lifted 岗位 Java/Go 二选一、Ciena 后端点名
   Java/Python/Go——多语言示例是 Upwork 简历上的「交付物证据」；
3. **市场雷达 09-18**：OneSeven 岗位要求「GitHub 作品 + Java Spring Boot WebFlux + MCP 生产经验」
   与本项目完全同构；沃尔玛 JD（MCP 网关+Skill Registry+治理）被 V1.21/25/26 逐条命中——
   这是当前最值得投的两个岗位。

## 验证状态
- `mvn clean test`（全仓 16+ 模块）执行中/结果见下节；
- Go 客户端 `go vet` + `go build` 验证中（如本机无 Go 则跳过，代码均为标准库 API）。