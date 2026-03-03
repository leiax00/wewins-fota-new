# FOTA 平台 TTL 缓存策略设计

> **版本**: v2.0
> **更新日期**: 2026-03-03
> **适用范围**: FOTA 平台所有 Redis 缓存设计

---

## 📋 概述

基于 FOTA 平台**脉冲式检查模式**和**分层 TTL 策略**，设计了一套平衡性能、内存和数据一致性的缓存策略。

---

## 🎯 设计原则

### 1. 设备检查模式

| 模式 | 检查间隔 | 沉默期 | 说明 |
|------|---------|--------|------|
| **脉冲模式**（推荐） | 2小时 | Day 2-3 (48-72h) | Day 1 检查，Day 2-3 沉默，每周循环 |
| **稳定模式** | 6小时 | 无 | 固定间隔持续检查 |
| **服务端控制** | 动态调整 | 可变 | 根据负载动态调整 |

### 2. 分层 TTL 策略

| 缓存类型 | TTL | 续期策略 | 一致性保证 | 理由 |
|---------|-----|---------|-----------|------|
| **设备缓存** | 24小时 | ❌ 不续期 | 被动过期 | 沉默期释放内存（主要内存占用） |
| **策略缓存** | 24小时 | ✅ **续期** | 主动失效 | 热点数据持续缓存 |
| **产品信息** | 7天 | ✅ **续期** | 主动失效 | 产品信息极少变更 |
| **固件版本** | 30天 | ✅ **续期** | 主动失效 | 固件发布后基本不变 |

### 3. 核心目标

- ✅ **设备缓存沉默期释放内存**: 10GB → 0（Day 2-3）
- ✅ **共享缓存续期保持热点数据**: 性能最优
- ✅ **主动失效保证一致性**: 运营修改立即生效
- ✅ **兜底过期防止失效失败**: 较长 TTL 作为保险

---

## 📊 TTL 配置方案

### 分层 TTL 配置表

| 数据类型 | TTL | 续期 | 变更频率 | 失效策略 | 备注 |
|---------|-----|------|---------|---------|------|
| **设备缓存** | **24 小时** | ❌ 不续期 | 检测时更新 | 被动过期 | 沉默期释放内存 |
| **策略缓存** | **24 小时** | ✅ **续期** | 偶尔（运营操作） | 主动失效 + 兜底过期 | 热点数据持续缓存 |
| **产品信息** | **7 天** | ✅ **续期** | 极少变更 | 主动失效 + 兜底过期 | 产品信息稳定 |
| **固件版本** | **30 天** | ✅ **续期** | 发布时更新 | 主动失效 + 兜底过期 | 固件发布后基本不变 |
| **活跃度 Bitmap** | **90 天** | ❌ 不续期 | 每日新增 | 按日创建 | 用于离线分析 |

### 详细说明

#### 1. 设备缓存（24 小时，不续期）

```java
// RedisKeyConstants.java
public static final long DEVICE_CACHE_TTL_SECONDS = 24 * 60 * 60; // 24小时
```

**读取时不续期**：
```java
public DeviceCache get(String imei) {
    String key = String.format(DEVICE_KEY_TEMPLATE, imei);
    Object cached = redisTemplate.opsForValue().get(key);
    // ❌ 不调用 expire()，让缓存自然过期
    return (DeviceCache) cached;
}
```

**理由**：
- 每个设备独立缓存
- 脉冲模式 Day 2-3 沉默期，设备不检查
- 缓存自然过期，释放内存（10GB → 0）

#### 2. 策略缓存（24 小时，续期）

```java
// RedisKeyConstants.java
public static final long POLICY_CACHE_TTL_SECONDS = 24 * 60 * 60; // 24小时
```

**读取时续期**：
```java
public List<UpgradePolicy> getProductPolicies(Long productId, boolean includeTest) {
    String key = buildKey(productId, includeTest);
    String json = redisTemplate.opsForValue().get(key);
    
    if (json != null) {
        // ✅ 续期：热点数据持续保持缓存
        long ttl = getRandomizedTtl(POLICY_CACHE_TTL_SECONDS);
        redisTemplate.expire(key, Duration.ofSeconds(ttl));
        return deserializePolicies(json);
    }
    
    return null;
}
```

