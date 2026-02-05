# Day 1: 项目基础架构搭建

> **任务编号**: T01
> **所属 Sprint**: Sprint 1
> **所属 Day**: Day 1
> **状态**: ✅ Done

---

## 📋 基本信息

**负责人**: FOTA 团队
**开始日期**: 2026-02-05
**完成日期**: 2026-02-05
**实际工时**: 8 小时

---

## 🎯 目标与范围

### 目标
搭建 Spring Boot 3.5.9 + Java 21 的多模块项目基础架构

### 包含
- ✅ Maven 多模块项目结构
- ✅ Spring Boot 3.5.9 升级（修复安全漏洞）
- ✅ MyBatis-Plus + Liquibase 技术栈
- ✅ PostgreSQL + Redis + RabbitMQ + ClickHouse 集成
- ✅ Lombok 代码简化
- ✅ Jackson JSON 序列化
- ✅ Git Flow 工作流规范

### 不包含
- ❌ 具体业务逻辑实现
- ❌ 数据库表创建
- ❌ API 接口实现
- ❌ 完整 RBAC 实现

---

## 📦 交付物

### 代码
- [x] 多模块 Maven 项目（11个模块）
- [x] Spring Boot 应用入口
- [x] 基础配置文件
- [x] Dockerfile

### 文档
- [x] Git Flow 工作流规范
- [x] Jackson 配置指南
- [x] Lombok 使用规范
- [x] 文档导航首页

---

## 🔧 技术栈

### 核心框架
- Spring Boot: 3.5.9
- Java: 21

### 数据库层
- ORM: MyBatis-Plus 3.5.15
- 迁移: Liquibase (Spring Boot BOM 管理)
- 主数据库: PostgreSQL 16+
- 分析数据库: ClickHouse

### 缓存和消息队列
- Redis: 缓存、限流、Bitmap
- RabbitMQ: 消息队列

### 对象存储
- AWS S3 SDK 2.25.43 (兼容 MinIO/阿里云OSS)

### 代码质量
- Lombok 1.18.36
- Jackson (Spring Boot 默认)

### 安全
- JWT: jjwt 0.12.6
- Spring Security (最新版本)

---

## 📂 项目结构

```
wewins-fota-new/
├── fota-bom/ (依赖版本管理)
├── fota-framework/ (框架聚合模块)
│   ├── fota-framework-common/
│   ├── fota-framework-database/
│   ├── fota-framework-cache/
│   ├── fota-framework-mq/
│   ├── fota-framework-storage/
│   ├── fota-framework-security/
│   └── fota-framework-starter/
└── fota-service/ (Spring Boot 应用)
```

---

## ⚙️ 配置要点

### Maven 配置
- BOM 统一版本管理
- 父 POM 定义所有依赖版本
- 子模块继承父 POM

### 多环境支持
- MODE=main: 主区域模式
- MODE=region: 区域模式
- 条件装配控制模块启用

### Docker 配置
- 多阶段构建
- 阿里云镜像仓库
- 健康检查配置

---

## ✅ 验收标准

### 功能验收
- [x] Maven 编译成功
- [x] Maven 打包成功
- [x] JAR 包可正常运行
- [x] Docker 镜像构建成功

### 代码质量
- [x] 无安全漏洞警告（IDEA 提示）
- [x] 依赖版本统一管理
- [x] 模块职责清晰

### 文档验收
- [x] Git Flow 工作流规范完整
- [x] Jackson/Lombok 配置指南完整
- [x] 文档导航清晰

---

## 📝 实施笔记

### 关键决策

1. **多模块 vs 单模块**
   - 决策：采用多模块架构
   - 理由：职责清晰，便于维护

2. **技术栈选型**
   - MyBatis-Plus vs JPA → 选择 MyBatis-Plus
   - Liquibase vs Flyway → 选择 Liquibase
   - 理由：SQL 可控，适合复杂场景

3. **版本管理**
   - 决策：使用 BOM 统一版本
   - 理由：避免版本冲突

### 遇到的问题

**问题 1**: Flyway 版本冲突
- **解决**: 移除 Flyway，使用 Liquibase
- **影响**: 无

**问题 2**: 安全漏洞警告
- **解决**: 升级 Spring Boot 到 3.5.9
- **影响**: 修复所有已知 CVE

### 踩坑经验

1. **MyBatis-Plus 版本兼容**
   - 必须使用 Spring Boot 3 专用 starter
   - Artifact: `mybatis-plus-spring-boot3-starter`

2. **Lombok 配置**
   - Maven compiler-plugin 需要配置 annotationProcessorPaths
   - IDEA 需要安装插件并启用注解处理

3. **Git Flow 合并策略**
   - feature → develop 必须使用 `--no-ff`
   - 保留完整历史，便于回滚

---

## 📚 相关资源

### 设计文档
- [Sprint 1 计划](../../05-plans/sprint-1.md)
- [系统架构文档](../../02-architecture/fota-architecture.md)

### 规范文档
- [Git Flow 工作流](../../03-standards/git-workflow.md)
- [Lombok 使用规范](../../03-standards/lombok-standards.md)
- [Jackson 配置指南](../../03-standards/jackson-config.md)

---

## 📌 回顾与改进

### 做得好的方面
- ✅ 模块划分清晰
- ✅ 版本管理规范
- ✅ 文档组织良好
- ✅ 技术选型合理

### 经验教训
1. **多模块重构要分步进行**
   - 先创建结构，再迁移代码
   - 每步验证编译

2. **Codex 协作很重要**
   - 架构讨论很充分
   - 避免了很多坑

### 改进建议
1. **可以更早创建文档结构**
   - 避免后期重构
   - 减少文档混乱

2. **应该使用 TaskList 工具**
   - 实时跟踪任务进度
   - 避免遗漏任务

---

**任务完成时间**: 2026-02-05 18:00
**Sprint 进度**: [████░░░░░░░░░░░] 10% (Day 1/5 完成)
