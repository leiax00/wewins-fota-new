# FOTA 项目文档导航

> 本项目文档采用分类管理，便于查找、维护和归档。

**最后更新**: 2026-03-08
**文档版本**: v1.6

---

## 📚 文档分类

### 1️⃣ 产品与需求 (`01-product/`)
产品需求、业务范围、验收标准

- [产品需求文档 (PRD)](01-product/prd.md)

---

### 2️⃣ 架构与设计 (`02-architecture/`)
系统架构、技术选型、设计决策

- [FOTA 系统架构及技术说明书](02-architecture/fota-architecture.md)
- [升级检查流程架构](02-architecture/upgrade-check-flow-architecture.md) 🆕
- [DDD 架构设计文档](02-architecture/ddd-architecture.md)
- [DDD 实施指南](02-architecture/ddd-implementation-guide.md)
- [API 分层与目录边界约定](02-architecture/api-layer-boundaries.md)
- [文件存储策略说明](02-architecture/storage-strategy.md)
- [前端架构设计](02-architecture/frontend-architecture.md)
- [前端移动端策略 ADR](02-architecture/frontend-mobile-strategy-adr.md)

---

### 3️⃣ 规范与标准 (`03-standards/`)
团队约定、编码规范、流程规范

- [Git Flow 工作流规范](03-standards/git-workflow.md)
- [Lombok 使用规范](03-standards/lombok-standards.md)
- [Jackson 配置指南](03-standards/jackson-config.md)
- [Redis 缓存标准与规范](03-standards/redis-cache-standards.md) 🆕

---

### 4️⃣ 技术专题 (`04-technical/`)
负载控制、设备 API、专题技术设计

- [动态周期与负载控制设计说明](04-technical/dynamic-interval-and-load-control.md)
- [多实例负载评估与动态周期优化方案](04-technical/multi-instance-monitoring-improvement.md) 🆕
- [负载控制运行期配置设计](04-technical/load-control-runtime-configuration.md) 🆕
- [升级检查 API 设计](04-technical/upgrade-check-api.md)
- [升级上报 API 设计](04-technical/upgrade-report-api.md)
- [固件下载签名设计](04-technical/signed-url-spec.md)

---

### 5️⃣ 指南与知识 (`04-guides/`)
技术指南、知识沉淀、最佳实践

- [策略权限控制指南](04-guides/policy-permissions.md) 🆕
- [时区语义设计](04-guides/time-semantics.md)

---

### 6️⃣ 计划与里程碑 (`05-plans/`)
Sprint 计划、里程碑、风险评估

- [Sprint 1 计划 - 后端基础平台](05-plans/sprint-1.md)
- [Sprint 2 计划 - 管理后台前端](05-plans/sprint-2-frontend.md)
- [Sprint 3 计划 - 核心链路开发](05-plans/sprint-3-core-pipeline.md)
- [Sprint 4 计划 - 动态周期调整与监控](05-plans/sprint-4-load-control.md) 🆕
- [feature/fota-ui-console 功能完成清单](05-plans/feature-fota-ui-console-summary.md)

---

### 7️⃣ 任务执行 (`tasks/`)
任务文档、进度跟踪、验收清单

#### Sprint 1 (2026-02)

**Day 1: 项目基础架构搭建** ✅ 已完成
- [任务文档](tasks/2026-02-sprint-1/day-01-project-setup.md)
- [进度跟踪](tasks/2026-02-sprint-1/day-01-progress.md)

**Day 2: PostgreSQL 数据库架构** ✅ 已完成
- [任务文档](tasks/2026-02-sprint-1/day-02-database.md)
- [最终总结](tasks/2026-02-sprint-1/day-02-final-summary.md)

**Day 3: 系统管理与安全认证** ✅ 已完成
- [任务文档](tasks/2026-02-sprint-1/day-03-system-security.md)

**Day 4: Redis 缓存架构** ⏸️ 待开始
- [任务文档](tasks/2026-02-sprint-1/day-04-redis-cache.md)

**Day 5: 集成测试与验收** ⏸️ 待开始

---

## 🎯 快速导航

### 按角色查找

**产品经理**:
- [产品需求文档](01-product/prd.md)
- [Sprint 计划](05-plans/sprint-1.md)

**架构师/技术负责人**:
- [系统架构文档](02-architecture/fota-architecture.md)
- [前端架构设计](02-architecture/frontend-architecture.md) 🆕
- [时区语义设计](04-guides/time-semantics.md)

**前端工程师**:
- [前端架构设计](02-architecture/frontend-architecture.md) 🆕
- [Sprint 2 前端计划](05-plans/sprint-2-frontend.md) 🆕

