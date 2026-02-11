# FOTA 系统架构重构文档

**日期**：2026-02-11
**分支**：refactor/architecture-restructure
**负责人**：Claude Sonnet 4.5
**状态**：已完成

---

## 一、变更概述

本次重构对 FOTA 系统的代码结构进行了全面调整，从传统的平铺结构转变为清晰的领域驱动设计（DDD）分层架构。

**核心变更**：
- 🏗️ 重新组织包结构，按领域划分（device、product、firmware、policy、analytics）
- 📦 建立应用层和适配器层，实现跨领域编排
- 🔧 引入基础设施层，独立技术实现（分布式锁、Leader 选举）
- ⚙️ 添加 app.mode 配置控制，支持 main/region 双部署模式

**影响范围**：
- fota-service 模块：约 3000+ 行代码，50+ 个文件
- fota-framework-database 模块：配置调整
- 新增：9 个配置类，8 个控制器

---

## 二、新的包结构

### 2.1 顶层结构

```
com.wewins.fota/
├── adapter/                    # 适配器层（API 接口）
│   ├── api/
│   │   ├── device/         # 设备 API（/v1/upgrade/*）
│   │   ├── admin/           # 管理后台 API（/admin/*）
│   │   └── internal/         # 内部 API（/internal/*）
├── application/               # 应用层（用例/应用服务）
│   ├── upgrade/           # 升级应用服务
│   ├── policy/             # 策略应用服务
│   └── region/             # 区域应用服务
├── domain/                   # 领域层（实体、仓储、领域服务）
│   ├── device/           # 设备领域
│   │   ├── cache/          # 设备缓存
│   │   ├── entity/         # 设备实体
│   │   ├── mapper/         # 设备数据访问
│   │   ├── repository/     # 设备仓储接口
│   │   └── service/        # 设备领域服务
│   ├── product/          # 产品领域
│   │   ├── entity/         # 产品实体
│   │   ├── mapper/         # 产品数据访问
│   │   ├── repository/     # 产品仓储接口
│   │   └── service/        # 产品领域服务
│   ├── firmware/        # 固件领域
│   │   ├── entity/         # 固件实体
│   │   ├── mapper/         # 固件数据访问
│   │   ├── repository/     # 固件仓储接口
│   │   └── service/        # 固件领域服务
│   ├── policy/           # 策略领域
│   │   ├── entity/         # 策略实体
│   │   ├── mapper/         # 策略数据访问
│   │   ├── repository/     # 策略仓储接口
│   │   └── service/        # 策略领域服务
│   ├── analytics/        # 事件分析领域（独立）
│   │   ├── entity/         # ClickHouse 实体
│   │   ├── enums/          # 枚举类型
│   │   ├── mapper/         # ClickHouse Mapper
│   │   └── service/        # ClickHouse 服务
├── infra/                     # 基础设施层
│   ├── config/          # 配置类
│   │   ├── AppProperties.java            # 应用配置属性
│   │   ├── RegionModeConfig.java       # 区域模式配置
│   │   ├── MainModeConfig.java        # 主区域模式配置
│   ├── lock/            # 分布式协调
│   │   │   ├── DistributedLockService    # 分布式锁接口
│   │   │   ├── RedisDistributedLockService # Redis 实现
│   │   │   ├── LeaderElectionService      # Leader 选举接口
│   │   │   └── RedisLeaderElectionService  # Redis 实现
│   └── mq/              # 消息队列
│       └── consumer/       # MQ 消费者
└── FotaApplication.java    # 应用启动类
```

### 2.2 旧的包结构（已废弃）

以下包和目录已被废弃，不应再使用：

| 旧路径 | 新路径 | 说明 |
|---------|--------|--------|
| `entity/` | `domain/*/entity` | 领域实体 |
| `mapper/` | `domain/*/mapper` | 领域数据访问 |
| `cache/` | `domain/device/cache` | 设备缓存 |
| `clickhouse/` | `analytics/` | 事件分析 |
| `service/DataIntegrityService.java` | `application/policy/` | 策略应用服务 |

### 2.3 文件移动清单

