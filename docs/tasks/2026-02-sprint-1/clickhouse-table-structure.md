# ClickHouse 表结构设计文档（最终简化版）

**Sprint**: 2026-02 Sprint-1
**完成日期**: 2026-02-11
**实施人员**: Claude Code

---

## 概述

设计并实现了 ClickHouse 表结构来存储设备升级相关的日志数据，支持实时查询和统计分析。该设计采用**Redis 缓存简化**方案，缓存未命中时保持字段为空，不做异步回填。

---

## 设计原则

1. **高性能写入**：通过 Redis 缓存避免每次上报查库
2. **简化优先**：缓存未命中时保持为空，不做复杂回填逻辑
3. **链路追踪**：通过 `request_id` 关联检查日志和升级事件
4. **自动清理**：使用 TTL 自动删除 30 天前的数据
5. **类型安全**：使用 Enum8 存储事件类型
6. **分区优化**：按月分区，便于管理和查询
7. **UTC 时区**：统一使用 UTC 时区，避免时区混乱
8. **统一 Key**：Redis Key 使用 `fota:` 前缀，按功能模块细分

---

## 表结构设计

### 1. device_check_logs（设备检查日志表）

**用途**：记录设备调用 `/v1/upgrade/check` 接口的日志

#### 字段说明

| 字段名 | 类型 | 说明 |
|---------|------|------|
| event_time | DateTime64(3, 'UTC') | 事件时间（服务端时间） |
| date | Date (MATERIALIZED) | 日期（自动计算） |
| month | UInt32 (MATERIALIZED) | 月份（自动计算，用于分区） |
| device_id | UInt64 | 设备 ID |
| imei | String | 设备 IMEI（防止前导 0 丢失） |
| product_id | UInt64 | 产品 ID |
| current_version | String | 当前固件版本 |
| check_mode | LowCardinality(String) | 检查模式（auto/manual） |
| language | LowCardinality(String) | 语言设置 |
| device_tags | String | 设备标签（JSON） |
| ext_tags | String | 扩展标签（JSON） |
| is_dev | UInt8 | 是否为开发设备 |
| has_update | UInt8 | 是否有更新 |
| target_version | Nullable(String) | 目标固件版本 |
| target_version_id | Nullable(UInt64) | 目标版本 ID |
| policy_id | Nullable(UInt64) | 策略 ID |
| gray_bucket | Nullable(UInt8) | 灰度桶号（0-99） |
| is_gray_hit | Nullable(UInt8) | 是否命中灰度 |
| decision | Nullable(String) | 检查决策结果 |
| response_check_interval | Nullable(UInt32) | 建议下次检查间隔（秒） |
| download_delay | Nullable(UInt32) | 建议下载延迟（秒） |
| request_id | Nullable(UUID) | 请求唯一标识 |
| client_ip | Nullable(IPv4) | 客户端 IP |
| user_agent | Nullable(String) | 用户代理 |
| region | LowCardinality(String) | 区域标识 |
| error_code | Nullable(String) | 错误码 |
| error_message | Nullable(String) | 错误信息 |

#### 存储配置

```sql
ENGINE = MergeTree()
PARTITION BY month
ORDER BY (product_id, device_id, event_time)
TTL event_time + toIntervalDay(30)
SETTINGS index_granularity = 8192;
```

---

### 2. device_upgrade_events（设备升级事件表）

**用途**：记录设备上报的升级进度和结果事件（从 `/v1/upgrade/report` 接口）

#### 字段说明

