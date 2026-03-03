# Redis 缓存键设计方案

> **版本**: v2.0
> **更新日期**: 2026-03-03
> **变更**: 更新 TTL 策略为分层设计（24h/7d/30d），添加共享缓存续期机制

## 1. 设计概述

本文档设计了一套完整的 Redis 缓存键结构，用于策略、产品、固件版本等数据的缓存管理。设计遵循以下原则：

- **统一规范**：使用 `fota:{module}:{key}` 的命名规范
- **关联性**：通过索引键实现快速查找和批量失效
- **可扩展性**：预留扩展空间，便于后续新增缓存类型
- **性能优先**：选择合适的数据结构，优化读写性能
- **分层 TTL**：不同数据类型采用不同的 TTL 和续期策略

## 2. 键命名规范

### 2.1 基本格式
```
fota:{module}:{key}
```

### 2.2 命名规则
- **前缀**：`fota`（统一标识）
- **模块**：明确标识缓存的数据类型
- **分隔符**：`:`（冒号）
- **参数**：使用字符串格式化，避免复杂嵌套

### 2.3 已有模块
- `device` - 设备相关
- `policy` - 策略相关
- `product` - 产品相关
- `config` - 配置相关
- `pol` - 策略快照
- `cache` - 缓存索引和集合
- `firmware` - 固件相关

## 3. 具体键设计

### 3.1 策略相关缓存

#### 3.1.1 产品策略列表

**功能**：缓存指定产品的策略列表，区分是否包含测试策略

**键设计**：
```
fota:cache:product:policy:{productId}:{type}
```

**参数说明**：
- `{productId}`：产品 ID
- `{type}`：策略类型，取值：
  - `all` - 包含测试策略
  - `prod` - 仅生产策略

**示例**：
```
fota:cache:product:policy:1001:all  # 产品 1001 的所有策略
fota:cache:product:policy:1001:prod # 产品 1001 的生产策略
```

**数据结构**：
- **类型**：String（存储 JSON 序列化的策略列表）
- **TTL**：24 小时（滑动续期）
- **一致性**：主动失效（策略修改时立即清除）

#### 3.1.2 单个策略详情

**键设计**：
```
fota:policy:{policyId}
```

**示例**：
```
fota:policy:101  # 策略 ID 为 101 的详情
```

**数据结构**：
- **类型**：String（JSON）
- **TTL**：24 小时（滑动续期）
- **一致性**：主动失效

#### 3.1.3 策略快照（已存在）

**键设计**：
```
fota:pol:snap:{scope}:{version}
fota:pol:active_ver:{scope}
```

**示例**：
```
fota:pol:snap:main:v42   # 主区域版本 42 的快照
fota:pol:active_ver:main # 主区域活跃版本指针
```

### 3.2 产品信息缓存

#### 3.2.1 产品基本信息

**键设计**：
```
fota:product:{productId}
```

**示例**：
```
fota:product:1001  # 产品 ID 为 1001 的信息
```

**数据结构**：
- **类型**：String（JSON）
- **TTL**：7 天（滑动续期）
- **一致性**：主动失效
- **理由**：产品信息极少变更

#### 3.2.2 产品型号索引

**功能**：通过产品型号反向查找产品 ID

**键设计**：
```
fota:cache:product:model:{model}
```

**示例**：
```
fota:cache:product:model:M476  # 产品型号 M476 的索引
```

**数据结构**：
- **类型**：String（存储产品 ID 字符串）
- **TTL**：7 天（滑动续期）
- **一致性**：主动失效

### 3.3 固件版本信息缓存

#### 3.3.1 固件详情

**键设计**：
```
fota:firmware:{versionId}
```

**示例**：
```
fota:firmware:201  # 固件版本 ID 为 201 的详情
```

**数据结构**：
- **类型**：String（JSON）
- **TTL**：30 天（滑动续期）
- **一致性**：主动失效
- **理由**：固件发布后基本不变

#### 3.3.2 产品固件列表

**功能**：存储产品关联的所有固件版本 ID

**键设计**：
```
fota:cache:firmware:list:{productId}
```

