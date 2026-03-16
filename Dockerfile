# FOTA 多模块项目 Docker 镜像（多阶段构建）

# ================================
# Stage 1: 构建
# ================================
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /workspace

# 复制整个项目
COPY . .

# 构建整个项目
RUN mvn -Dmaven.test.skip=true clean package

# 验证 JAR 文件生成
RUN ls -la /workspace/fota-service/target/*.jar

# ================================
# Stage 2: 运行时
# ================================
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# 复制打包好的 JAR 文件
COPY --from=build /workspace/fota-service/target/fota-service-*.jar /app/app.jar
# 暴露端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
