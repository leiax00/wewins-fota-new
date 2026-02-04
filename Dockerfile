# syntax=docker/dockerfile:1
#
# FOTA 平台 Docker 镜像（多阶段构建）
# 支持两种运行模式：MODE=main 或 MODE=region

# ================================
# Stage 1: 构建
# ================================
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /workspace

# 复制 pom.xml 并下载依赖（利用 Docker 缓存层）
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# 复制源代码并打包
COPY src ./src
RUN mvn -q -DskipTests clean package

# ================================
# Stage 2: 运行时
# ================================
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# 环境变量
ENV MODE=main \
    REGION=main \
    JAVA_OPTS=""

# 复制打包好的 JAR 文件
COPY --from=build /workspace/target/*.jar /app/app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s \
  CMD java -jar /app/app.jar --dry-run || exit 1

# 暴露端口
EXPOSE 8080

# 启动命令
# MODE 环境变量映射到 spring.profiles.active
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=${MODE} -jar /app/app.jar"]
