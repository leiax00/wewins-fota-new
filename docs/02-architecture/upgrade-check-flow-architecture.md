# 升级检查流程架构

> **版本**: v1.0
> **最后更新**: 2026-03-07
> **关联文档**: [升级检查 API 规范](../04-technical/upgrade-check-api.md)

---

## 概述

本文档描述设备升级检查（`/v1/upgrade/check`）的内部实现架构，包括分层设计、核心组件、数据流和性能指标。

---

## 架构总览

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                    API Layer                                         │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │  UpgradeCheckController                                                      │   │
│  │  - GET/POST /v1/upgrade/check                                                │   │
│  │  - GET /fota/version/query (兼容老接口)                                       │   │
│  │  - buildLogContext() → 提取 clientIp/userAgent/region                        │   │
│  │  - convertToRespDTO() → CheckResult → UpgradeCheckRespDTO                    │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                               Application Layer                                      │
│  ┌─────────────────────────────────────────────────────────────────────────────┐   │
│  │  UpgradeCheckService                                                         │   │
│  │  ┌───────────────────────────────────────────────────────────────────────┐  │   │
│  │  │  CheckContext (上下文封装)                                              │  │   │
│  │  │  - request, logContext, requestId                                      │  │   │
│  │  │  - product, device, versionId, matchedPolicy                           │  │   │
│  │  │  - result                                                              │  │   │
│  │  └───────────────────────────────────────────────────────────────────────┘  │   │
│  │                                                                              │   │
│  │  主流程:                                                                     │   │
│  │  1. validateRequest()      → 参数校验                                        │   │
│  │  2. checkRateLimit()       → 限流检查 (Redis)                                │   │
│  │  3. findProduct()          → 产品查询 (PostgreSQL)                           │   │
│  │  4. loadDevice()           → 设备加载 (Redis Cache → PostgreSQL)             │   │
│  │  5. markDeviceActive()     → 活跃标记 (Redis Bitmap)                         │   │
│  │  6. findVersionId()        → 版本查找 (Redis Cache → PostgreSQL)             │   │
│  │  7. findApplicablePolicies() → 策略匹配                                      │   │
│  │  8. buildResponse()        → 构建响应 (UpgradeResponseBuilder)               │   │
│  │                                                                              │   │
│  │  finally (异步):                                                             │   │
│  │  - recordCheckLog()            → 发送检查日志 MQ                              │   │
│  │  - checkAndSendDeviceInfoUpdate() → 发送设备信息更新 MQ                       │   │
│  └─────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────┘
                    │                                           │
                    ▼                                           ▼
┌───────────────────────────────┐           ┌───────────────────────────────────────┐
│         Domain Layer          │           │            Infrastructure Layer        │
│                               │           │                                        │
│  - Device                     │           │  Repository (PostgreSQL):              │
│  - Product                    │           │  - DeviceRepository                    │
│  - UpgradePolicy              │           │  - ProductRepository                   │
│  - FirmwareVersion            │           │  - UpgradePolicyRepository             │
│  - DeviceCheckLog             │           │  - FirmwareVersionRepository           │
│  - DeviceInfoUpdateMessage    │           │                                        │
│                               │           │  Cache (Redis):                        │
│  Gateway (Domain Port):       │           │  - DeviceCacheRepository               │
│  - CheckLogGateway            │           │  - DeviceRateLimiter                   │
│  - DeviceInfoUpdateGateway    │           │  - DeviceActivityBitmapRepository      │
│                               │           │                                        │
│                               │           │  MQ Gateway:                           │
│                               │           │  - RabbitMqCheckLogGateway             │
│                               │           │  - RabbitMqDeviceInfoUpdateGateway     │
└───────────────────────────────┘           └───────────────────────────────────────┘
```

---

## 核心组件

### 1. CheckContext (上下文封装)

封装整个检查流程的中间状态，避免零散参数传递。

```java
@Data
@Builder
public class CheckContext {
    // 入参
    private UpgradeCheckReqDTO request;
    private CheckLogContext logContext;

    // 中间参数
    private String requestId;
    private Product product;
    private Device device;
    private Long versionId;
    private UpgradePolicy matchedPolicy;
    private RateLimitDecision rateLimitDecision;

    // 结果
    private CheckResult result;