| 字段名 | 类型 | 说明 |
|---------|------|------|
| event_time | DateTime64(3, 'UTC') | **服务端时间**（权威） |
| date | Date (MATERIALIZED) | 日期（自动计算） |
| month | UInt32 (MATERIALIZED) | 月份（自动计算，用于分区） |
| event_id | UUID | 事件唯一标识（幂等写入） |
| imei | String | 设备 IMEI（从上报获取） |
| request_id | Nullable(UUID) | 关联检查日志的 request_id（从 URL 解析） |
| policy_id | Nullable(UInt64) | 策略 ID（从 URL 解析） |
| event_type | Enum8 | 事件类型（DL_START/DL_OK/DL_FAIL/UP_OK） |
| download_url | String | 下载 URL（从上报获取） |
| details | String | 原始上报详情（JSON，含设备时间、进度、错误等） |
| device_id | Nullable(UInt64) | **设备 ID**（从 Redis 缓存补全，可为空） |
| product_id | Nullable(UInt64) | **产品 ID**（从 Redis 缓存补全，可为空） |
| firmware_version | Nullable(String) | **固件版本**（从 Redis 缓存补全，可为空） |
| client_ip | Nullable(IPv4) | 客户端 IP |
| region | LowCardinality(String) | 区域标识 |

#### 事件类型枚举

| 枚举值 | 说明 |
|---------|------|
| DL_START | 开始下载固件 |
| DL_OK | 下载完成 |
| DL_FAIL | 下载失败 |
| UP_OK | 升级完成 |

#### 存储配置

```sql
ENGINE = MergeTree()
PARTITION BY month
ORDER BY (request_id, event_time)
TTL event_time + toIntervalDay(30)
SETTINGS index_granularity = 8192;
```

---

## Redis 缓存设计

### Key 命名规范

统一使用 `fota:` 前缀，按功能模块细分：

| 模块 | Key 格式 | 示例 |
|------|----------|------|
| 设备信息 | `fota:device:{imei}` | `fota:device:861234567890123` |
| 策略缓存 | `fota:policy:{id}` | `fota:policy:101` |
| 产品信息 | `fota:product:{id}` | `fota:product:1001` |
| 配置快照 | `fota:config:{region}` | `fota:config:cn` |

### 缓存数据结构

```java
DeviceCache {
  Long deviceId;        // 设备 ID
  Long productId;       // 产品 ID
  String fwVersion;     // 当前固件版本
  Long policyId;        // 当前策略 ID（可选）
  LocalDateTime cachedAt;  // 缓存时间
}
```

### 缓存策略

1. **写入路径**：
   - 从 Redis 获取设备信息
   - 缓存命中：直接补全字段
   - 缓存未命中：字段为 null（不做回填）

2. **TTL**：24 小时

3. **预热**：系统启动时批量加载活跃设备

---

## 上报处理流程

### 1. 设备上报数据

```json
{
  "imei": 861234567890123,
  "url": "http://foid-dl.xxx.com/xxx.bin?pid=101&rid=uuid-xxx",
  "event": "DL_START",
  "details": {
    "device_time": "2026-02-11T10:00:00Z",
    "progress": 0
  }
}
```

### 2. 服务端处理流程

```
接收上报
   ↓
解析 URL 提取 request_id 和 policy_id
   ↓
从 Redis 获取设备信息 (fota:device:{imei})
   ↓
缓存命中？
   ├─ 是 → 补全 deviceId, productId, firmwareVersion
   └─ 否 → 字段保持为 null
   ↓
立即写入 ClickHouse（不阻塞）
```

### 3. 数据最终状态

- **有数据**：缓存命中，冗余字段完整
- **无数据**：缓存未命中，冗余字段为 null

---

## 已创建的文件

### 枚举类

```
fota-service/src/main/java/com/wewins/fota/clickhouse/enums/DeviceUpgradeEventType.java
```

### 实体类

```
fota-service/src/main/java/com/wewins/fota/clickhouse/entity/DeviceCheckLog.java
fota-service/src/main/java/com/wewins/fota/clickhouse/entity/DeviceUpgradeEvent.java
```

### 缓存相关

```
fota-service/src/main/java/com/wewins/fota/cache/DeviceCache.java
fota-service/src/main/java/com/wewins/fota/cache/DeviceCacheService.java
```

### Mapper 接口

```
fota-service/src/main/java/com/wewins/fota/clickhouse/mapper/DeviceCheckLogMapper.java
fota-service/src/main/java/com/wewins/fota/clickhouse/mapper/DeviceUpgradeEventMapper.java
fota-service/src/main/java/com/wewins/fota/mapper/DeviceMapper.java (新增 selectByImei 方法)
```

