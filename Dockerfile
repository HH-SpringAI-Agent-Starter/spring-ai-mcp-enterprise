# ===========================================
# MCP Enterprise Server — Dockerfile
# 多阶段构建：编译 → 打包 → 运行
# 目标运行时：Java 17 (与项目 pom.xml 一致)
# ===========================================

# ---- 阶段 1: 编译 ----
# 使用 Java 17 构建镜像，与项目 java.version=17 严格一致
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 仅拷贝 pom.xml 以利用 Docker 层缓存预下载依赖
COPY pom.xml .
COPY mcp-core/pom.xml mcp-core/
COPY mcp-spring-boot-starter/pom.xml mcp-spring-boot-starter/
COPY mcp-monitor/pom.xml mcp-monitor/
COPY mcp-auth/pom.xml mcp-auth/
COPY mcp-server/pom.xml mcp-server/
COPY mcp-tools/tool-database/pom.xml mcp-tools/tool-database/
COPY mcp-tools/tool-search/pom.xml mcp-tools/tool-search/
COPY mcp-tools/tool-system/pom.xml mcp-tools/tool-system/
COPY mcp-tools/tool-weather/pom.xml mcp-tools/tool-weather/
COPY mcp-tools/tool-calculator/pom.xml mcp-tools/tool-calculator/
COPY mcp-tools/tool-http/pom.xml mcp-tools/tool-http/
COPY mcp-tools/tool-finance/pom.xml mcp-tools/tool-finance/
COPY mcp-integrations/mcp-alibaba/pom.xml mcp-integrations/mcp-alibaba/
COPY mcp-examples/mcp-client-spring-ai/pom.xml mcp-examples/mcp-client-spring-ai/

# 预下载依赖（利用 Docker 层缓存）；失败不阻塞（源码阶段会重试）
RUN mvn dependency:go-offline --no-transfer-progress || true

# 复制源码并构建 mcp-server 及其依赖链
# (-pl mcp-server -am: 只构建 server 与所需模块，避免可选集成模块拉取额外仓库)
COPY . .
RUN mvn clean package -DskipTests -pl mcp-server -am --no-transfer-progress

# ---- 阶段 2: 运行 ----
FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.title="MCP Enterprise Server"
LABEL org.opencontainers.image.description="企业级 MCP Server 框架 - Java/Spring Boot 实现"
LABEL org.opencontainers.image.source="https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise"
LABEL org.opencontainers.image.licenses="Apache-2.0"

WORKDIR /app

# 从构建阶段复制 fat JAR
COPY --from=builder /build/mcp-server/target/*.jar app.jar

# 暴露端口
EXPOSE 8081

# 启动
ENTRYPOINT ["java", "-jar", "app.jar"]
