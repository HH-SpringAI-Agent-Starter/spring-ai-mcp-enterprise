# MCP Enterprise — Sonatype 发布指南

> 将 MCP Enterprise 发布到 Maven Central 让全世界 Java 开发者使用

---

## 📋 前置要求

| 项目 | 说明 | 操作 |
|------|------|------|
| Sonatype 账号 | [central.sonatype.com](https://central.sonatype.com/) 注册 | 用 GitHub 登录 |
| 命名空间 | `com.mcp.enterprise` | 在 Sonatype 控制台创建 |
| GPG 密钥 | 用于签名 JAR | `gpg --gen-key` |
| 环境变量 | SONATYPE_USERNAME / PASSWORD / GPG_KEY_NAME / PASSPHRASE | 设置或写入 SETTINGS-CENTRAL.xml |

---

## 🔑 第一步：注册 Sonatype

1. 打开 https://central.sonatype.com/
2. 点击 **Sign in with GitHub**
3. 授权 GitHub 账号（推荐使用 `HH-SpringAI-Agent-Starter` 组织账号）

## 🔑 第二步：创建命名空间

1. 登录后进入 **View Namespaces**
2. 点击 **Add Namespace**
3. 输入 `com.mcp.enterprise`
4. 验证域名所有权（通过 DNS TXT 记录或 GitHub 仓库验证）

## 🔑 第三步：生成 User Token

1. 进入 **User → User Token**
2. 点击 **Generate**
3. 记录生成的 Username 和 Password（即 `SONATYPE_USERNAME` 和 `SONATYPE_PASSWORD`）

---

## 🔐 GPG 密钥设置

```bash
# 生成 GPG 密钥
gpg --gen-key
# 输入名称: HH-SpringAI-Agent-Starter
# 输入邮箱: hh.springai@gmail.com

# 查看生成的密钥
gpg --list-keys

# 上传公钥到 keyserver
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID

# 验证公钥已同步
gpg --keyserver keyserver.ubuntu.com --recv-keys YOUR_KEY_ID
```

## 🔐 设置环境变量

```bash
# Linux/macOS
export SONATYPE_USERNAME="your-sonatype-token-username"
export SONATYPE_PASSWORD="your-sonatype-token-password"
export GPG_KEY_NAME="HH-SpringAI-Agent-Starter"
export GPG_PASSPHRASE="your-gpg-passphrase"

# Windows PowerShell
$env:SONATYPE_USERNAME="your-sonatype-token-username"
$env:SONATYPE_PASSWORD="your-sonatype-token-password"
$env:GPG_KEY_NAME="HH-SpringAI-Agent-Starter"
$env:GPG_PASSPHRASE="your-gpg-passphrase"
```

---

## 🚀 执行发布

### 方式一：命令行（推荐）

```bash
# Linux/macOS
./scripts/release.sh patch   # 自动 patch 版本发布

# Windows
scripts\release.bat patch
```

### 方式二：手动分步执行

```bash
# 1. 设置为 RELEASE 版本
mvn versions:set -DnewVersion=0.0.3
git add pom.xml */pom.xml
git commit -m "Release v0.0.3"

# 2. 创建 Git 标签
git tag -a v0.0.3 -m "Release v0.0.3"

# 3. 编译并测试
mvn clean test

# 4. 发布到 Maven Central（需激活 release profile）
mvn clean deploy -P release -DskipTests \
    -Dgpg.keyname="$GPG_KEY_NAME" \
    -Dgpg.passphrase="$GPG_PASSPHRASE"

# 5. 推送到 GitHub
git push origin main --tags

# 6. 切换到下一个 SNAPSHOT
mvn versions:set -DnewVersion=0.0.4-SNAPSHOT
git add pom.xml */pom.xml
git commit -m "Prepare for next development iteration: 0.0.4-SNAPSHOT"
git push origin main
```

---

## ✅ 发布后验证

1. **Maven Central**: https://central.sonatype.com/artifact/com.mcp.enterprise/mcp-core
   - 发布后约 15 分钟可见

2. **GitHub Release**: https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise/releases
   - GitHub Actions 自动发布到 Docker

3. **验证安装**：
   ```xml
   <dependency>
       <groupId>com.mcp.enterprise</groupId>
       <artifactId>mcp-spring-boot-starter</artifactId>
       <version>0.0.3</version>
   </dependency>
   ```

---

## ❓ 常见问题

### Q: 发布失败提示 "Missing required artifacts"

确保以下文件都存在：
- `mcp-core/target/*.jar`（主 JAR）
- `mcp-core/target/*-sources.jar`（源码）
- `mcp-core/target/*-javadoc.jar`（Javadoc）
- GPG `.asc` 签名文件

### Q: Sonatype 提示 "Invalid POM"

检查 pom.xml 是否包含所有必需字段：
- groupId, artifactId, version
- name, description, url
- licenses（至少要有一个）
- developers（至少要有一个）
- scm（Git 仓库地址）

### Q: 发布 snapshot 版本

不需要 GPG 签名，直接运行：
```bash
mvn clean deploy -DskipTests
```

---

## 📊 发布状态

| 版本 | 状态 | Sonatype | GitHub Release |
|------|------|----------|---------------|
| 0.0.1 | ✅ 已完成 | ❌ 首次需手动 | ✅ 已上传 |
| 0.0.2 | ✅ 已完成 | ❌ 首次需手动 | ✅ 已上传 |
| 0.0.3 | 🔜 下一版 | - | - |

---

> **提示**：首次发布到 Central 需要人工审核（1-3 天），之后自动发布。
