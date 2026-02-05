# syntax=docker/dockerfile:1
#
# FOTA 多模块项目 Docker 镜像（多阶段构建）
# 支持两种运行模式：MODE=main 或 MODE=region

# ================================
# Stage 1: 构建
# ================================
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /workspace

# 1) 复制父 POM 和所有模块 POM（利用 Docker 缓存层）
COPY pom.xml .
COPY fota-bom/pom.xml fota-bom/
COPY fota-framework/pom.xml fota-framework/
COPY fota-framework/fota-framework-common/pom.xml fota-framework/fota-framework-common/
COPY fota-framework/fota-framework-storage/pom.xml fota-framework/fota-framework-storage/
COPY fota-framework/fota-framework-security/pom.xml fota-framework/fota-framework-security/
COPY fota-service/pom.xml fota-service/

# 下载依赖
RUN mvn -q -DskipTests dependency:go-offline

# 2) 复制源代码
COPY fota-bom/src fota-bom/src
COPY fota-framework/fota-framework-common/src fota-framework/fota-framework-common/src
COPY fota-framework/fota-framework-storage/src fota-framework/fota-framework-storage/src
COPY fota-framework/fota-framework-security/src fota-framework/fota-framework-security/src
COPY fota-service/src fota-service/src

# 3) 仅构建 fota-service 模块
RUN mvn -q -DskipTests -pl fota-service -am clean package

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
COPY --from=build /workspace/fota-service/target/fota-service-*.jar /app/app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:8080/actuator/health" || exit 1

# 暴露端口
EXPOSE 8080

# 启动命令
# MODE 环境变量映射到 spring.profiles.active
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=${MODE} -jar /app/app.jar"]
