# DDD 架构设计文档

- 文档版本：v1.0
- 创建日期：2026-02-05
- 最后更新：2026-02-05
- 作者：FOTA 架构组

## 目录

1. [架构概述](#架构概述)
2. [DDD 分层架构](#ddd-分层架构)
3. [限界上下文](#限界上下文)
4. [领域模型设计](#领域模型设计)
5. [分层职责](#分层职责)
6. [模块依赖](#模块依赖)
7. [重构路线图](#重构路线图)

---

## 架构概述

### 为什么选择 DDD？

**当前三层架构的问题**：
- ❌ 业务逻辑分散在 Service 层，缺乏领域模型
- ❌ 数据库实体（Entity）直接暴露给上层
- ❌ 贫血模型：只有 getter/setter，没有业务行为
- ❌ 业务规则散落各处，难以维护和测试
- ❌ 领域语言不统一，技术术语主导

**DDD 带来的价值**：
- ✅ 业务复杂性隔离在领域层
- ✅ 聚合根保护业务不变性
- ✅ 领域语言统一（Ubiquitous Language）
- ✅ 业务规则集中在领域模型中
- ✅ 易于测试和演进

---

## DDD 分层架构

### 依赖方向

```
┌─────────────────────────────────────────┐
│         adapter.api (用户接口层)         │
│  - REST Controller                      │
│  - DTO 转换                              │
│  - 鉴权、限流                            │
└─────────────────┬───────────────────────┘
                  │ 依赖
                  ↓
┌─────────────────────────────────────────┐
│       application (应用服务层)           │
│  - 用例编排                              │
│  - 事务边界                              │
│  - 调用领域服务与仓储                    │
└─────────────────┬───────────────────────┘
                  │ 依赖
                  ↓
┌─────────────────────────────────────────┐
│          domain (领域层)                │
│  - 聚合根、实体、值对象                  │
│  - 领域服务                              │
│  - 领域事件                              │
│  - 仓储接口（抽象）                      │
└─────────────────┬───────────────────────┘
                  │ 实现
                  ↑
┌─────────────────────────────────────────┐
│    infrastructure (基础设施层)          │
│  - MyBatis-Plus 仓储实现                │
│  - Redis 缓存                            │
│  - RabbitMQ 消息                         │
│  - 对象存储（S3/MinIO/OSS）              │
└─────────────────────────────────────────┘
```

### 关键原则

1. **依赖倒置**：领域层不依赖任何外层，是最稳定的内核
2. **接口隔离**：基础设施通过接口（Repository）依赖领域层
3. **业务内聚**：相关业务逻辑聚合在同一个限界上下文中

---

## 限界上下文

### 核心上下文

| 上下文 | 职责 | 核心业务 |
|--------|------|---------|
| **product** | 产品与固件元数据管理 | 固件版本、校验和、下载地址 |
| **device** | 设备注册与状态管理 | 设备档案、版本跟踪、活跃度 |
| **upgrade** | 升级策略与执行 | 灰度、时间窗、配额、升级决策 |
| **reporting** | 上报事件处理 | 状态上报、事件入库、统计分析 |
| **sync** | 主/区域配置同步 | 策略快照、配置拉取、版本同步 |

### 上下文关系图

```
┌──────────┐
│ product  │ ←──────┐
└──────────┘        │
      ↑             │ 依赖快照接口
      │             ↓
┌──────────┐   ┌──────────┐
│ upgrade  │ ←→│   sync   │
└──────────┘   └──────────┘
      ↓
┌──────────┐
│ device   │
└──────────┘
      ↓
┌──────────┐
│reporting │
└──────────┘
```

---

## 领域模型设计

### 1. Product 聚合（产品上下文）

**聚合根**：`Product`

**实体**：
- `FirmwarePackage`：固件包（内联在 Product 中）

**值对象**：
- `ProductId`：产品 ID
- `FirmwareVersion`：版本号（如 "1.0.0"）
- `Checksum`：校验和（MD5/SHA256）
- `DownloadUri`：下载地址（带签名）
- `FileMetadata`：文件大小、类型等

**领域规则**：
- 固件版本只能单向升级（语义化版本）
- Checksum 必须在注册时校验
- 固件包不可变（上传后不可修改）

**领域事件**：
- `FirmwarePublished`：固件发布
- `FirmwareDeprecated`：固件废弃

**仓储接口**：
```java
interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(ProductId id);
    List<Product> findAll();
}
```

---

### 2. Device 聚合（设备上下文）

**聚合根**：`Device`

**实体**：
- `DeviceProfile`：设备档案（可内联为值对象）

**值对象**：
- `DeviceId`：设备 ID（IMEI）
- `ProductId`：产品 ID
- `RegionId`：区域 ID
- `ActiveBitmapKey`：活跃度 Bitmap Key
- `DeviceTag`：设备标签（用于灰度分桶）

**领域规则**：
- 设备注册时生成唯一 ID
- 设备版本变更只能由 `DeviceVersionUpgraded` 事件触发
- 活跃度在设备上报时自动更新

**领域事件**：
- `DeviceRegistered`：设备注册
- `DeviceVersionUpgraded`：设备版本升级成功
- `DeviceActive`：设备活跃

**仓储接口**：
```java
interface DeviceRepository {
    Device save(Device device);
    Optional<Device> findById(DeviceId id);
    Optional<Device> findByImei(String imei);
}
```

---

### 3. UpgradePolicy 聚合（升级上下文）

**聚合根**：`UpgradePolicy`

**实体**：
- `PolicyRule`：策略规则（灰度、时间窗、配额）

**值对象**：
- `PolicyId`：策略 ID
- `GrayRate`：灰度比例（0-100）
- `TimeWindow`：时间窗口（start/end/timezone）
- `Quota`：配额（每日最大升级数）
- `TargetVersion`：目标版本

**领域规则**：
- 灰度命中：`Hash(IMEI) % 100 < grayRate`
- 时间窗口校验：当前时间必须在 window 内
- 配额预扣：检查剩余配额，扣减后返回
- 策略优先级：多个策略命中时，取优先级最高的

**领域事件**：
- `PolicyPublished`：策略发布
- `PolicyActivated`：策略激活
- `PolicyDeprecated`：策略废弃

**仓储接口**：
```java
interface UpgradePolicyRepository {
    UpgradePolicy save(UpgradePolicy policy);
    Optional<UpgradePolicy> findById(PolicyId id);
    List<UpgradePolicy> findActiveByProductId(ProductId productId);
}
```

---

### 4. UpgradeSession 聚合（升级上下文）

**聚合根**：`UpgradeSession`

**实体**：
- `UpgradeStep`：升级步骤（DL_START → DL_OK → UP_OK）

**值对象**：
- `SessionId`：会话 ID
- `UpgradeState`：升级状态（状态机）
- `ReportTimestamp`：上报时间戳

**领域规则**：
- 状态机单向前进，不可回退
- UP_OK 触发 `DeviceVersionUpgraded` 事件
- 会话超时自动失效

**领域事件**：
- `UpgradeSessionCreated`：会话创建
- `UpgradeStepProgressed`：步骤推进
- `UpgradeCompleted`：升级完成
- `UpgradeFailed`：升级失败

**仓储接口**：
```java
interface UpgradeSessionRepository {
    UpgradeSession save(UpgradeSession session);
    Optional<UpgradeSession> findById(SessionId id);
}
```

---

### 领域服务

有些业务逻辑不属于某个聚合，需要领域服务：

| 领域服务 | 职责 |
|---------|------|
| `UpgradeDecisionService` | 给定 Device + Policy，返回升级决策 |
| `PolicyMatchService` | 标签、范围、灰度命中判断 |
| `QuotaService` | 配额窗口校验与预扣 |
| `SignedUrlService` | 生成带签名的下载 URL（接口）|
| `BitmapService` | 设备活跃度 Bitmap 操作 |

---

## 分层职责

### adapter.api（用户接口层）

**职责**：
- 接收 HTTP 请求（Controller）
- 参数校验（@Valid）
- DTO 转换（Request → Command）
- 调用应用服务
- 返回响应（Response → DTO）

**不应该**：
- ❌ 包含业务逻辑
- ❌ 直接调用领域层
- ❌ 直接操作数据库

**示例**：
```java
@RestController
@RequestMapping("/v1/upgrade")
public class UpgradeController {

    private final UpgradeCheckAppService upgradeCheckAppService;

    @GetMapping("/check")
    public UpgradeCheckResponse check(@Valid UpgradeCheckRequest request) {
        UpgradeCheckCommand command = toCommand(request);
        UpgradeCheckDto result = upgradeCheckAppService.check(command);
        return toResponse(result);
    }
}
```

---

### application（应用服务层）

**职责**：
- 用例编排（Use Case Orchestration）
- 事务边界（@Transactional）
- 调用领域服务与仓储
- 返回 DTO（不暴露领域模型）

**不应该**：
- ❌ 包含业务规则（应在领域层）
- ❌ 直接依赖基础设施实现

**示例**：
```java
@ApplicationService
public class UpgradeCheckAppService {

    private final DeviceRepository deviceRepo;
    private final UpgradePolicyRepository policyRepo;
    private final UpgradeDecisionService decisionService;

    @Transactional
    public UpgradeCheckDto check(UpgradeCheckCommand command) {
        // 1. 加载聚合
        Device device = deviceRepo.findByImei(command.getImei())
            .orElseThrow(() -> new DeviceNotFoundException());

        List<UpgradePolicy> policies = policyRepo.findActiveByProductId(device.getProductId());

        // 2. 调用领域服务
        UpgradeDecision decision = decisionService.decide(device, policies);

        // 3. 返回 DTO
        return UpgradeCheckDto.from(decision);
    }
}
```

---

### domain（领域层）

**职责**：
- 核心业务逻辑
- 聚合根、实体、值对象
- 领域服务
- 领域事件
- 仓储接口（抽象）

**不应该**：
- ❌ 依赖 Spring/MyBatis 等框架
- ❌ 直接访问数据库
- ❌ 包含基础设施实现

**示例**：
```java
public class Device {

    private final DeviceId deviceId;
    private ProductId productId;
    private FirmwareVersion currentVersion;
    private DeviceStatus status;

    // 业务方法
    public void reportUpgrade(UpgradeReport report) {
        if (!report.isSuccess()) {
            this.status = DeviceStatus.UPGRADE_FAILED;
            return;
        }

        FirmwareVersion oldVersion = this.currentVersion;
        this.currentVersion = report.getTargetVersion();
        this.status = DeviceStatus.ACTIVE;

        // 发布领域事件
        this.registerEvent(new DeviceVersionUpgraded(
            this.deviceId,
            oldVersion,
            this.currentVersion
        ));
    }
}
```

---

### infrastructure（基础设施层）

**职责**：
- 实现仓储接口（MyBatis-Plus）
- 缓存实现（Redis）
- 消息发布（RabbitMQ）
- 对象存储（S3/MinIO/OSS）
- 外部系统适配器

**示例**：
```java
@Repository
public class DeviceRepositoryImpl implements DeviceRepository {

    private final DeviceMapper deviceMapper;
    private final DevicePoConverter converter;

    @Override
    public Device save(Device device) {
        DevicePO po = converter.toPo(device);
        deviceMapper.insert(po);
        return converter.toDomain(po);
    }

    @Override
    public Optional<Device> findByImei(String imei) {
        DevicePO po = deviceMapper.selectByImei(imei);
        return Optional.ofNullable(po)
            .map(converter::toDomain);
    }
}
```

---

## 模块依赖

### Maven 模块划分

```
fota-domain (领域层)
  - fota-domain-product
  - fota-domain-device
  - fota-domain-upgrade
  - fota-domain-shared

fota-application (应用层)
  - fota-application-upgrade
  - fota-application-device
  - fota-application-product

fota-adapter-api (用户接口层)
  - fota-adapter-api-rest
  - fota-adapter-api-internal

fota-infrastructure (基础设施层)
  - fota-infrastructure-persistence (MyBatis-Plus)
  - fota-infrastructure-cache (Redis)
  - fota-infrastructure-messaging (RabbitMQ)
  - fota-infrastructure-storage (S3)
```

### 依赖关系

```xml
<!-- fota-adapter-api-rest 依赖应用层 -->
<dependency>
    <groupId>com.wewins</groupId>
    <artifactId>fota-application-upgrade</artifactId>
</dependency>

<!-- fota-application-upgrade 依赖领域层 -->
<dependency>
    <groupId>com.wewins</groupId>
    <artifactId>fota-domain-upgrade</artifactId>
</dependency>

<!-- fota-infrastructure-persistence 依赖领域层 -->
<dependency>
    <groupId>com.wewins</groupId>
    <artifactId>fota-domain-device</artifactId>
</dependency>
```

---

## 重构路线图

### Phase 1：准备阶段（Day 3-4）

**目标**：建立 DDD 分层基础

**任务**：
1. ✅ 创建 DDD 架构设计文档（本文档）
2. ⬜ 创建新的 Maven 模块结构（fota-domain, fota-application）
3. ⬜ 引入 DDD 基础设施（AggregateRoot, DomainEvent, Repository）
4. ⬜ 团队培训：DDD 核心概念、聚合设计

**产出**：
- 新的模块骨架
- 基础类型定义（AggregateRoot, Entity, ValueObject, DomainEvent）

---

### Phase 2：领域模型提取（Day 5-7）

**目标**：从贫血模型转为富领域模型

**任务**：
1. ⬜ 提取 Product 聚合（从 Entity 转为 AggregateRoot）
2. ⬜ 提取 Device 聚合
3. ⬜ 提取 UpgradePolicy 聚合
4. ⬜ 定义值对象（FirmwareVersion, Checksum, GrayRate 等）
5. ⬜ 编写领域规则测试

**产出**：
- 4 个核心聚合根
- 10+ 个值对象
- 聚合根单元测试

---

### Phase 3：应用服务封装（Day 8-10）

**目标**：用应用服务封装用例

**任务**：
1. ⬜ 创建 UpgradeCheckAppService
2. ⬜ 创建 UpgradeReportAppService
3. ⬜ 创建 ProductManagementAppService
4. ⬜ Controller 改为调用应用服务
5. ⬜ 引入 Command/Query 模式

**产出**：
- 5+ 个应用服务
- 用例集成测试

---

### Phase 4：仓储模式（Day 11-13）

**目标**：隔离领域层和数据访问层

**任务**：
1. ⬜ 定义仓储接口（在领域层）
2. ⬜ MyBatis-Plus 实现仓储（在基础设施层）
3. ⬜ 引入 PO 转换器（Domain ↔ PO）
4. ⬜ 领域层不依赖 MyBatis-Plus 注解

**产出**：
- 4 个仓储接口
- MyBatis-Plus 实现
- PO 转换器

---

### Phase 5：领域事件（Day 14-16）

**目标**：解耦聚合间协作

**任务**：
1. ⬜ 定义领域事件（DeviceVersionUpgraded 等）
2. ⬜ 引入事件发布器（DomainEventPublisher）
3. ⬜ 实现事件订阅者（RabbitMQ）
4. ⬜ 设备版本升级改为事件驱动

**产出**：
- 事件总线基础设施
- 5+ 个领域事件
- 事件订阅处理器

---

### Phase 6：清理旧代码（Day 17-18）

**目标**：删除旧的三层架构代码

**任务**：
1. ⬜ 删除旧的 Service 层（已迁移到应用服务）
2. ⬜ 删除 Entity 直接暴露给 Controller 的代码
3. ⬜ 统一错误处理
4. ⬜ 性能测试与优化

**产出**：
- 清爽的 DDD 架构
- 性能测试报告

---

## 实施指南

### 如何开始？

**Sprint 1（当前）**：
- ✅ Day 1-2：基础框架搭建
- ⬜ Day 3：创建 DDD 模块骨架
- ⬜ Day 4：定义基础类型和工具类
- ⬜ Day 5：提取第一个聚合（Product）

**Sprint 2**：
- ⬜ Day 6-7：提取 Device、UpgradePolicy 聚合
- ⬜ Day 8-10：应用服务封装

**Sprint 3**：
- ⬜ Day 11-13：仓储模式
- ⬜ Day 14-16：领域事件

### 渐进式重构原则

1. **不停止新功能开发**：重构与新功能并行
2. **小步提交**：每个聚合独立提交
3. **测试保护**：先写测试，再重构
4. **文档同步**：及时更新架构文档

---

## 附录

### A. 参考资源

- **领域驱动设计**：Eric Evans
- **实现领域驱动设计**：Vaughn Vernon
- **DDD 实战**：基于 Spring Boot 实现

### B. 术语表

| 术语 | 说明 |
|------|------|
| 聚合根 | 聚合的入口，保证一致性边界 |
| 实体 | 有唯一标识的对象 |
| 值对象 | 不可变的、通过属性值相等的对象 |
| 领域服务 | 不属于某个聚合的业务逻辑 |
| 领域事件 | 领域内发生的业务事件 |
| 仓储 | 领域对象的持久化抽象 |
| 限界上下文 | 领域的边界，划分业务范围 |

### C. 代码示例仓库

待补充：DDD 架构示例代码

---

**变更历史**：

| 版本 | 日期 | 变更内容 | 变更人 |
|------|------|---------|--------|
| v1.0 | 2026-02-05 | 初版：DDD 架构设计、领域模型、重构路线图 | FOTA 架构组 |