**示例**：
```
fota:cache:firmware:list:1001  # 产品 1001 的固件列表
```

**数据结构**：
- **类型**：Set（存储版本 ID 字符串）
- **TTL**：30 天（滑动续期）
- **一致性**：主动失效

#### 3.3.3 固件标签索引

**功能**：按标签索引固件版本，支持标签查询

**键设计**：
```
fota:cache:firmware:tag:{productId}:{tag}
```

**示例**：
```
fota:cache:firmware:tag:1001:stable    # 产品 1001 标签为 stable 的固件
fota:cache:firmware:tag:1001:beta     # 产品 1001 标签为 beta 的固件
```

**数据结构**：
- **类型**：Set（存储版本 ID 字符串）
- **TTL**：30 天（滑动续期）
- **一致性**：主动失效

## 4. 缓存索引设计

### 4.1 索引键结构

为了支持快速查找和批量失效，设计以下索引：

#### 4.1.1 产品缓存索引

**键设计**：
```
fota:cache:index:product:{productId}
```

**功能**：记录产品相关的所有缓存键

**数据结构**：
- **类型**：Set（存储相关缓存键）
- **TTL**：7 天（与产品信息一致）

**示例 Set 内容**：
```
fota:product:1001
fota:cache:product:policy:1001:all
fota:cache:product:policy:1001:prod
fota:cache:firmware:list:1001
```

#### 4.1.2 策略缓存索引

**键设计**：
```
fota:cache:index:policy:{policyId}
```

**功能**：记录策略相关的所有缓存键

**数据结构**：
- **类型**：Set
- **TTL**：24 小时（与策略缓存一致）

#### 4.1.3 固件缓存索引

**键设计**：
```
fota:cache:index:firmware:{versionId}
```

**功能**：记录固件相关的所有缓存键

**数据结构**：
- **类型**：Set
- **TTL**：30 天（与固件缓存一致）

### 4.2 索引更新逻辑

```java
// 更新缓存索引（TTL 与对应缓存类型一致）
public void updateCacheIndex(Long id, String cacheKey, String cacheType) {
    String indexKey = String.format("fota:cache:index:%s:%s", cacheType, id);
    redisTemplate.opsForSet().add(indexKey, cacheKey);
    
    // 根据缓存类型设置不同的 TTL
    long ttl = switch (cacheType) {
        case "policy" -> 24 * 60 * 60;  // 24 小时
        case "product" -> 7 * 24 * 60 * 60;  // 7 天
        case "firmware" -> 30 * 24 * 60 * 60;  // 30 天
        default -> 24 * 60 * 60;
    };
    redisTemplate.expire(indexKey, ttl, TimeUnit.SECONDS);
}

// 批量失效
public void invalidateCache(Long id, String cacheType) {
    String indexKey = String.format("fota:cache:index:%s:%s", cacheType, id);
    Set<String> relatedKeys = redisTemplate.opsForSet().members(indexKey);

    if (relatedKeys != null && !relatedKeys.isEmpty()) {
        redisTemplate.delete(relatedKeys);
        redisTemplate.delete(indexKey);
    }
}
```

## 5. 数据结构选择

### 5.1 选择原则

| 缓存类型 | 数据结构 | 选择原因 |
|---------|---------|---------|
| 策略列表 | String | JSON 序列化，结构清晰 |
| 产品信息 | String | 简单信息，JSON 易于解析 |
| 固件版本 | String | 结构化数据，JSON 易于扩展 |
| 索引集合 | Set | 高效的添加、删除、成员检查 |
| 计数器 | String | 简单的数字计数 |

### 5.2 序列化方式

统一使用 JSON 序列化：
- **优点**：
  - 可读性好，便于调试
  - 与现有策略快照保持一致
  - 支持复杂对象的序列化
- **配置**：
  - 使用 Jackson 进行序列化
  - 统一配置 `ObjectMapper`
  - 启用默认日期格式化

## 6. 完整键示例

### 6.1 策略相关
```
fota:cache:product:policy:1001:all     # 产品 1001 的所有策略
fota:cache:product:policy:1001:prod    # 产品 1001 的生产策略
fota:policy:101                        # 策略 101 的详细信息
fota:pol:snap:main:v42                 # 策略快照
fota:pol:active_ver:main              # 活跃版本指针
fota:cache:index:policy:101            # 策略 101 的缓存索引
```