**理由**：
- 所有设备共享同一份缓存
- 千万设备访问时间分散，几乎每时每刻都有访问
- 续期保证热点数据持续缓存
- 主动失效保证策略变更立即生效
- 24 小时兜底过期防止主动失效失败

#### 3. 产品信息（7 天，续期）

```java
// RedisKeyConstants.java
public static final long PRODUCT_CACHE_TTL_SECONDS = 7 * 24 * 60 * 60; // 7天
```

**理由**：
- 产品信息极少变更
- 7 天足够保证数据新鲜度
- 主动失效保证产品发布时立即生效

#### 4. 固件版本（30 天，续期）

```java
// RedisKeyConstants.java
public static final long FIRMWARE_CACHE_TTL_SECONDS = 30 * 24 * 60 * 60; // 30天
```

**理由**：
- 固件发布后基本不变
- 30 天足够覆盖发布周期
- 主动失效保证固件发布时立即生效

---

## 🔧 实现方案

### 1. 分层 TTL 配置

```java
// RedisKeyConstants.java
public final class RedisKeyConstants {
    
    // ========== 设备缓存（固定TTL，不续期）==========
    public static final long DEVICE_CACHE_TTL_SECONDS = 24 * 60 * 60;  // 24小时
    
    // ========== 共享缓存（滑动TTL，续期）==========
    public static final long POLICY_CACHE_TTL_SECONDS = 24 * 60 * 60;      // 24小时
    public static final long PRODUCT_CACHE_TTL_SECONDS = 7 * 24 * 60 * 60; // 7天
    public static final long FIRMWARE_CACHE_TTL_SECONDS = 30 * 24 * 60 * 60; // 30天
    
    // ========== 其他缓存 ==========
    public static final long ACTIVE_BITMAP_TTL_SECONDS = 90 * 24 * 60 * 60; // 90天
    public static final long RATE_LIMIT_TTL_SECONDS = 60;                 // 60秒
}
```

### 2. TTL 随机化（防缓存雪崩）

```java
// RandomizedTtlUtil.java
public class RandomizedTtlUtil {

    private static final double RANDOM_FACTOR = 0.1; // ±10%

    /**
     * 10% 随机偏移，避免同一时间大量缓存失效
     * 
     * @param baseTtl 基础 TTL（秒）
     * @return 随机化后的 TTL（秒）
     */
    public static long getRandomizedTtl(long baseTtl) {
        long randomOffset = (long) (baseTtl * RANDOM_FACTOR * (Math.random() - 0.5));
        return Math.max(1, baseTtl + randomOffset); // 确保大于0
    }

    // 使用示例
    public long getPolicyTtl() {
        long baseTtl = RedisKeyConstants.POLICY_CACHE_TTL_SECONDS;
        return getRandomizedTtl(baseTtl);
    }
}
```

### 3. 设备缓存：固定 TTL（不续期）

```java
// RedisDeviceCacheRepository.java
@Repository
@RequiredArgsConstructor
public class RedisDeviceCacheRepository implements DeviceCacheRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    
    @Override
    public DeviceCache get(String imei) {
        String key = String.format(DEVICE_KEY_TEMPLATE, imei);
        Object cached = redisTemplate.opsForValue().get(key);
        // ❌ 不续期，让缓存自然过期（沉默期释放内存）
        return (DeviceCache) cached;
    }
    
    @Override
    public void put(String imei, DeviceCache cache) {
        String key = String.format(DEVICE_KEY_TEMPLATE, imei);
        cache.setCachedAt(LocalDateTime.now());
        // 固定 TTL，不续期
        redisTemplate.opsForValue().set(key, cache,
            Duration.ofSeconds(DEVICE_CACHE_TTL_SECONDS));
    }
}
```

### 4. 共享缓存：滑动 TTL（续期）