| 旧路径 | 新路径 | 类型 |
|---------|--------|--------|
| `entity/Device.java` | `domain/device/entity/Device.java` | 实体 |
| `entity/DeviceImportBatch.java` | `domain/device/entity/DeviceImportBatch.java` | 实体 |
| `mapper/DeviceMapper.java` | `domain/device/mapper/DeviceMapper.java` | Mapper |
| `cache/DeviceCache.java` | `domain/device/cache/DeviceCache.java` | DTO |
| `cache/DeviceCacheService.java` | `domain/device/cache/DeviceCacheService.java` | 服务 |
| `entity/Product.java` | `domain/product/entity/Product.java` | 实体 |
| `mapper/ProductMapper.java` | `domain/product/mapper/ProductMapper.java` | Mapper |
| `entity/FirmwareVersion.java` | `domain/firmware/entity/FirmwareVersion.java` | 实体 |
| `mapper/FirmwareVersionMapper.java` | `domain/firmware/mapper/FirmwareVersionMapper.java` | Mapper |
| `entity/UpgradePolicy.java` | `domain/policy/entity/UpgradePolicy.java` | 实体 |
| `mapper/UpgradePolicyMapper.java` | `domain/policy/mapper/UpgradePolicyMapper.java` | Mapper |
| `clickhouse/entity/*` | `analytics/entity/*` | 实体 |
| `clickhouse/mapper/*` | `analytics/mapper/*` | Mapper |
| `clickhouse/service/*` | `analytics/service/*` | 服务 |
| `mq/consumer/UpgradeEventConsumer.java` | `infra/mq/consumer/UpgradeEventConsumer.java` | 消费者 |

### 2.4 配置变更清单

| 配置文件 | 变更说明 |
|---------|--------|--------|
| `application.yml` | 更新 `type-aliases-package` 为新的领域包路径 |
| `application-main.yml` | 新增：主区域 API 配置、子区域注册表、集群配置 |
| `application-region.yml` | 新增：区域配置、集群配置，禁用 Liquibase |
| `fota-framework-database/src/main/java/.../MybatisPrimaryConfig.java` | 更新 `@MapperScan` 为领域 mapper 包 |
| `fota-framework-database/src/main/java/.../MybatisClickHouseConfig.java` | 更新 `@MapperScan` 和 `mapperLocations` 为 analytics |

### 2.5 新增配置类

| 类名 | 路径 | 说明 |
|---------|--------|--------|
| `AppProperties.java` | `infra/config/AppProperties.java` | 应用配置属性类 |
| `RegionProperties.java` | `infra/config/RegionProperties.java` | 区域配置属性 |
| `MainProperties.java` | `infra/config/MainProperties.java` | 主区域配置属性 |
| `ClusterProperties.java` | `infra/config/ClusterProperties.java` | 集群配置属性 |
| `RegionModeConfig.java` | `infra/config/RegionModeConfig.java` | 区域模式条件配置 |
| `MainModeConfig.java` | `infra/config/MainModeConfig.java` | 主区域条件配置 |

### 2.6 新增服务

| 服务名 | 路径 | 说明 |
|---------|--------|--------|
| `DistributedLockService.java` | `infra/lock/DistributedLockService.java` | 分布式锁接口 |
| `RedisDistributedLockService.java` | `infra/lock/RedisDistributedLockService.java` | Redis 锁实现 |
| `LeaderElectionService.java` | `infra/lock/LeaderElectionService.java` | Leader 选举接口 |
| `RedisLeaderElectionService.java` | `infra/lock/RedisLeaderElectionService.java` | Leader 选举实现 |

### 2.7 新增控制器

| 控制器 | 路径 | 说明 |
|---------|--------|--------|
| `UpgradeCheckController.java` | `adapter/api/device/UpgradeCheckController.java` | GET/POST /v1/upgrade/check |
| `UpgradeReportController.java` | `adapter/api/device/UpgradeReportController.java` | POST /v1/upgrade/report |
| `ProductController.java` | `adapter/api/admin/ProductController.java` | 产品管理 CRUD |
| `FirmwareVersionController.java` | `adapter/api/admin/FirmwareVersionController.java` | 固件版本管理 |
| `UpgradePolicyController.java` | `adapter/api/admin/UpgradePolicyController.java` | 升级策略管理 |
| `DeviceController.java` | `adapter/api/admin/DeviceController.java` | 设备管理 |
| `ConfigVersionController.java` | `adapter/api/internal/ConfigVersionController.java` | 配置版本查询（仅 main） |
| `ConfigSnapshotController.java` | `adapter/api/internal/ConfigSnapshotController.java` | 配置快照拉取（仅 main） |
| `IngestController.java` | `adapter/api/internal/IngestController.java` | 数据汇总接入（仅 main） |

---

## 三、架构原则

### 3.1 分层职责

| 层级 | 职责 | 说明 |
|---------|--------|--------|
| **Adapter（适配器）** | 对外暴露 REST API，处理 HTTP 请求/响应 | 只调用 Application Service |
| **Application（应用）** | 跨领域编排，实现核心业务逻辑 | 调用多个领域的 Repository 和 Service |
| **Domain（领域）** | 封装业务实体，提供领域服务 | 可选：Repository 接口 |
| **Infrastructure（基础设施）** | 提供技术能力（Redis、RabbitMQ、ClickHouse） | 支撑业务逻辑 |