    // 便捷方法
    public String imei() { return request.getImei(); }
    public Long productId() { return product.getId(); }
    public boolean hasDevice() { return device != null; }
    public boolean hasResult() { return result != null; }
}
```

### 2. UpgradeCheckService (核心编排服务)

负责协调各领域组件，执行检查流程。

**依赖组件**:
| 组件 | 职责 |
|------|------|
| `DeviceRateLimiter` | 限流检查 (10次/分钟) |
| `DeviceCacheRepository` | 设备缓存读写 |
| `DeviceActivityBitmapRepository` | 设备活跃度 Bitmap |
| `FirmwareVersionLookupService` | 固件版本查找 |
| `PolicyMatcher` | 策略匹配器 |
| `GrayReleaseService` | 灰度发布服务 |
| `CheckLogGateway` | 检查日志网关 |
| `DeviceInfoUpdateGateway` | 设备信息更新网关 |

### 3. 策略匹配过滤器链

```
findApplicablePolicies()
    │
    ├─→ matchesDevMode()         → 测试设备/生产设备区分
    │
    ├─→ matchesTriggerMode()     → 手动/自动触发匹配
    │
    ├─→ matchesSourceVersion()   → 源版本范围匹配
    │
    ├─→ matchesTargetMode()      → 目标设备匹配 (imei/batchId/tags)
    │
    ├─→ matchesTimeWindow()      → 时间窗口匹配
    │
    └─→ matchesGrayRelease()     → 灰度发布匹配 (MurmurHash3)
```

---

## 数据流时序

```
设备请求
    │
    ├─→ [1] Controller 接收请求，提取 clientIp/userAgent/region
    │
    ├─→ [2] Service 参数校验
    │
    ├─→ [3] Redis 限流检查 (10次/分钟)
    │       └─→ 限流 → 返回 code=2
    │
    ├─→ [4] PostgreSQL 查询产品 (通过型号)
    │       └─→ 不存在 → 返回 code=4
    │
    ├─→ [5] Redis 缓存查询设备
    │       ├─→ 缓存命中 → 返回设备信息
    │       └─→ 缓存未命中 → PostgreSQL 查询 → 写入缓存
    │              └─→ 设备不存在 → 返回 code=3
    │
    ├─→ [6] Redis Bitmap 标记设备活跃 (异步,可失败)
    │
    ├─→ [7] Redis 缓存查询固件版本 ID
    │       └─→ 缓存未命中 → PostgreSQL 查询 → 写入缓存
    │
    ├─→ [8] PostgreSQL 查询策略列表
    │       └─→ 6重过滤匹配
    │              ├─→ devMode 匹配
    │              ├─→ triggerMode 匹配
    │              ├─→ sourceVersion 匹配
    │              ├─→ targetMode 匹配 (imei/batchId/tags)
    │              ├─→ timeWindow 匹配
    │              └─→ grayRelease 匹配 (MurmurHash3)
    │              └─→ 无匹配策略 → 返回 code=1
    │
    ├─→ [9] 构建响应 (固件信息、下载URL、checksum等)
    │
    ├─→ [10] 返回响应给设备
    │
    └─→ [11] finally (异步,不阻塞):
            ├─→ RabbitMQ 发送 CheckLog 消息
            │       └─→ Consumer 批量写入 ClickHouse
            │
            └─→ RabbitMQ 发送 DeviceInfoUpdate 消息
                    └─→ Consumer 批量更新 PostgreSQL (单条SQL)
                            └─→ 刷新 Redis 缓存
```

---

## MQ 异步处理

### 队列配置

| 队列 | 批量大小 | 等待时间 | TTL | 目标存储 |
|------|----------|----------|-----|----------|
| `fota.check.logs` | 100 | 5s | 7天 | ClickHouse |
| `fota.device.info.update` | 500 | 30s | 24小时 | PostgreSQL |

### 检查日志 (CheckLog)

```
Exchange: fota.check.logs.exchange
Queue: fota.check.logs
Consumer: CheckLogConsumer
    │
    └─→ DeviceUpgradeEventAppService.recordCheckLogs()
            └─→ ClickHouse: device_check_logs
```

### 设备信息更新 (DeviceInfoUpdate)

```
Exchange: fota.device.info.update.exchange
Queue: fota.device.info.update
Consumer: DeviceInfoUpdateConsumer
    │
    └─→ DeviceInfoUpdateAppService.processBatch()
            ├─→ PostgreSQL: devices (单条SQL批量更新)
            └─→ Redis: 刷新设备缓存
