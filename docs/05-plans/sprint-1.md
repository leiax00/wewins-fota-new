# Sprint 1: 基础平台搭建

> **时间**: 2026-02 (Week 1-5)
> **目标**: 搭建 FOTA 平台基础架构，完成核心链路最小可用版本

**Sprint Owner**: FOTA 架构组
**文档版本**: v1.0
**创建日期**: 2026-02-05

---

## 📋 Sprint 概览

### 目标
- 搭建 Spring Boot 3.5.9 + Java 21 基础架构
- 集成 PostgreSQL + Redis + RabbitMQ + ClickHouse
- 完成核心数据链路基础功能
- 实现最小授权模型

### 范围
- ✅ **包含**: 项目基础架构、数据库架构、缓存、消息队列、安全认证
- ❌ **不包含**: 管理后台、完整 RBAC、高级功能

### 验收标准
- [x] 项目编译打包成功
- [ ] 数据库表创建成功
- [ ] MyBatis-Plus CRUD 正常工作
- [ ] Redis 缓存正常工作
- [ ] 基础 API 可用
- [ ] 单元测试覆盖率 ≥ 60%

---

## 📅 Day 1: 项目基础架构搭建 ✅

**状态**: ✅ 已完成 (2026-02-05)
**分支**: `feature/project-structure`
**合并提交**: `c77abd6`

### 完成内容

#### 技术栈
- ✅ Spring Boot 3.5.9 + Java 21
- ✅ MyBatis-Plus 3.5.15
- ✅ Liquibase (Spring Boot BOM 管理)
- ✅ PostgreSQL + ClickHouse + Redis + RabbitMQ
- ✅ AWS S3 SDK
- ✅ Lombok 1.18.36 + Jackson

#### 项目架构
- ✅ 多模块 Maven 架构（11个模块）
  - fota-bom: 依赖版本管理
  - fota-framework: 框架聚合
  - fota-framework-common: 公共模块
  - fota-framework-database: 数据库层
  - fota-framework-cache: 缓存层
  - fota-framework-mq: 消息队列
  - fota-framework-storage: 对象存储
  - fota-framework-security: 安全模块
  - fota-framework-starter: 自动配置
  - fota-service: Spring Boot 应用

#### 代码质量
- ✅ BOM 统一版本管理
- ✅ Git Flow 工作流规范
- ✅ Jackson/Lombok 配置指南
- ✅ 安全漏洞修复

### 文档产出
- [x] docs/git-workflow.md
- [x] docs/jackson-config.md
- [x] docs/lombok-standards.md
- [x] docs/00-index.md (文档导航)
- [x] docs/task-progress.md (任务跟踪)

---

## 📅 Day 2: PostgreSQL 数据库架构 ⏳

**状态**: ⏳ 进行中
**分支**: `feature/database-schema`
**预计时间**: 1天

### 任务列表

#### 2-1: 创建 Liquibase changelog 结构
- [ ] 创建 `db/changelog/db.changelog-master.yaml`
- [ ] 创建 `db/changelog/changes/` 目录
- [ ] 配置 Spring Boot 集成

#### 2-2: 设计核心表结构
- [ ] products 表（产品表）
- [ ] devices 表（设备表）
- [ ] firmware_versions 表（固件版本表）
- [ ] upgrade_policies 表（升级策略表）

#### 2-3: 创建 MyBatis-Plus 实体和 Mapper
- [ ] Product.java
- [ ] Device.java
- [ ] FirmwareVersion.java
- [ ] UpgradePolicy.java

#### 2-4: 配置多数据源
- [ ] PostgreSQL 主数据源配置
- [ ] ClickHouse 分析数据源配置
- [ ] MyBatis-Plus 配置

#### 2-5: 验证数据库集成
- [ ] Liquibase 迁移测试
- [ ] MyBatis-Plus CRUD 测试
- [ ] 编译验证

### 验收标准
- [ ] Liquibase 脚本执行成功
- [ ] 表结构创建成功
- [ ] MyBatis-Plus 插入查询测试通过
- [ ] 多数据源配置正确

---

## 📅 Day 3: Redis 缓存架构 ⏸️

**状态**: ⏸️ 待开始
**预计时间**: 1天

### 任务列表
- [ ] Redis 配置
- [ ] 缓存键设计规范
- [ ] 设备活跃度 Bitmap 实现
- [ ] 策略缓存实现
- [ ] 限流功能

---

## 📅 Day 4: 简化认证与授权 ⏸️

**状态**: ⏸️ 待开始
**预计时间**: 1天

### 任务列表
- [ ] JWT 工具类
- [ ] 设备认证接口
- [ ] 简化用户认证
- [ ] API 权限控制

---

## 📅 Day 5: 集成测试与验收 ⏸️

**状态**: ⏸️ 待开始
**预计时间**: 1天

### 任务列表
- [ ] 集成测试
- [ ] 性能测试
- [ ] Week 1 验收
- [ ] 文档更新

---

## 🎯 关键决策（ADR）

### ADR-001: PostgreSQL 统一数据库
**状态**: ✅ Accepted
**决策**: 主业务库统一 PostgreSQL
**原因**: JSONB 支持、分区演进、与架构文档一致

### ADR-002: 分阶段安全建设
**状态**: ✅ Accepted
**决策**: M1 最小授权，M4 完整 RBAC
**原因**: 优先打通核心链路

### ADR-005: UTC 时间语义
**状态**: ✅ Accepted
**决策**: 存储层统一 UTC，策略时区可配置
**原因**: 避免时区混乱

---

## 📊 Sprint 进度

```
Sprint 1: [████░░░░░░░░░░░░] 10% (Day 1/5)

Day 1: ✅ 项目基础架构搭建
Day 2: ⏳ PostgreSQL 数据库架构
Day 3: ⏸️ Redis 缓存架构
Day 4: ⏸️ 简化认证与授权
Day 5: ⏸️ 集成测试与验收
```

---

## ⚠️ 风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 | 状态 |
|------|------|------|----------|------|
| PostgreSQL 上手困难 | 高 | 中 | 预留 1-2 天学习时间 | ⏸️ |
| 多数据源配置复杂 | 中 | 中 | 参考官方文档，先 PostgreSQL | ⏸️ |
| 时间不够完成全部 5 天 | 高 | 低 | Must-have 优先，Nice-to-have 砍删 | ⏸️ |

---

## 📝 变更日志

### 2026-02-05
- ✅ Day 1 完成
- 🔄 Day 2 进行中
- ✅ 文档重组（创建分类目录结构）

---

## 🔗 相关文档

- [详细任务列表](../tasks.md)
- [任务进度跟踪](../task-progress.md)
- [架构文档](02-architecture/fota-architecture.md)
- [产品需求](01-product/prd.md)
