# 🤝 贡献指南

感谢您对 **Spring AI MCP Enterprise** 的关注！本项目旨在为 Java/Spring Boot 生态提供企业级 MCP Server 框架，欢迎任何形式的贡献。

---

## 📋 贡献方式

### 🐛 报告 Bug

1. 前往 [Issues](https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise/issues) 页面
2. 使用 **Bug Report** 模板创建 Issue
3. 提供：
   - 清晰的标题和描述
   - 复现步骤（代码片段或测试用例最佳）
   - 预期行为 vs 实际行为
   - 环境信息（JDK 版本、操作系统、Spring Boot 版本）

### 💡 提出新功能

1. 先搜索 Issues 确认是否已有类似提议
2. 使用 **Feature Request** 模板
3. 说明：
   - 功能解决的问题场景
   - 预期的使用方式
   - 能否通过现有 SPI 扩展实现

### 🔧 提交代码

#### 前置条件

- JDK 17+
- Maven 3.9+
- Git

#### 开发流程

```bash
# 1. Fork 本仓库
# 2. 克隆你的 Fork
git clone https://github.com/你的用户名/spring-ai-mcp-enterprise.git
cd spring-ai-mcp-enterprise

# 3. 创建特性分支
git checkout -b feature/你的特性名

# 4. 编写代码和测试
# 确保新代码有对应的单元测试

# 5. 运行测试
mvn test

# 6. 提交代码（中文/英文均可）
git add .
git commit -m "feat: 添加 XXX 功能"

# 7. 推送到你的 Fork
git push origin feature/你的特性名

# 8. 创建 Pull Request
```

#### 代码规范

- **Java 17+**：使用 var、record、sealed class 等现代语法
- **Spring Boot 3.4+**：自动配置、@ConfigurationProperties
- **测试覆盖**：新功能必须有单元测试，bug fix 应有回归测试
- **无编译警告**：提交前确保 `mvn compile` 无警告
- **日志**：使用 SLF4J，避免 System.out

#### Commit 信息规范

```
<type>: <简短描述>

类型:
- feat: 新功能
- fix: 修复 Bug
- docs: 文档修改
- test: 测试修改
- refactor: 重构
- style: 代码格式
- chore: 构建/CI 变更
```

---

## 🧪 测试

```bash
# 运行所有测试
mvn test

# 运行特定模块测试
mvn test -pl mcp-core

# 运行集成测试
mvn verify -P integration-test
```

---

## 📦 模块修改指南

| 模块 | 修改场景 | 注意事项 |
|------|---------|---------|
| mcp-core | SPI 接口、安全、注册中心 | 接口变更需同步更新所有实现 |
| mcp-spring-boot-starter | AutoConfiguration、配置属性 | 新增配置需添加 ConfigurationProperties |
| mcp-server | Controller、端点路由 | 兼容现有 API 路径 |
| mcp-tools | 新增/修改工具 | 实现 McpToolExecutor 接口 |
| mcp-monitor | 监控指标、Grafana 面板 | Prometheus 指标命名规范 |
| mcp-integrations | 接入第三方 AI 平台 | 独立的 AutoConfiguration |
| mcp-examples | 使用示例 | 保持简单易懂，中文注释 |

---

## 🚀 发布流程（仅维护者）

```bash
# 1. 更新版本号
mvn versions:set -DnewVersion=0.0.3

# 2. 编译并测试
mvn clean verify

# 3. 发布到 Maven Central
mvn deploy -P release

# 4. 创建 Git Tag
git tag v0.0.3
git push origin v0.0.3

# 5. 创建 GitHub Release
# 在 GitHub 页面创建 Release 并附上 CHANGELOG
```

---

## 📖 文档贡献

- 文档位于 `docs/` 目录，使用 Markdown 格式
- 所有中文文档使用 UTF-8 编码
- 示例代码使用 Java / YAML / Bash 语言标记

---

## 💬 沟通

- **Issues**：技术问题、Bug 报告、功能建议
- **Discussions**：使用疑问、最佳实践分享
- **Pull Requests**：代码贡献

---

## 📄 许可

贡献代码即表示您同意 Apache License 2.0 许可条款。您的代码将与其他贡献者的代码一同以该许可发布。
