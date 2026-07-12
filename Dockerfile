# ===========================================
# MCP Enterprise Server — Dockerfile
# 多阶段构建：编译 → 打包 → 运行
# ===========================================

# ---- 阶段 1: 编译 ----
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build
COPY pom.xml .
COPY mcp-core/pom.xml mcp-core/
COPY mcp-spring-boot-starter/pom.xml mcp-spring-boot-starter/
COPY mcp-monitor/pom.xml mcp-monitor/
COPY mcp-server/pom.xml mcp-server/
COPY mcp-tools/tool-database/pom.xml mcp-tools/tool-database/
COPY mcp-tools/tool-search/pom.xml mcp-tools/tool-search/
COPY mcp-tools/tool-system/pom.xml mcp-tools/tool-system/

# 预下载依赖（利用 Docker 层缓存）
RUN mvn dependency:go-offline --no-transfer-progress || true

# 复制源码并编译
COPY . .
RUN mvn clean package -DskipTests --no-transfer-progress

# ---- 阶段 2: 运行 ----
FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.title="MCP Enterprise Server"
LABEL org.opencontainers.image.description="企业级 MCP Server 框架 - Java/Spring Boot 实现"
LABEL org.opencontainers.image.source="https://github.com/HH-SpringAI-Agent-Starter/spring-ai-mcp-enterprise"
LABEL org.opencontainers.image.licenses="Apache-2.0"

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /build/mcp-server/target/*.jar app.jar

# 暴露端口
EXPOSE 8081

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD sh -c 'wget -qO- http://localhost:${PORT:-8080}/ || exit 1'

# 启动
ENTRYPOINT ["java", "-jar", "app.jar"]