**开发工程师**:
- [Git Flow 工作流](03-standards/git-workflow.md)
- [Lombok 规范](03-standards/lombok-standards.md)
- [Jackson 配置](03-standards/jackson-config.md)
- [Redis 缓存标准](03-standards/redis-cache-standards.md)
- [当前任务](#️⃣-任务执行)

**测试工程师**:
- [任务文档](tasks/2026-02-sprint-1/)
- [验收清单](tasks/2026-02-sprint-1/day-05-progress.md)

---

## 📊 项目进度

```
Sprint 1: 基础平台搭建 [██████████████████] 100% (Day 1-5 完成 ✅)
Sprint 2: 管理后台前端 [████████████████░░] 85%  (核心功能完成，表单待完善)
Sprint 3: 核心链路开发 [███████████████████] 98% (核心功能完成，性能测试待执行)
Sprint 4: 动态周期调整 [░░░░░░░░░░░░░░░░░░░░] 0%   (待开始)

Day 1: ✅ 项目基础架构搭建 (2026-02-05)
Day 2: ✅ PostgreSQL 数据库架构 (2026-02-06)
Day 3: ✅ 系统管理与安全认证 (2026-02-10)
Day 4: ✅ Redis 缓存架构 (2026-02-17)
Day 5: ✅ 集成测试与验收 (2026-02-18)
```

**Sprint 1 状态**: ✅ 圆满完成！所有核心功能已实现并集成。
**Sprint 2 状态**: 🔄 进行中（85%）- 核心功能基本完成，部分表单和交互待完善
**Sprint 3 状态**: 🔄 收尾中（98%）- 核心链路完成，单元测试覆盖率和性能测试待执行
**Sprint 4 状态**: 📋 待开始 - 动态周期调整与系统可观测性

---

## 📝 文档更新日志

### 2026-03-14
- ✅ 新增负载控制运行期配置设计文档
- ✅ 补充实例级 / 区域级评分配置与 Sentinel 统一管理方案
- ✅ 更新 Sprint 4 文档与技术专题导航
- ✅ 明确采用字典作为配置源，当前阶段不新增独立配置页

### 2026-03-08
- ✅ 新增 Sprint 4 计划：动态周期调整与系统可观测性
- ✅ 集成 Sentinel 限流熔断方案
- ✅ 智能退避算法设计
- ✅ 自建监控仪表盘方案

### 2026-03-07
- ✅ 新增升级检查流程架构文档
- ✅ 定义分层设计、数据流、性能指标
- ✅ MQ 异步处理配置（CheckLog、DeviceInfoUpdate）
- ✅ 批量更新 SQL 设计（单条 SQL 更新 500 条）

### 2026-02-28
- ✅ 更新 Sprint 2 进度为 85%
- ✅ 更新 Sprint 2 任务状态清单
- ✅ 补充已完成的设备管理功能（导入、批量操作、批次管理）
- ✅ 补充后端增强功能（固件分步上传、动态菜单、权限控制）
- ✅ 更新项目进度显示

### 2026-02-27
- ✅ 新增策略权限控制指南文档
- ✅ 记录状态切换、策略修改、策略删除的权限规则
- ✅ 添加测试人员和发布人员的权限矩阵
- ✅ 提供前端实现建议和常见问题解答

### 2026-02-20
- ✅ 更新 Sprint 2 状态为进行中（约 40%）
- ✅ 同步前端登录页主题重构与主题切换入口进度
- ✅ 更新前端架构文档为“规划 + 实现快照”
- ✅ 同步 TabsView / Breadcrumb / Header-Sidebar 联动实现
- ✅ 记录当前“桌面优先（min-width: 1024）”策略
- ✅ 新增前端移动端策略 ADR（桌面优先）

### 2026-02-18
- ✅ Day 5 完成：集成测试与验收
- ✅ Sprint 1 圆满完成（100%）
- ✅ Week 1 验收总结
- ✅ 更新文档导航索引
- ✅ 更新 Sprint 1 进度

### 2026-02-17
- ✅ 创建前端架构设计文档
- ✅ 创建 Sprint 2 前端迭代计划
- ✅ 更新文档导航，添加前端相关链接

### 2026-02-17
- ✅ Day 4 完成：Redis 缓存架构
- ✅ Redis 配置补齐、Bitmap 实现、限流功能
- ✅ UpgradeCheckService 集成完成
- ✅ 更新文档导航索引
- ✅ 更新 Sprint 1 进度：80%

### 2026-02-17
- ✅ 创建 Day 4: Redis 缓存架构实施计划
- ✅ 创建 Redis 缓存标准与规范文档
- ✅ 更新文档导航索引

### 2026-02-10
- ✅ 创建 Day 4: Redis 缓存架构实施计划
- ✅ 创建 Redis 缓存标准与规范文档
- ✅ 更新文档导航索引

### 2026-02-10
- ✅ 标记 Day 2 为已完成
- ✅ 标记 Day 3 为已完成（系统管理与安全认证）
- ✅ 添加 Day 3 任务文档
- ✅ 更新项目进度：60%

### 2026-02-05
- ✅ 重新组织文档结构
- ✅ 创建文档导航首页
- ✅ 创建任务模板
- 🔄 迁移现有文档到新结构

---

## 🔗 相关链接

- [项目 README](../README.md)
- [Git 仓库](../)
- [任务列表](../docs/tasks.md)

---

**文档维护**: 本文档应在每次新增文档或结构调整时更新。