```

**消息格式**:
```json
{
  "messageId": "uuid-v4",
  "timestamp": "2026-03-07T10:00:00",
  "correlationId": "check-request-id",
  "deviceId": 12345,
  "imei": "865123456789012",
  "productId": 1,
  "updateReason": "FIRST_ONLINE",
  "isFirstOnline": true,
  "newVersion": "1.0.0",
  "newVersionId": 101,
  "partName": "main",
  "accessTime": "2026-03-07T10:00:00"
}
```

**更新原因枚举**:
| 原因 | 说明 |
|------|------|
| `FIRST_ONLINE` | 设备首次上线，初始化 firstSeenAt, initialVersionParts |
| `VERSION_CHANGED` | 版本变化，更新 versionParts |
| `ACCESS_TIME_UPDATE` | 仅更新访问时间 lastSeenAt |

---

## 批量更新 SQL

设备信息更新使用单条 SQL 批量更新（PostgreSQL 特有语法）：

```sql
UPDATE devices d
SET 
    first_seen_at = COALESCE(v.first_seen_at, d.first_seen_at),
    last_seen_at = v.last_seen_at,
    version_parts = COALESCE(v.version_parts::jsonb, d.version_parts),
    initial_version_parts = COALESCE(v.initial_version_parts::jsonb, d.initial_version_parts),
    updated_at = NOW()
FROM (VALUES
    (1, '2026-03-07 10:00:00', '2026-03-07 10:00:00', '{"main":{...}}'::jsonb, NULL),
    (2, NULL, '2026-03-07 10:00:00', '{"main":{...}}'::jsonb, NULL),
    (3, NULL, '2026-03-07 10:00:00', '{"main":{...}}'::jsonb, NULL)
    -- ... 500 条
) AS v(id, first_seen_at, last_seen_at, version_parts, initial_version_parts)
WHERE d.id = v.id
```

**性能对比**:
| 方案 | 500条消息 | 网络往返 |
|------|----------|----------|
| 逐条 updateById | 500 次 SQL | 500 次 |
| JdbcTemplate batchUpdate | 500 次 SQL (打包) | 1 次 |
| **单条 SQL VALUES** | **1 次 SQL** | **1 次** |

---

## 性能指标

### 目标指标

| 环节 | 耗时目标 | 存储 |
|------|----------|------|
| 限流检查 | < 1ms | Redis |
| 产品查询 | < 5ms | PostgreSQL |
| 设备加载 | < 5ms (缓存命中) / < 20ms (缓存未命中) | Redis + PostgreSQL |
| 活跃标记 | < 1ms (异步) | Redis Bitmap |
| 版本查找 | < 5ms (缓存命中) | Redis + PostgreSQL |
| 策略匹配 | < 10ms | PostgreSQL |
| **总耗时** | **< 50ms (99%)** | - |

### 缓存策略

| 数据 | 缓存位置 | 过期时间 | 说明 |
|------|----------|----------|------|
| 设备信息 | Redis | 24小时 | 负缓存 5 分钟 |
| 固件版本 ID | Redis | 24小时 | 负缓存 5 分钟 |
| 设备活跃度 | Redis Bitmap | 按日分离 | 30 天保留 |

---

## 错误处理

### 响应码映射

| code | 枚举 | 场景 |
|------|------|------|
| 0 | UPDATE | 匹配到升级策略 |
| 1 | NO_UPDATE | 无匹配策略 |
| 2 | RATE_LIMITED | 请求被限流 |
| 3 | DEVICE_NOT_FOUND | 设备未注册 |
| 4 | ERROR | 处理异常 |

### 降级策略

| 场景 | 降级方案 |
|------|----------|
| Redis 不可用 | 限流降级（允许通过），缓存穿透到数据库 |
| ClickHouse 写入失败 | MQ 重试，进入死信队列 |
| PostgreSQL 更新失败 | MQ 重试，进入死信队列 |

---

## 关键类索引

| 类 | 路径 | 职责 |
|------|------|------|
| `UpgradeCheckController` | `adapter/api/device/` | API 入口 |
| `UpgradeCheckService` | `application/upgrade/` | 核心编排 |
| `CheckContext` | `application/upgrade/dto/` | 上下文封装 |
| `CheckResult` | `application/upgrade/dto/` | 检查结果 |
| `PolicyMatcher` | `application/upgrade/` | 策略匹配器 |
| `GrayReleaseService` | `application/upgrade/` | 灰度发布 |
| `UpgradeResponseBuilder` | `application/upgrade/` | 响应构建 |
| `CheckLogConsumer` | `infra/mq/` | 检查日志消费者 |
| `DeviceInfoUpdateConsumer` | `infra/mq/` | 设备信息更新消费者 |
| `DeviceInfoUpdateAppService` | `application/device/` | 批量更新服务 |

---

## 变更日志

### 2026-03-07 (v1.0)
- 初版：定义升级检查流程架构
- 包含分层设计、数据流、性能指标
- MQ 异步处理配置
- 批量更新 SQL 设计