```java
// RedisPolicyCacheRepository.java
@Repository
@RequiredArgsConstructor
public class RedisPolicyCacheRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * 获取产品策略列表（带续期）
     */
    public List<UpgradePolicy> getProductPolicies(Long productId, boolean includeTest) {
        String key = buildKey(productId, includeTest);
        String json = redisTemplate.opsForValue().get(key);
        
        if (json != null) {
            // ✅ 续期：热点数据持续保持缓存
            long ttl = getRandomizedTtl(POLICY_CACHE_TTL_SECONDS);
            redisTemplate.expire(key, Duration.ofSeconds(ttl));
            return deserializePolicies(json);
        }
        
        return null;
    }
    
    /**
     * 缓存产品策略列表
     */
    public void cacheProductPolicies(Long productId, boolean includeTest, 
                                      List<UpgradePolicy> policies) {
        String key = buildKey(productId, includeTest);
        String json = serializePolicies(policies);
        
        long ttl = getRandomizedTtl(POLICY_CACHE_TTL_SECONDS);
        redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttl));
    }
}
```

### 5. 主动失效机制

```java
// PolicyCacheInvalidator.java
@Service
@RequiredArgsConstructor
public class PolicyCacheInvalidator {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 策略修改后失效缓存（立即生效）
     */
    public void invalidateOnPolicyChange(Long productId, Long policyId) {
        // 1. 删除单个策略详情
        String policyKey = String.format(POLICY_KEY_TEMPLATE, policyId);
        redisTemplate.delete(policyKey);
        
        // 2. 删除产品的策略列表缓存
        String allPoliciesKey = String.format(PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, "all");
        String prodPoliciesKey = String.format(PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, "prod");
        redisTemplate.delete(List.of(allPoliciesKey, prodPoliciesKey));
        
        log.info("策略缓存已失效: productId={}, policyId={}", productId, policyId);
    }
}

// CacheInvalidationListener.java
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheInvalidationListener {
    
    private final PolicyCacheInvalidator policyCacheInvalidator;
    
    @EventListener
    @Async  // 异步执行，不阻塞业务逻辑
    public void onPolicyChanged(PolicyChangedEvent event) {
        log.info("收到策略变更事件: productId={}, policyId={}", 
            event.productId(), event.policyId());
        
        policyCacheInvalidator.invalidateOnPolicyChange(event.productId(), event.policyId());
    }
}
```

### 6. 降级策略

```java
// CacheFallbackService.java
@Service
@RequiredArgsConstructor
public class CacheFallbackService {

    private final ProductService productService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final boolean redisAvailable = checkRedisAvailable();

    private boolean checkRedisAvailable() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            log.warn("Redis is unavailable, using fallback mode");
            return false;
        }
    }

    public Product getProductWithFallback(Long productId) {
        // Redis 不可用时直接查数据库
        if (!redisAvailable) {
            log.debug("Redis unavailable, direct DB query for product: {}", productId);
            return productService.findById(productId);
        }

        try {
            // 尝试从缓存获取
            String key = "product:" + productId;
            Product cached = (Product) redisTemplate.opsForValue().get(key);
            if (cached != null) {
                // ✅ 续期（共享缓存）
                redisTemplate.expire(key, Duration.ofSeconds(getRandomizedTtl(PRODUCT_CACHE_TTL_SECONDS)));
                return cached;
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis connection failed, fallback to DB for product: {}", productId);
        }

        // 缓存未命中，查数据库并回写
        Product product = productService.findById(productId);
        if (product != null) {
            redisTemplate.opsForValue().set(key, product,
                Duration.ofSeconds(getRandomizedTtl(PRODUCT_CACHE_TTL_SECONDS)));
        }
        return product;
    }
}
```

---

## 📈 监控指标

### 关键监控项

| 指标名称 | 阈值 | 告警级别 | 说明 |
|---------|------|---------|------|
| `cache.hit.rate` | < 95% | 警告 | 缓存命中率过低 |
| `cache.renew.success.rate` | < 90% | 警告 | 共享缓存续期成功率 |
| `cache.latency.p95` | > 50ms | 警告 | 缓存查询延迟 |
| `redis.memory.usage` | > 80% | 严重 | Redis 内存使用率 |
| `cache.evict.count` | > 1000/小时 | 警告 | 缓存失效过于频繁 |
| `cache.miss.fallback.db` | > 100/小时 | 严重 | 降级到数据库次数 |

### 监控实现