### 6.2 产品相关
```
fota:product:1001                      # 产品 1001 的基本信息
fota:cache:product:model:M476          # 产品型号 M476 的索引
fota:cache:index:product:1001         # 产品 1001 的缓存索引
```

### 6.3 固件相关
```
fota:firmware:201                      # 固件版本 201 的详细信息
fota:cache:firmware:list:1001          # 产品 1001 的固件列表
fota:cache:firmware:tag:1001:stable   # 产品 1001 标签为 stable 的固件
fota:cache:index:firmware:201         # 固件 201 的缓存索引
```

## 7. 批量失效策略

### 7.1 产品失效
```java
// 失效产品相关的所有缓存
public void invalidateProductCache(Long productId) {
    // 1. 获取产品相关的所有缓存键
    Set<String> relatedKeys = redisTemplate.opsForSet()
        .members("fota:cache:index:product:" + productId);

    // 2. 批量删除
    if (CollectionUtils.isNotEmpty(relatedKeys)) {
        redisTemplate.delete(relatedKeys);
    }

    // 3. 删除索引键
    redisTemplate.delete("fota:cache:index:product:" + productId);

    // 4. 删除产品型号索引
    redisTemplate.delete("fota:cache:product:model:" + model);
}
```

### 7.2 策略失效
```java
// 失效策略相关的所有缓存
public void invalidatePolicyCache(Long policyId) {
    Set<String> relatedKeys = redisTemplate.opsForSet()
        .members("fota:cache:index:policy:" + policyId);

    if (CollectionUtils.isNotEmpty(relatedKeys)) {
        redisTemplate.delete(relatedKeys);
    }

    redisTemplate.delete("fota:cache:index:policy:" + policyId);
}
```

### 7.3 固件失效
```java
// 失效固件相关的所有缓存
public void invalidateFirmwareCache(Long versionId) {
    Set<String> relatedKeys = redisTemplate.opsForSet()
        .members("fota:cache:index:firmware:" + versionId);

    if (CollectionUtils.isNotEmpty(relatedKeys)) {
        redisTemplate.delete(relatedKeys);
    }

    redisTemplate.delete("fota:cache:index:firmware:" + versionId);
}
```

## 8. 缓存管理服务设计

### 8.1 服务接口
```java
@Service
public class CacheManagerService {

    // 策略缓存管理
    void cacheProductPolicies(Long productId, List<UpgradePolicy> policies, boolean includeTest);
    List<UpgradePolicy> getCachedProductPolicies(Long productId, boolean includeTest);
    void invalidatePolicyCache(Long policyId);

    // 产品缓存管理
    void cacheProductInfo(Product product);
    Product getCachedProductInfo(Long productId);
    Long getProductIdByModel(String model);
    void invalidateProductCache(Long productId);

    // 固件缓存管理
    void cacheFirmwareInfo(FirmwareVersion firmware);
    FirmwareVersion getCachedFirmwareInfo(Long versionId);
    Set<Long> getProductFirmwareList(Long productId);
    void invalidateFirmwareCache(Long versionId);
}
```

### 8.2 扩展 RedisKeyConstants
```java
// ========== 键模板 ==========
PRODUCT_POLICY_LIST_KEY_TEMPLATE = "fota:cache:product:policy:%s:%s";
PRODUCT_MODEL_INDEX_KEY_TEMPLATE = "fota:cache:product:model:%s";
PRODUCT_FIRMWARE_LIST_KEY_TEMPLATE = "fota:cache:firmware:list:%s";
FIRMWARE_TAG_INDEX_KEY_TEMPLATE = "fota:cache:firmware:tag:%s:%s";
PRODUCT_CACHE_INDEX_KEY_TEMPLATE = "fota:cache:index:product:%s";
POLICY_CACHE_INDEX_KEY_TEMPLATE = "fota:cache:index:policy:%s";
FIRMWARE_CACHE_INDEX_KEY_TEMPLATE = "fota:cache:index:firmware:%s";
FIRMWARE_KEY_TEMPLATE = "fota:firmware:%s";

// ========== 分层 TTL 常量 ==========
// 策略缓存：24小时
POLICY_CACHE_TTL_SECONDS = 24 * 60 * 60;
// 产品缓存：7天
PRODUCT_CACHE_TTL_SECONDS = 7 * 24 * 60 * 60;
// 固件缓存：30天
FIRMWARE_CACHE_TTL_SECONDS = 30 * 24 * 60 * 60;
```