### 3.2 依赖方向

```
Controller → Application Service → Domain Repository/Infrastructure
```

**规则**：
- Controller 只调用 Application Service，不直接访问 Domain 或 Infrastructure
- Application Service 可调用 Domain Service 和 Repository
- Infrastructure 可被任何层调用
- Domain Service 不直接访问 Infrastructure

### 3.3 包命名规范

| 类型 | 命名格式 | 示例 |
|---------|--------|--------|
| 领域实体 | `com.wewins.fota.domain.device.entity.Device` |
| 领域服务 | `com.wewins.fota.domain.device.service.DeviceService` |
| 应用服务 | `com.wewins.fota.application.upgrade.UpgradeCheckService` |
| 适配器 | `com.wewins.fota.adapter.api.device.UpgradeCheckController` |

### 3.4 部署模式支持

**配置方式**：使用 `app.mode` 属性控制
- **main**：主区域模式（管理后台、配置中心、数据汇聚）
- **region**：区域模式（设备 API、配置同步、数据转发）

**条件装配**：使用 `@ConditionalOnProperty(name = "app.mode", havingValue = "main/region")`

**配置文件**：
- `application.yml` - 主配置（默认）
- `application-main.yml` - 主区域配置
- `application-region.yml` - 区域配置

### 3.5 分布式协调

#### 3.5.1 分布式锁

**用途**：配额扣减、灰度分桶、版本更新等需要原子操作

**实现**：基于 Redis SETNX + TTL

**关键特性**：
- Lua 脚本保证原子性（SET + GET + DELETE）
- TTL 自动过期，防止死锁
- 支持可配置的锁前缀和租约时间

#### 3.5.2 Leader 选举

**用途**：确保定时任务（配置同步、数据聚合）只在 Leader 实例执行

**实现**：
- Redis 存储 Leader 身份
- 定期续约（renewInterval）
- 其他实例通过检查键是否存在判断 Leader 状态

**关键特性**：
- TTL 自动过期，防止 Leader 崩溃后身份永久持有
- 实例 ID 区分（hostname + PID）

---

## 四、API 接口总览

### 4.1 设备 API（`/v1/upgrade/*`）

| 端点 | 方法 | 说明 |
|---------|--------|--------|
| GET /v1/upgrade/check | 检查设备更新（支持查询参数） |
| POST /v1/upgrade/check | 检查设备更新（支持扩展载荷） |
| POST /v1/upgrade/report | 上报升级状态 |

### 4.2 管理后台 API（`/admin/*`）

| 端点 | 资源 | 功能 |
|---------|--------|--------|
| /admin/product | 产品管理 |
| /admin/firmware | 固件版本管理 |
| /admin/policy | 升级策略管理 |
| /admin/device | 设备管理 |

### 4.3 内部 API（`/internal/*` - 仅 main 模式

| 端点 | 功能 |
|---------|--------|--------|
| /internal/config/version | 查询配置版本 |
| /internal/config/snapshot/{type} | 拉取配置快照（policy/product/control） |

---

## 五、部署模式对比

| 特性 | main 模式 | region 模式 | 说明 |
|---------|--------|--------|----------|
| 启用 API | 管理后台、配置中心 | 设备 API、内部 API |
| 启用功能 | 设备 API、配置同步、数据转发、Forwarder |
| 启用配置 | app.mode=region, 集群、分布式锁、Leader 选举 |
| 禁用配置 | Liquibase（由主区域管理） |

---

## 六、后续行动计划

### 6.1 短期（1-2 个月）

1. **完善应用层服务实现**
   - 补齐 UpgradeCheckService 的灰度、配额、时间窗口检查逻辑
   - 实现策略匹配的完整算法（版本、标签、灰度）
   - 添加设备标签过滤功能

2. **添加单元测试**
   - 为所有新增的 Application Service 编写测试
   - 为 Controller 编写集成测试

3. **集成测试**
   - 端到端测试（MockMvc）
   - 性能测试

### 6.2 中期（3-6 个月）

1. **性能优化**
   - 设备缓存预热
   - ClickHouse 批量写入优化
   - API 响应压缩

2. **功能增强**
   - 灰度发布流量控制
   - 策略版本回滚
   - 设备批量导入

3. **运维工具**
   - 集群监控面板
   - 分布式锁监控
   - 配置版本管理界面

---

## 七、参考资料

- [领域驱动设计（Eric Evans）](https://www.domainlanguage.com/zh/docs/)
- [MyBatis-Plus 官方文档](https://mybatis.plus.org/)
- [Spring Boot 条件装配](https://docs.spring.io/spring-boot/docs/current/reference/html/core/index.html)

---

**文档维护**：本文档应与代码同步更新，记录架构演进过程