```java
// CacheMetrics.java
@Component
@RequiredArgsConstructor
public class CacheMetrics {

    private final MeterRegistry meterRegistry;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedRate = 60000)
    public void recordMetrics() {
        // 缓存命中率
        double hitRate = calculateCacheHitRate();
        meterRegistry.gauge("cache.hit.rate", hitRate);

        // Redis 内存使用率
        double memoryUsage = getRedisMemoryUsage();
        meterRegistry.gauge("redis.memory.usage", memoryUsage);

        // 共享缓存续期成功率
        double renewSuccessRate = calculateRenewSuccessRate();
        meterRegistry.gauge("cache.renew.success.rate", renewSuccessRate);
    }

    private double calculateCacheHitRate() {
        // 实现缓存命中率计算逻辑
    }

    private double calculateRenewSuccessRate() {
        // 实现续期成功率计算逻辑
    }
}
```

---

## 🔍 决策理由

### 为什么设备缓存用固定 24 小时（不续期）？

1. **内存释放**
   - 脉冲模式 Day 2-3 沉默期，设备不检查
   - 缓存自然过期，释放内存（10GB → 0）
   - 24 小时覆盖整个脉冲周期

2. **性能考虑**
   - 设备信息相对稳定，不需要频繁更新
   - 每周循环重新加载，保证数据新鲜度

### 为什么共享缓存用滑动 TTL（续期）？

1. **访问模式**
   - 所有设备共享同一份数据
   - 千万设备访问时间分散（0-2h 抖动）
   - 几乎每时每刻都有设备在访问
   - 固定 TTL 无法真正释放内存，反而增加重新加载开销

2. **性能优化**
   - 续期保证热点数据持续缓存
   - 减少数据库查询，性能最优
   - 冷门数据自然过期，释放内存

3. **一致性保证**
   - 主动失效机制保证运营修改立即生效
   - 兜底过期（24h/7d/30d）防止主动失效失败

### 为什么产品信息用 7 天？

1. **变更频率低**
   - 产品信息（硬件定义、基线版本）极少变更
   - 7 天足够保证数据新鲜度
   - 即使有变更，主动失效立即生效

2. **性能最大化**
   - 7 天是最长合理的 TTL
   - 避免频繁查询产品配置
   - 适合 1000 万+ 设备规模

---

## 🚀 实施步骤

### 第一阶段：基础配置
1. 更新 `RedisKeyConstants` 中的分层 TTL 值
2. 实现 TTL 随机化工具类
3. 添加缓存监控指标

### 第二阶段：分层实现
1. 实现设备缓存的固定 TTL（不续期）
2. 实现共享缓存的滑动 TTL（续期）
3. 实现主动失效机制
4. 配置降级策略

### 第三阶段：优化监控
1. 完善监控告警规则
2. 性能调优
3. 容量规划

---

## ⚠️ 注意事项

1. **分层 TTL 策略**
   - 设备缓存：固定 TTL，沉默期释放内存
   - 共享缓存：滑动 TTL，续期保持热点数据
   - 不要混淆两种策略

2. **主动失效必须实现**
   - 没有主动失效，运营修改要等很长时间才能生效
   - 共享缓存的一致性主要靠主动失效，不靠 TTL 过期

3. **注意内存使用**
   - 设备缓存：1000 万设备 × 1KB = 10GB（沉默期释放）
   - 共享缓存：100 产品 × 10KB + 1000 策略 × 5KB ≈ 6MB（可忽略）

4. **监控降级情况**
   - Redis 不可用时自动降级
   - 记录降级次数和影响
   - 及时排查 Redis 问题

5. **续期成功率监控**
   - 共享缓存续期失败会影响性能
   - 需监控续期成功率，若 < 90% 需检查

---

## 📚 参考资料

- Redis 官方文档：https://redis.io/docs/
- Spring Data Redis：https://docs.spring.io/spring-data/redis/docs/current/reference/html/
- Micrometer 监控：https://micrometer.io/
- [Redis 缓存实现计划](../04-guides/redis-cache-implementation-plan.md) - 完整实施计划
- [Redis 缓存标准](../03-standards/redis-cache-standards.md) - 缓存使用规范