### Mapper XML

```
fota-service/src/main/resources/mapper/clickhouse/DeviceCheckLogMapper.xml
fota-service/src/main/resources/mapper/clickhouse/DeviceUpgradeEventMapper.xml
```

### DDL 脚本

```
fota-service/src/main/resources/db/clickhouse/01_create_tables.sql
```

### Service 层

```
fota-service/src/main/java/com/wewins/fota/clickhouse/service/DeviceUpgradeEventService.java
fota-service/src/main/java/com/wewins/fota/clickhouse/service/impl/DeviceUpgradeEventServiceImpl.java
```

### MQ 消费者

```
fota-service/src/main/java/com/wewins/fota/mq/consumer/UpgradeEventConsumer.java
```

---

## 使用示例

### 1. 查询某次检查的所有后续事件

```sql
SELECT *
FROM device_upgrade_events
WHERE request_id = 'uuid-xxx-xxx'
ORDER BY event_time;
```

### 2. 查询某个策略的执行情况

```sql
SELECT
    date,
    count() AS total_checks,
    countIf(has_update) AS devices_with_update,
    countIf(event_type = 'DL_OK') AS downloads_ok,
    countIf(event_type = 'UP_OK') AS upgrades_ok
FROM device_check_logs
LEFT JOIN device_upgrade_events USING (request_id)
WHERE policy_id = 101
GROUP BY date;
```

### 3. 查询缓存命中率

```sql
-- 查询有多少事件补全了设备信息
SELECT
    date,
    count() AS total_events,
    countIf(device_id IS NOT NULL) AS with_device_id,
    with_device_id * 100.0 / total_events AS percentage
FROM device_upgrade_events
WHERE event_time >= now() - INTERVAL 7 DAY
GROUP BY date
ORDER BY date;
```

---

## 部署说明

### 1. 创建 ClickHouse 表

```bash
# 连接到 ClickHouse
clickhouse-client --host localhost --port 9000

# 单机环境：移除 ON CLUSTER 子句
# 或修改 DDL 中的 {cluster} 为实际集群名

# 执行 DDL 脚本
source fota-service/src/main/resources/db/clickhouse/01_create_tables.sql
```

### 2. 配置 RabbitMQ 队列

```yaml
# application.yml
app:
  mq:
    enabled: true
    upgrade-event-queue: fota.upgrade.events
```

### 3. 配置 Redis 和 ClickHouse

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
  datasource:
    clickhouse:
      url: jdbc:clickhouse://localhost:8123/fota
      driver-class-name: com.clickhouse.jdbc.ClickHouseDriver
```

---

## 注意事项

### 1. ON CLUSTER 配置

DDL 脚本使用 `ON CLUSTER '{cluster}'`，需要根据实际环境调整：

- **集群环境**：替换 `{cluster}` 为实际集群名
- **单机环境**：移除 `ON CLUSTER '{cluster}'` 子句

### 2. 时区处理

- `event_time` 使用服务端时间（权威），用于排序和 SLA 统计
- 设备上报时间放在 `details` JSON 中（仅供参考）

### 3. 字段为空处理

- `device_id`, `product_id`, `firmware_version` 可能为空
- 查询时需要使用 `LEFT JOIN` 或 `countIf(device_id IS NOT NULL)`

### 4. 数据质量监控

建议监控以下指标：

- 缓存命中率（device_id 不为空的比例）
- `request_id` 为空的升级事件比例
- `event_type` 分布情况
- 分区数据量和 TTL 清理情况

---

## 后续优化建议

1. **物化视图**：为常用查询创建物化视图
2. **分布式表**：集群环境创建分布式表
3. **排序键优化**：根据实际查询模式调整 `ORDER BY`
4. **缓存预热**：系统启动时加载活跃设备到 Redis

---

## 相关文档

- [ClickHouse 官方文档](https://clickhouse.com/docs/)
- [项目架构文档](/docs/FOTA%20系统架构及技术说明书.md)