### 8.3 TTL 配置汇总

| 缓存类型 | 键前缀 | TTL | 续期策略 | 一致性保证 |
|---------|--------|-----|---------|-----------|
| **策略列表** | `fota:cache:product:policy:` | 24小时 | ✅ 滑动续期 | 主动失效 |
| **策略详情** | `fota:policy:` | 24小时 | ✅ 滑动续期 | 主动失效 |
| **产品信息** | `fota:product:` | 7天 | ✅ 滑动续期 | 主动失效 |
| **产品型号索引** | `fota:cache:product:model:` | 7天 | ✅ 滑动续期 | 主动失效 |
| **固件详情** | `fota:firmware:` | 30天 | ✅ 滑动续期 | 主动失效 |
| **固件列表** | `fota:cache:firmware:` | 30天 | ✅ 滑动续期 | 主动失效 |
| **策略索引** | `fota:cache:index:policy:` | 24小时 | ✅ 滑动续期 | 随主缓存失效 |
| **产品索引** | `fota:cache:index:product:` | 7天 | ✅ 滑动续期 | 随主缓存失效 |
| **固件索引** | `fota:cache:index:firmware:` | 30天 | ✅ 滑动续期 | 随主缓存失效 |

## 9. 性能考虑

### 9.1 内存使用
- 使用 String 类型存储 JSON，节省内存
- Set 类型的索引键只存储字符串，内存占用小
- **分层 TTL 策略**：根据数据变更频率设置不同 TTL（24h/7d/30d）
- **共享缓存续期**：热点数据持续缓存，减少重复加载

### 9.2 并发性能
- 使用 Set 数据结构的原子操作保证并发安全
- 批量操作减少网络往返
- 避免大对象的序列化

### 9.3 缓存雪崩防护
- **分层 TTL**：不同类型缓存使用不同 TTL（24h/7d/30d）
- **TTL 随机化**：基础 TTL ±10% 随机抖动
- **滑动续期**：共享缓存访问时续期，自然分散过期时间
- **降级策略**：缓存不可用时直接查询数据库

### 9.4 一致性保证
- **主动失效**：管理后台修改后立即清除相关缓存
- **滑动 TTL**：兜底过期机制，防止主动失效失败
- **索引联动**：主缓存失效时同步清除索引

## 10. 监控与维护

### 10.1 监控指标
- 缓存命中率
- 缓存大小
- TTL 分布
- 失效操作次数

### 10.2 维护策略
- 定期 review 键的命名和结构
- 根据业务发展调整 TTL
- 清理无用的缓存键
- 优化序列化性能

## 11. 总结

本设计方案提供了完整的 Redis 缓存键结构，具有以下特点：

1. **规范统一**：遵循现有命名规范，易于维护
2. **关联性强**：通过索引实现快速查找和批量失效
3. **扩展性好**：预留扩展空间，便于新增缓存类型
4. **性能优化**：选择合适的数据结构，优化读写性能
5. **易于维护**：清晰的结构和文档，便于团队协作
6. **分层 TTL**：24h/7d/30d 针对不同数据类型优化
7. **共享缓存续期**：热点数据持续缓存，性能最优
8. **主动失效**：管理后台修改立即生效

该方案能够满足当前业务需求，并为未来发展提供了良好的基础。

---

> **相关文档**：
> - [Redis 缓存实现方案](../04-guides/redis-cache-implementation-plan.md) - 完整实现方案
> - [TTL 策略设计](./ttl-strategy-design.md) - TTL 详细设计
> - [Redis 缓存标准](../03-standards/redis-cache-standards.md) - 编码规范