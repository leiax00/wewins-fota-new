# Redis 缓存键设计方案

> **版本**: v3.0  
> **更新日期**: 2026-03-04  
> **变更**: 重新组织文档结构，添加总览表格，按类型分章节详细说明  
> **代码参考**: `fota-framework-cache/src/main/java/com/wewins/fota/cache/constant/RedisKeyConstants.java`

---

## 📋 目录

1. [设计概述](#1-设计概述)
2. [Key 命名规范](#2-key-命名规范)
3. [缓存总览表](#3-缓存总览表)
4. [缓存模式最佳实践](#4-缓存模式最佳实践)
5. [设备缓存](#5-设备缓存)
6. [策略缓存](#6-策略缓存)
7. [产品缓存](#7-产品缓存)
8. [固件缓存](#8-固件缓存)
9. [缓存索引设计](#9-缓存索引设计)
10. [策略快照缓存](#10-策略快照缓存)
11. [活跃度 Bitmap](#11-活跃度-bitmap)
12. [其他缓存](#12-其他缓存)
13. [批量失效策略](#13-批量失效策略)
14. [性能考虑](#14-性能考虑)
15. [监控与维护](#15-监控与维护)

---

## 1. 设计概述

本设计方案定义了 FOTA 系统的 Redis 缓存键结构，包括设备、策略、产品、固件等核心业务对象。设计遵循以下原则：

- **统一规范**：使用 `fota:{module}:{key}` 命名规范
- **Keys Pattern**：列表缓存采用 ID 列表 + 单独对象的模式（更新范围减少 95%）
- **分层 TTL**：不同数据类型采用不同的 TTL 和续期策略（24h/7d/30d）
- **性能优先**：选择合适的数据结构，优化读写性能
- **可维护性**：清晰的结构和完整的示例

### 核心设计决策

1. **列表缓存采用 Keys Pattern**（详见第 4 章）
   - 列表缓存只存 ID 集合（Set 类型）
   - 单个对象单独缓存（String JSON）
   - 读取流程：SMEMBERS + MGET

2. **设备缓存精简设计**
   - 保留 5 个核心字段（deviceId、productId、currentVersionId、tags、importBatchId）
   - 内存占用：~500 bytes/设备（1000万设备 ≈ 5GB）

3. **命名简化**
- 去掉 `ids` 层级（`fota:cache:list:product:policy:{productId}:{type}`）
   - Redis Type 本身能区分（Set = ID 列表，String = 完整对象）

详细设计决策参考：`docs/02-architecture/cache-design-decisions.md`

---

## 2. Key 命名规范

### 2.1 基本格式
```
fota:{module}:{key}
```

### 2.2 命名层级

| 层级 | 说明 | 示例 |
|------|------|------|
| `fota` | 项目前缀（固定） | `fota` |
| `{module}` | 模块名 | `device`, `policy`, `product`, `firmware`, `cache` |
| `{key}` | 功能键 | `{imei}`, `{policyId}`, `list:{productId}` |
| `{identifier}` | 标识符 | IMEI、ID、日期等 |

### 2.3 命名规范

| 规范 | 说明 | 示例 |
|------|------|------|
| **统一小写** | 所有 key 使用小写字母 | ✅ `fota:device:123` ❌ `fota:Device:123` |
| **冒号分隔** | `:` 作为层级分隔符 | ✅ `fota:cache:list:product:policy:1001:all` |
| **避免特殊字符** | 不使用空格、特殊符号 | ✅ `fota:policy:101` ❌ `fota:policy-101` |
| **长度控制** | Key 长度不超过 256 字节 | Redis 限制 |
| **语义化** | Key 名称应清晰表达用途 | ✅ `fota:cache:index:product:1001` |

### 2.4 命名示例

```redis
# 单个对象缓存（Type: String）
fota:device:861234567890123              # 设备信息
fota:policy:101                          # 策略详情
fota:product:1001                        # 产品信息
fota:firmware:201                        # 固件详情

# 列表缓存（Type: Set）
fota:cache:list:product:policy:1001:all  # 产品策略列表（ID集合）

# 缓存索引（Type: Set）
fota:cache:index:product:1001            # 产品缓存索引
fota:cache:index:policy:101              # 策略缓存索引

# 策略快照
fota:pol:snap:main:v123                  # 策略快照数据
fota:pol:active_ver:main                 # 活跃版本指针

# 活跃度 Bitmap
fota:active:20260304                     # 设备活跃度（按日期）

# 其他
fota:config:cn                           # 配置信息
fota:lock:device_import                  # 分布式锁
fota:ratelimit:check:123:202603041030    # 限流
```

---

## 3. 缓存总览表


### 3.1 核心缓存 Key 总表

| Key 模板 | 类型 | 说明 | TTL | 续期策略 | 实现类 | 常量名 |
|---------|------|------|-----|---------|--------|--------|
| **设备缓存** |
| `fota:device:{imei}` | String (JSON) | 设备信息（5字段） | 24h | ❌ 固定 TTL | `RedisDeviceCacheRepository` | `DEVICE_KEY_TEMPLATE` |
| **策略缓存** |
| `fota:policy:{policyId}` | String (JSON) | 策略详情 | 24h | ✅ 滑动续期 | `RedisPolicyCacheRepository` | `POLICY_KEY_TEMPLATE` |
| `fota:cache:list:product:policy:{productId}:{type}` | Set | 策略列表（ID集合） | 24h | ✅ 滑动续期 | `RedisPolicyCacheRepository` | `PRODUCT_POLICY_LIST_KEY_TEMPLATE` |
| `fota:cache:index:policy:{policyId}` | Set | 策略缓存索引 | 24h | ✅ 滑动续期 | `RedisPolicyCacheRepository` | `POLICY_CACHE_INDEX_KEY_TEMPLATE` |
| **产品缓存** |
| `fota:product:{productId}` | String (JSON) | 产品信息 | 7d | ✅ 滑动续期 | `RedisProductCacheRepository` | `PRODUCT_KEY_TEMPLATE` |
| `fota:cache:product:model:{model}` | String | 产品型号索引 | 7d | ✅ 滑动续期 | `RedisProductCacheRepository` | `PRODUCT_MODEL_INDEX_KEY_TEMPLATE` |
| `fota:cache:index:product:{productId}` | Set | 产品缓存索引 | 24h | ✅ 滑动续期 | `RedisProductCacheRepository` | `PRODUCT_CACHE_INDEX_KEY_TEMPLATE` |
| **固件缓存** |
| `fota:firmware:{versionId}` | String (JSON) | 固件详情 | 30d | ✅ 滑动续期 | `RedisFirmwareCacheRepository` | `FIRMWARE_KEY_TEMPLATE` |
| `fota:cache:firmware:lookup:{productId}:{version}:{internalVersion}` | String | 版本映射（查 versionId） | 24h / 60s(NF) | ✅ 正缓存续期，❌ 负缓存不续期 | `RedisFirmwareVersionLookupCacheRepository` | `FIRMWARE_LOOKUP_KEY_TEMPLATE` |
| **策略快照** |
| `fota:pol:snap:{scope}:{version}` | Hash | 策略快照数据 | 7d | ✅ 滑动续期 | `RedisPolicySnapshotRepository` | `POLICY_SNAPSHOT_KEY_TEMPLATE` |
| `fota:pol:active_ver:{scope}` | String | 活跃版本指针 | 7d | ✅ 滑动续期 | `RedisPolicySnapshotRepository` | `POLICY_ACTIVE_VER_KEY_TEMPLATE` |
| `fota:pol:sync_ts:{productId}` | String | 同步时间戳 | 30d | ✅ 滑动续期 | `RedisPolicySnapshotRepository` | `POLICY_SYNC_TS_KEY_TEMPLATE` |
| `fota:pol:write_lock:{productId}` | String | 写入锁 | 30s | ❌ 固定 TTL | `RedisPolicySnapshotRepository` | `POLICY_WRITE_LOCK_KEY_TEMPLATE` |
| **活跃度 Bitmap** |
| `fota:active:{date}` | String (Bitmap) | 设备活跃度 | 90d | ❌ 固定 TTL | `RedisDeviceActivityBitmapRepository` | `ACTIVE_BITMAP_KEY_TEMPLATE` |
| `fota:tmp:bitop:{operation}:{identifier}` | String | BITOP 临时键 | 60s | ❌ 固定 TTL | `RedisDeviceActivityBitmapRepository` | `BITOP_TEMP_KEY_TEMPLATE` |
| **其他缓存** |
| `fota:config:{region}` | String (JSON) | 配置信息 | 6h | ✅ 滑动续期 | - | `CONFIG_KEY_TEMPLATE` |
| `fota:lock:{lockName}` | String | 分布式锁 | 30s | ❌ 固定 TTL | - | `LOCK_KEY_TEMPLATE` |
| `fota:ratelimit:{limitName}:{limitKey}` | String | 限流计数 | 60s | ❌ 固定 TTL | `RedisDeviceRateLimiter` | `RATE_LIMIT_KEY_TEMPLATE` |
| `fota:fw:upload:sess:{sessionId}` | String (JSON) | 固件上传会话 | 2h | ❌ 固定 TTL | - | `FIRMWARE_UPLOAD_SESSION_KEY_TEMPLATE` |
| `fota:fw:upload:lock:{productId}:{version}` | String | 固件上传锁 | 30s | ❌ 固定 TTL | - | `FIRMWARE_UPLOAD_LOCK_KEY_TEMPLATE` |
| `fota:device:import:sess:{sessionId}` | String (JSON) | 设备导入会话 | 2h | ❌ 固定 TTL | - | `DEVICE_IMPORT_SESSION_KEY_TEMPLATE` |

### 3.2 TTL 策略说明

| TTL 策略 | 适用场景 | 实现方式 | 示例 |
|---------|---------|---------|------|
| **固定 TTL（不续期）** | 沉默期释放内存、短生命周期数据 | 缓存创建时设置 TTL，不续期 | 设备缓存、Bitmap、锁、限流 |
| **滑动 TTL（续期）** | 热点数据持续缓存 | 每次读取时续期 + 10% 随机偏移 | 策略、产品、固件、配置信息 |

**续期实现**：
```java
// 滑动 TTL 续期示例（RedisPolicyCacheRepository.java:117-124）
private void renewTtl(String key) {
    try {
        long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.POLICY_CACHE_TTL_SECONDS);
        redisTemplate.expire(key, Duration.ofSeconds(ttl));
    } catch (Exception e) {
        log.warn("续期策略缓存 TTL 失败: key={}", key, e);
    }
}
```

### 3.3 存储类型分布

| 存储类型 | 使用场景 | Key 数量 | 示例 |
|---------|---------|---------|------|
| **String (JSON/String)** | 单个对象与映射缓存 | 13+ | 设备、策略、产品、固件、版本映射 |
| **String (Bitmap)** | 设备活跃度跟踪 | 2 | 活跃度 Bitmap、BITOP 临时键 |
| **Set** | ID 列表、缓存索引 | 8+ | 策略列表、缓存索引 |
| **Hash** | 策略快照数据 | 1 | 策略快照 |

---

## 4. 缓存模式最佳实践

### 4.1 Keys Pattern（推荐用于列表缓存）

**适用场景**：产品策略列表

**存储结构**：
```redis
# 列表缓存（只存 ID）
fota:cache:list:product:policy:{productId}:{type}    Type: Set

# 单个对象缓存
fota:policy:{policyId}                               Type: String (JSON)
fota:firmware:{versionId}                            Type: String (JSON)
```

**读取流程**：
```java
String idsKey = String.format(PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, type);
Set<Object> policyIds = redisTemplate.opsForSet().members(idsKey);

if (policyIds == null || policyIds.isEmpty()) {
    return loadFromDatabase(productId, type);
}

List<String> policyKeys = policyIds.stream()
    .map(id -> String.format(POLICY_KEY_TEMPLATE, id))
    .collect(Collectors.toList());

List<Object> policies = redisTemplate.opsForValue().multiGet(policyKeys);
```

### 4.2 Embedded Pattern（不推荐用于列表）

**问题**：
- 更新任何对象需要重写整个列表
- 并发修改存在丢失更新风险
- 仅适用于小型列表（<10项）且几乎不变更的静态数据

---

## 5. 设备缓存

### 5.1 DeviceCache 字段定义

**文件位置**：`fota-service/src/main/java/com/wewins/fota/domain/device/cache/DeviceCache.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCache implements Serializable {

    private static final long serialVersionUID = 1L;

    // 核心字段
    private Long deviceId;              // 设备 ID
    private Long productId;             // 产品 ID
    private Long currentVersionId;      // 当前版本 ID
    
    // 策略匹配字段
    private JsonNode tags;              // 设备标签
    private Long importBatchId;         // 批量导入 ID
    
    // 缓存元数据（可选）
    private LocalDateTime cachedAt;     // 缓存时间戳
}
```

### 5.2 字段用途说明

| 字段 | 使用场景 | 代码位置 | 必需性 |
|------|---------|---------|--------|
| `deviceId` | 标记设备活跃 | `UpgradeCheckService.java:182` | ✅ 必需 |
| `productId` | 产品查询 + 策略匹配 | `UpgradeCheckService.java:195` | ✅ 必需 |
| `currentVersionId` | 版本匹配 | `UpgradeCheckService.loadDevice()` | ✅ 必需 |
| `tags` | 测试设备判断 + 策略标签匹配 | `UpgradeCheckService.java:351, 225` | ✅ 必需 |
| `importBatchId` | 批量导入标签匹配 | `UpgradeCheckService.java:222, 225` | ✅ 必需 |
| `cachedAt` | 缓存时间戳 | 监控用 | ⚠️ 可选 |

### 5.3 缓存读写实现

**实现类**：`RedisDeviceCacheRepository.java`

**Key 示例**：
```redis
fota:device:861234567890123  →  {
  "deviceId": 123,
  "productId": 1001,
  "currentVersionId": 201,
  "tags": {"env": "test", "region": "CN"},
  "importBatchId": 5,
  "cachedAt": "2026-03-04T10:30:00"
}
```

**读取流程**：
```java
// UpgradeCheckService.java:245-269
private Device loadDevice(String imei) {
    // 1. 从缓存读取
    DeviceCache cached = deviceCacheService.get(imei);
    if (cached != null) {
        log.debug("设备缓存命中: imei={}, deviceId={}", imei, cached.getDeviceId());
        
        // 构建完整 Device 对象
        Device device = new Device();
        device.setId(cached.getDeviceId());
        device.setImei(imei);
        device.setProductId(cached.getProductId());
        device.setCurrentVersionId(cached.getCurrentVersionId());
        device.setTags(cached.getTags());              // ✅ 必需
        device.setImportBatchId(cached.getImportBatchId());  // ✅ 必需
        return device;
    }

    // 2. 缓存未命中，从数据库加载
    Device device = deviceRepository.findByImei(imei).orElse(null);
    if (device != null) {
        // 构建缓存对象
        DeviceCache cache = DeviceCache.builder()
                .deviceId(device.getId())
                .productId(device.getProductId())
                .currentVersionId(device.getCurrentVersionId())
                .tags(device.getTags())                // ✅ 必需
                .importBatchId(device.getImportBatchId())  // ✅ 必需
                .cachedAt(LocalDateTime.now())         // 可选
                .build();
        deviceCacheService.put(imei, cache);
        log.debug("设备缓存已写入: imei={}, deviceId={}", imei, device.getId());
    }

    return device;
}
```

### 5.4 内存估算

| 方案 | 字段数 | 单个对象大小 | 1000万设备总内存 |
|------|-------|------------|------------------|
| **当前（3字段）** | deviceId, productId, currentVersionId | ~150 bytes | ~1.5 GB |
| **优化后（5字段）** | + tags, importBatchId | ~500 bytes | ~5 GB |
| **完整 Device 对象** | 12+ 字段 | ~1 KB | ~10 GB |

**结论**：精简 DeviceCache 方案内存占用可接受（5GB），同时保证性能。

### 5.5 失效策略

**实现类**：`RedisDeviceCacheRepository.java:87-92`

```java
@Override
public void evict(String imei) {
    try {
        String key = buildDeviceKey(imei);
        redisTemplate.delete(key);
        log.debug("设备缓存已删除: imei={}", imei);
    } catch (Exception e) {
        log.error("删除 Redis 设备缓存失败: imei={}", imei, e);
    }
}
```

**触发场景**：
- 设备信息更新（通过 `DeviceCacheInvalidator`）
- 设备删除
- 批量设备更新

---

## 6. 策略缓存

### 6.1 策略详情缓存

**Key 模板**：`fota:policy:{policyId}`  
**数据结构**：String (JSON)  
**TTL**：24 小时（滑动续期）  
**实现类**：`RedisPolicyCacheRepository.java`

**缓存内容**：
```json
{
  "id": 101,
  "productId": 1001,
  "name": "测试升级策略",
  "targetVersionId": 20,
  "sourceVersions": [10, 11, 12],
  "priority": 100,
  "grayRate": 50,
  "status": "ACTIVE",
  "timeWindow": {...},
  "targetMode": "ALL"
}
```

**读取流程**：
```java
// RedisPolicyCacheRepository.java（假设方法）
public Optional<UpgradePolicy> findById(Long policyId) {
    try {
        String key = buildPolicyKey(policyId);
        Object cached = redisTemplate.opsForValue().get(key);
        
        if (cached != null) {
            renewTtl(key);  // ✅ 续期
            return Optional.of(deserializePolicy(cached.toString()));
        }
    } catch (Exception e) {
        log.error("读取策略缓存失败: policyId={}", policyId, e);
    }
    return Optional.empty();
}
```

### 6.2 策略列表缓存（待迁移到 Keys Pattern）

**Key 模板**：`fota:cache:list:product:policy:{productId}:{type}`  
**数据结构**：Set（ID 集合）  
**TTL**：24 小时（滑动续期）  
**实现类**：`RedisPolicyCacheRepository.java`

**参数说明**：
- `{productId}`：产品 ID
- `{type}`：策略类型
  - `all` - 包含测试策略
  - `prod` - 仅生产策略

**示例**：
```redis
# 产品 1001 的所有策略（包含测试）
fota:cache:list:product:policy:1001:all  →  Set {101, 102, 103}

# 产品 1001 的生产策略
fota:cache:list:product:policy:1001:prod  →  Set {101, 102}
```

**当前实现（Embedded Pattern）**：
```java
// RedisPolicyCacheRepository.java:28-42
@Override
public List<UpgradePolicy> getProductPolicies(Long productId, boolean includeTestPolicies) {
    try {
        String key = buildProductPolicyListKey(productId, includeTestPolicies);
        Object cached = redisTemplate.opsForValue().get(key);
        
        if (cached != null) {
            renewTtl(key);  // ✅ 续期
            return deserializePolicies(cached.toString());
        }
    } catch (Exception e) {
        log.error("从 Redis 获取策略缓存失败: productId={}, includeTest={}", productId, includeTestPolicies, e);
    }
    return null;
}
```

**迁移计划**：
- 迁移到 Keys Pattern（Set 存储 ID 列表）
- 读取流程：SMEMBERS + MGET
- 预计收益：更新范围减少 95%

---

## 7. 产品缓存

### 7.1 产品信息缓存

**Key 模板**：`fota:product:{productId}`  
**数据结构**：String (JSON)  
**TTL**：7 天（滑动续期）  
**实现类**：`RedisProductCacheRepository.java`

**缓存内容**：
```json
{
  "id": 1001,
  "name": "智能手环 M476",
  "model": "M476",
  "description": "新一代智能手环",
  "status": "ACTIVE"
}
```

**读取流程**：
```java
// RedisProductCacheRepository.java
@Override
public Optional<Product> findById(Long productId) {
    try {
        String key = buildProductKey(productId);
        Object cached = redisTemplate.opsForValue().get(key);
        
        if (cached != null) {
            renewTtl(key);  // ✅ 续期
            return Optional.of(deserializeProduct(cached.toString()));
        }
    } catch (Exception e) {
        log.error("读取产品缓存失败: productId={}", productId, e);
    }
    return Optional.empty();
}
```

### 7.2 产品型号索引

**Key 模板**：`fota:cache:product:model:{model}`  
**数据结构**：String（产品 ID）  
**TTL**：7 天（滑动续期）  
**实现类**：`RedisProductCacheRepository.java`

**功能**：通过产品型号反向查找产品 ID

**示例**：
```redis
# 产品型号 M476 的索引
fota:cache:product:model:M476  →  "1001"
```

**使用场景**：
```java
// 通过型号查找产品
public Long getProductIdByModel(String model) {
    String key = String.format(PRODUCT_MODEL_INDEX_KEY_TEMPLATE, model);
    Object cached = redisTemplate.opsForValue().get(key);
    
    if (cached != null) {
        renewTtl(key);  // ✅ 续期
        return Long.parseLong(cached.toString());
    }
    
    // 缓存未命中，从数据库加载
    Product product = productRepository.findByModel(model);
    if (product != null) {
        cacheProductIdByModel(model, product.getId());
        return product.getId();
    }
    
    return null;
}
```

---

## 8. 固件缓存

### 8.1 固件详情缓存

**Key 模板**：`fota:firmware:{versionId}`  
**数据结构**：String (JSON)  
**TTL**：30 天（滑动续期）  
**实现类**：`RedisFirmwareCacheRepository.java`

**缓存内容**：
```json
{
  "id": 201,
  "productId": 1001,
  "version": "1.0.0",
  "internalVersion": "ASR_YEMEN_M476_V11_B03_Build02",
  "fileUrl": "fota/fw/201/a1b2c3d4e5f6.zip",
  "fileName": "firmware-v1.0.0.bin",
  "fileSize": 1048576,
  "md5": "abc123...",
  "sha256": "def456...",
  "tags": {"stability": "stable", "priority": "high"},
  "packageStatus": "READY"
}
```

**读取流程**：
```java
// RedisFirmwareCacheRepository.java:26-39
@Override
public Optional<FirmwareVersion> findById(Long versionId) {
    try {
        String key = buildFirmwareKey(versionId);
        Object cached = redisTemplate.opsForValue().get(key);
        
        if (cached != null) {
            renewTtl(key);  // ✅ 续期
            return Optional.ofNullable(deserializeFirmware(cached.toString()));
        }
    } catch (Exception e) {
        log.error("从 Redis 获取固件缓存失败: versionId={}", versionId, e);
    }
    return Optional.empty();
}
```

## 9. 缓存索引设计

### 9.1 索引键结构

**目的**：支持快速查找和批量失效

#### 9.1.1 产品缓存索引

**Key 模板**：`fota:cache:index:product:{productId}`  
**数据结构**：Set（存储相关缓存键）  
**TTL**：24 小时（滑动续期）  
**实现类**：`RedisPolicyCacheRepository.java:126-136`

**功能**：记录产品相关的所有缓存键

**示例 Set 内容**：
```redis
fota:cache:index:product:1001  →  Set {
  "fota:product:1001",
  "fota:cache:list:product:policy:1001:all",
  "fota:cache:list:product:policy:1001:prod"
}
```

**更新索引**：
```java
// RedisPolicyCacheRepository.java:126-136
private void updateCacheIndex(Long productId, String cacheKey) {
    try {
        String indexKey = String.format(RedisKeyConstants.PRODUCT_CACHE_INDEX_KEY_TEMPLATE, productId);
        redisTemplate.opsForSet().add(indexKey, cacheKey);
        
        long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.CACHE_INDEX_TTL_SECONDS);
        redisTemplate.expire(indexKey, Duration.ofSeconds(ttl));
    } catch (Exception e) {
        log.warn("更新缓存索引失败: productId={}, key={}", productId, cacheKey, e);
    }
}
```

#### 9.1.2 策略缓存索引

**Key 模板**：`fota:cache:index:policy:{policyId}`  
**数据结构**：Set  
**TTL**：24 小时（滑动续期）

**功能**：记录策略相关的所有缓存键

### 9.2 索引更新逻辑

```java
// 通用索引更新方法
public void updateCacheIndex(Long id, String cacheKey, String cacheType) {
    String indexKey = String.format("fota:cache:index:%s:%s", cacheType, id);
    redisTemplate.opsForSet().add(indexKey, cacheKey);
    
    // 根据缓存类型设置不同的 TTL
    long ttl = switch (cacheType) {
        case "policy" -> RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.POLICY_CACHE_TTL_SECONDS);
        case "product" -> RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.PRODUCT_CACHE_TTL_SECONDS);
        case "firmware" -> RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.FIRMWARE_CACHE_TTL_SECONDS);
        default -> RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.CACHE_INDEX_TTL_SECONDS);
    };
    redisTemplate.expire(indexKey, Duration.ofSeconds(ttl));
}
```

---

## 10. 策略快照缓存

### 10.1 快照数据存储

**Key 模板**：`fota:pol:snap:{scope}:{version}`  
**数据结构**：Hash  
**TTL**：7 天（滑动续期）  
**实现类**：`RedisPolicySnapshotRepository.java`

**功能**：存储策略快照数据，用于区域配置同步

**示例**：
```redis
# 主区域版本 42 的快照
fota:pol:snap:main:v42  →  Hash {
  "ver": "42",
  "product_id": "1001",
  "generated_at": "2026-03-04T10:30:00",
  "generated_by": "admin",
  "policies": "[...]",
  "firmwares": "[...]",
  "control": "{...}"
}
```

### 10.2 活跃版本指针

**Key 模板**：`fota:pol:active_ver:{scope}`  
**数据结构**：String（版本号）  
**TTL**：7 天（滑动续期）

**功能**：指向当前活跃的策略快照版本，原子切换

**示例**：
```redis
# 主区域活跃版本指针
fota:pol:active_ver:main  →  "v42"
```

### 10.3 同步时间戳

**Key 模板**：`fota:pol:sync_ts:{productId}`  
**数据结构**：String（时间戳）  
**TTL**：30 天（滑动续期）

**功能**：记录最后一次成功同步的时间戳，用于判断是否需要降级

**示例**：
```redis
# 产品 1001 的同步时间戳
fota:pol:sync_ts:1001  →  "1709545800"
```

### 10.4 写入锁

**Key 模板**：`fota:pol:write_lock:{productId}`  
**数据结构**：String  
**TTL**：30 秒（固定 TTL）

**功能**：防止多个版本同时写入快照的分布式锁

### 10.5 快照写入流程（Lua 脚本）

```lua
-- writeAndSwitchScript.lua
-- 原子性写入快照、更新时间戳、切换版本指针、添加到版本集合、清理旧版本

local snapKey = KEYS[1]
local activeVerKey = KEYS[2]
local tsKey = KEYS[3]
local versionsKey = KEYS[4]

local version = ARGV[1]
local snapshotData = ARGV[2]
local timestamp = ARGV[3]
local ttl = tonumber(ARGV[4])

-- 1. 写入快照数据（Hash）
redis.call('HSET', snapKey, 'ver', version)
redis.call('HSET', snapKey, 'product_id', snapshotData.productId)
-- ... 其他字段
redis.call('EXPIRE', snapKey, ttl)

-- 2. 更新同步时间戳
redis.call('SET', tsKey, timestamp)
redis.call('EXPIRE', tsKey, ttl * 4)  -- 时间戳 TTL 更长

-- 3. 切换活跃版本指针
redis.call('SET', activeVerKey, version)
redis.call('EXPIRE', activeVerKey, ttl)

-- 4. 添加到版本集合
redis.call('SADD', versionsKey, version)
redis.call('EXPIRE', versionsKey, ttl * 4)

-- 5. 清理旧版本（保留最近 3 个版本）
local allVersions = redis.call('SMEMBERS', versionsKey)
if #allVersions > 3 then
    -- 删除最旧的版本
    -- ...
end

return {1, version}
```

---

## 11. 活跃度 Bitmap

### 11.1 设备活跃度跟踪

**Key 模板**：`fota:active:{date}`  
**数据结构**：String (Bitmap)  
**TTL**：90 天（固定 TTL）  
**实现类**：`RedisDeviceActivityBitmapRepository.java`

**功能**：标记设备活跃状态，用于离线分析和统计

**示例**：
```redis
# 2026-03-04 的设备活跃度
fota:active:20260304  →  Bitmap（设备 ID 作为偏移量）
```

**优势**：
- 极高存储效率：1 亿设备 ≈ 12MB
- 支持位运算（AND、OR、XOR、NOT）

### 11.2 使用场景

#### 11.2.1 标记设备活跃

```java
// RedisDeviceActivityBitmapRepository.java
@Override
public void markActive(LocalDate day, long deviceId) {
    String key = String.format(RedisKeyConstants.ACTIVE_BITMAP_KEY_TEMPLATE,
        day.format(DateTimeFormatter.BASIC_ISO_DATE));
    redisTemplate.opsForValue().setBit(key, deviceId, true);
}
```

#### 11.2.2 检查设备是否活跃

```java
@Override
public boolean isActive(LocalDate day, long deviceId) {
    String key = String.format(RedisKeyConstants.ACTIVE_BITMAP_KEY_TEMPLATE,
        day.format(DateTimeFormatter.BASIC_ISO_DATE));
    return Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(key, deviceId));
}
```

#### 11.2.3 统计活跃设备数

```java
@Override
public long countActive(LocalDate day) {
    String key = String.format(RedisKeyConstants.ACTIVE_BITMAP_KEY_TEMPLATE,
        day.format(DateTimeFormatter.BASIC_ISO_DATE));
    return redisTemplate.execute((RedisCallback<Long>) connection ->
        connection.bitCount(key.getBytes())
    );
}
```

#### 11.2.4 多日活跃度统计

```java
@Override
public long countActiveUnion(LocalDate endDay, int days) {
    List<String> keys = new ArrayList<>();
    for (int i = 0; i < days; i++) {
        LocalDate day = endDay.minusDays(i);
        keys.add(String.format(RedisKeyConstants.ACTIVE_BITMAP_KEY_TEMPLATE,
            day.format(DateTimeFormatter.BASIC_ISO_DATE)));
    }
    
    String tempKey = String.format(RedisKeyConstants.BITOP_TEMP_KEY_TEMPLATE,
        "union", endDay.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + days);
    
    redisTemplate.execute((RedisCallback<Long>) connection ->
        connection.bitOp(BitOperation.OR, tempKey.getBytes(),
            keys.stream().map(String::getBytes).toArray(byte[][]::new))
    );
    
    // 设置临时键 TTL
    redisTemplate.expire(tempKey, Duration.ofSeconds(RedisKeyConstants.BITOP_TEMP_TTL_SECONDS));
    
    // 统计结果
    Long count = redisTemplate.execute((RedisCallback<Long>) connection ->
        connection.bitCount(tempKey.getBytes())
    );
    
    return count != null ? count : 0;
}
```

### 11.3 BITOP 临时键

**Key 模板**：`fota:tmp:bitop:{operation}:{identifier}`  
**数据结构**：String (Bitmap)  
**TTL**：60 秒（固定 TTL）

**功能**：用于 BITOP 操作的临时键，操作完成后自动过期

**示例**：
```redis
# 3 日活跃设备并集
fota:tmp:bitop:union:20260304-3
```

---

## 12. 其他缓存

### 12.1 配置信息缓存

**Key 模板**：`fota:config:{region}`  
**数据结构**：String (JSON)  
**TTL**：6 小时（滑动续期）

**功能**：存储配置信息

**示例**：
```redis
# 中国区域配置
fota:config:cn  →  "{...}"
```

### 12.2 分布式锁

**Key 模板**：`fota:lock:{lockName}`  
**数据结构**：String  
**TTL**：30 秒（固定 TTL）

**功能**：分布式锁，防止死锁

**示例**：
```redis
# 设备导入锁
fota:lock:device_import  →  "locked"
```

### 12.3 限流

**Key 模板**：`fota:ratelimit:{limitName}:{limitKey}`  
**数据结构**：String（计数器）  
**TTL**：60 秒（固定 TTL）  
**实现类**：`RedisDeviceRateLimiter.java`

**功能**：固定窗口限流

**示例**：
```redis
# 设备检查限流（IMEI + 分钟）
fota:ratelimit:check:861234567890123:202603041030  →  "5"

# IP 限流
fota:ratelimit:ip:127.0.0.1  →  "10"

# LastSeen 限频（每小时最多更新一次）
fota:ratelimit:lastseen:861234567890123  →  "1"
```

**限流配置**：

| 限流类型 | 限制 | 窗口 | Key 格式 |
|---------|------|------|---------|
| 设备检查 | 10 次 | 1 分钟 | `fota:ratelimit:check:{imei}:{minute}` |
| 设备上报 | 30 次 | 1 分钟 | `fota:ratelimit:report:{imei}:{minute}` |
| IP 限流 | 100 次 | 1 分钟 | `fota:ratelimit:ip:{ip}:{minute}` |
| LastSeen 更新 | 1 次 | 1 小时 | `fota:ratelimit:lastseen:{imei}:{hour}` |

### 12.4 固件上传会话

**Key 模板**：`fota:fw:upload:sess:{sessionId}`  
**数据结构**：String (JSON)  
**TTL**：2 小时（固定 TTL）

**功能**：固件上传会话元数据，包含文件哈希、临时路径等信息

**示例**：
```redis
# 固件上传会话
fota:fw:upload:sess:a1b2c3d4e5f6  →  "{...}"
```

### 12.5 固件上传锁

**Key 模板**：`fota:fw:upload:lock:{productId}:{version}`  
**数据结构**：String  
**TTL**：30 秒（固定 TTL）

**功能**：防止并发上传同一版本号的分布式锁

**示例**：
```redis
# 产品 1001 版本 1.0.0 的上传锁
fota:fw:upload:lock:1001:1.0.0  →  "locked"
```

### 12.6 设备导入会话

**Key 模板**：`fota:device:import:sess:{sessionId}`  
**数据结构**：String (JSON)  
**TTL**：2 小时（固定 TTL）

**功能**：设备导入会话数据，包含解析后的 IMEI 列表和统计信息

**示例**：
```redis
# 设备导入会话
fota:device:import:sess:a1b2c3d4e5f6  →  "{...}"
```

---

## 13. 批量失效策略

### 13.1 产品失效

**实现类**：`ProductCacheInvalidator.java`

```java
// 失效产品相关的所有缓存
public void invalidateProductCache(Long productId) {
    // 1. 获取产品相关的所有缓存键
    Set<String> relatedKeys = redisTemplate.opsForSet()
        .members(String.format(RedisKeyConstants.PRODUCT_CACHE_INDEX_KEY_TEMPLATE, productId));

    // 2. 批量删除
    if (CollectionUtils.isNotEmpty(relatedKeys)) {
        redisTemplate.delete(relatedKeys);
    }

    // 3. 删除索引键
    redisTemplate.delete(String.format(RedisKeyConstants.PRODUCT_CACHE_INDEX_KEY_TEMPLATE, productId));

    // 4. 删除产品型号索引
    // redisTemplate.delete(String.format(RedisKeyConstants.PRODUCT_MODEL_INDEX_KEY_TEMPLATE, model));
}
```

**触发场景**：
- 产品信息更新
- 产品删除
- 产品型号变更

### 13.2 策略失效

**实现类**：`PolicyCacheInvalidator.java`

```java
// 失效策略相关的所有缓存
public void invalidatePolicyCache(Long policyId) {
    Set<String> relatedKeys = redisTemplate.opsForSet()
        .members(String.format(RedisKeyConstants.POLICY_CACHE_INDEX_KEY_TEMPLATE, policyId));

    if (CollectionUtils.isNotEmpty(relatedKeys)) {
        redisTemplate.delete(relatedKeys);
    }

    redisTemplate.delete(String.format(RedisKeyConstants.POLICY_CACHE_INDEX_KEY_TEMPLATE, policyId));
}
```

**触发场景**：
- 策略信息更新
- 策略删除
- 策略状态变更

### 13.3 固件失效

**实现类**：`FirmwareCacheInvalidator.java`

```java
// 失效固件详情缓存 + 版本映射缓存
public void invalidateOnFirmwarePublish(Long productId, Long versionId, String version, String internalVersion) {
    firmwareCacheRepository.evict(versionId);
    firmwareVersionLookupCacheRepository.evictByVersion(productId, version);
    if (StringUtils.hasText(internalVersion)) {
        firmwareVersionLookupCacheRepository.evict(productId, version, internalVersion);
    }
}
```

**触发场景**：
- 固件信息更新
- 固件删除
- 固件包状态变更

### 13.4 失效流程图

```
管理后台修改
    ↓
发布缓存失效事件（ApplicationEventPublisher）
    ↓
CacheInvalidationListener 监听事件
    ↓
调用对应的 CacheInvalidator
    ↓
1. 从索引 Set 获取相关缓存键
2. 批量删除相关缓存键
3. 删除索引键
    ↓
缓存失效完成
```

---

## 14. 性能考虑

### 14.1 内存使用

| 优化项 | 说明 | 收益 |
|--------|------|------|
| **使用 String 类型存储 JSON** | 节省内存 | 单对象 ~1KB |
| **Set 类型的索引键只存储字符串** | 内存占用小 | 单索引 ~100 bytes |
| **分层 TTL 策略** | 根据数据变更频率设置不同 TTL | 平衡内存与性能 |
| **共享缓存续期** | 热点数据持续缓存 | 减少重复加载 |
| **设备缓存精简** | 只缓存 5 个核心字段 | 1000万设备 ≈ 5GB |

### 14.2 并发性能

| 优化项 | 说明 |
|--------|------|
| **使用 Set 数据结构的原子操作** | 保证并发安全 |
| **批量操作减少网络往返** | MGET、Pipeline |
| **避免大对象的序列化** | 单对象 < 10KB |
| **Lua 脚本保证原子性** | 策略快照写入、限流 |

### 14.3 缓存雪崩防护

| 防护措施 | 说明 |
|---------|------|
| **分层 TTL** | 不同类型缓存使用不同 TTL（24h/7d/30d） |
| **TTL 随机化** | 基础 TTL ±10% 随机抖动 |
| **滑动续期** | 共享缓存访问时续期，自然分散过期时间 |
| **短 TTL 负缓存** | 映射未命中仅缓存 60 秒，防止穿透且避免长期误判 |
| **降级策略** | 缓存不可用时直接查询数据库 |

### 14.4 一致性保证

| 保证措施 | 说明 |
|---------|------|
| **主动失效** | 管理后台修改后立即清除相关缓存 |
| **滑动 TTL** | 兜底过期机制，防止主动失效失败 |
| **索引联动** | 主缓存失效时同步清除索引 |

---

## 15. 监控与维护

### 15.1 监控指标

| 指标 | 阈值 | 告警级别 | 说明 |
|------|------|---------|------|
| Redis 连接池使用率 | > 80% | 警告 | 可能连接池耗尽 |
| Redis 命令平均耗时 | > 10ms | 警告 | 性能下降 |
| Redis 慢查询 | > 100 | 警告 | 需优化查询 |
| Redis 内存使用率 | > 80% | 严重 | 可能 OOM |
| 缓存命中率 | < 80% | 警告 | 缓存效果差 |
| 限流命中率 | > 10% | 警告 | 可能被攻击 |

### 15.2 维护策略

- **定期 review 键的命名和结构**
- **根据业务发展调整 TTL**
- **清理无用的缓存键**
- **优化序列化性能**
- **监控缓存命中率**
- **定期清理过期索引**

### 15.3 日志记录

```java
@Slf4j
@Service
public class RedisDeviceCacheRepository {

    public DeviceCache get(String imei) {
        try {
            // Redis 操作
            log.debug("设备缓存命中: imei={}", imei);
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed: imei={}", imei, e);
            // 降级处理
        } catch (RedisException e) {
            log.warn("Redis operation failed: imei={}", imei, e);
        }
    }
}
```

---

## 16. 变更历史

| 版本 | 日期 | 变更内容 | 作者 |
|------|------|---------|------|
| v3.0 | 2026-03-04 | 重新组织文档结构，添加总览表格，按类型分章节详细说明 | FOTA Team |
| v2.0 | 2026-03-03 | 更新 TTL 策略为分层设计（24h/7d/30d），添加共享缓存续期机制 | FOTA Team |
| v1.0 | 2026-02-17 | 初始版本 | FOTA Team |

---

## 17. 相关文档

- **标准文档**：`docs/03-standards/redis-cache-standards.md`
- **设计决策**：`docs/02-architecture/cache-design-decisions.md`
- **代码常量**：`fota-framework-cache/src/main/java/com/wewins/fota/cache/constant/RedisKeyConstants.java`
- **实现示例**：
  - `fota-service/src/main/java/com/wewins/fota/infra/cache/RedisDeviceCacheRepository.java`
  - `fota-service/src/main/java/com/wewins/fota/infra/cache/RedisPolicyCacheRepository.java`
  - `fota-service/src/main/java/com/wewins/fota/infra/cache/RedisProductCacheRepository.java`
  - `fota-service/src/main/java/com/wewins/fota/infra/cache/RedisFirmwareCacheRepository.java`
  - `fota-service/src/main/java/com/wewins/fota/infra/cache/RedisPolicySnapshotRepository.java`
  - `fota-framework-cache/src/main/java/com/wewins/fota/cache/bitmap/RedisDeviceActivityBitmapRepository.java`
