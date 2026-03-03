# Redis 缓存标准与规范

> **版本**: v1.0
> **创建日期**: 2026-02-17
> **适用范围**: FOTA 平台所有 Redis 相关代码

---

## 📋 目录

1. [Redis Key 命名规范](#redis-key-命名规范)
2. [TTL 设置原则](#ttl-设置原则)
3. [数据类型使用规范](#数据类型使用规范)
4. [Bitmap 使用规范](#bitmap-使用规范)
5. [限流实现规范](#限流实现规范)
6. [序列化规范](#序列化规范)
7. [性能优化建议](#性能优化建议)
8. [监控与告警](#监控与告警)

---

## Redis Key 命名规范

### 基本格式

```
fota:{module}:{key}:{identifier}
```

### 命名层级

| 层级 | 说明 | 示例 |
|------|------|------|
| `fota` | 项目前缀（固定） | `fota` |
| `{module}` | 模块名 | `device`, `policy`, `product`, `config`, `quota`, `gray` |
| `{key}` | 功能键 | `active`, `snap`, `limit`, `count` |
| `{identifier}` | 标识符 | IMEI、ID、日期等 |

### 命名示例

```redis
# 设备信息
fota:device:861234567890123

# 策略快照
fota:pol:snap:main:v123

# 活跃度 Bitmap（日期）
fota:active:20260217

# 限流
fota:ratelimit:upgrade:127.0.0.1

# 灰度计数
fota:gray:count:101:20260217

# 临时 BITOP 键
fota:tmp:bitop:union:20260215-20260217

# 分布式锁
fota:lock:device_import:batch_123
```

### 命名原则

1. **统一小写**：所有 key 使用小写字母
2. **使用冒号分隔**：`:` 作为层级分隔符
3. **避免特殊字符**：不使用空格、特殊符号
4. **长度控制**：Key 长度不超过 256 字节（Redis 限制）
5. **语义化**：Key 名称应清晰表达用途

---

## TTL 设置原则

### 分层 TTL 策略

基于**设备缓存沉默期释放内存**和**共享缓存续期保持热点数据**的设计目标，采用分层 TTL 策略：

所有 TTL 必须在 `RedisKeyConstants` 中定义常量：

```java
// ========== 设备缓存（固定TTL，不续期）==========
public static final long DEVICE_CACHE_TTL_SECONDS = 24 * 60 * 60;  // 24小时

// ========== 共享缓存（滑动TTL，续期）==========
public static final long POLICY_CACHE_TTL_SECONDS = 24 * 60 * 60;      // 24小时
public static final long PRODUCT_CACHE_TTL_SECONDS = 7 * 24 * 60 * 60; // 7天
public static final long FIRMWARE_CACHE_TTL_SECONDS = 30 * 24 * 60 * 60; // 30天

// ========== 其他缓存 ==========
public static final long ACTIVE_BITMAP_TTL_SECONDS = 90 * 24 * 60 * 60; // 90天
public static final long RATE_LIMIT_TTL_SECONDS = 60;                 // 60秒
```

### 分层 TTL 配置表

| 缓存类型 | TTL | 续期策略 | 一致性保证 | 原因 |
|---------|-----|---------|-----------|------|
| **设备缓存** | 24小时 | ❌ 不续期 | 被动过期 | 沉默期释放内存（主要内存占用） |
| **策略缓存** | 24小时 | ✅ **续期** | 主动失效 | 热点数据持续缓存 |
| **产品信息** | 7天 | ✅ **续期** | 主动失效 | 产品信息极少变更 |
| **固件版本** | 30天 | ✅ **续期** | 主动失效 | 固件发布后基本不变 |
| **活跃度 Bitmap** | 90天 | ❌ 不续期 | 按日创建 | 用于离线分析 |
| **限流窗口** | 60秒 | ❌ 不续期 | 窗口重置 | 短期限流 |
| **临时键（BITOP）** | 60秒 | ❌ 不续期 | 临时使用 | 临时计算结果 |
| **分布式锁** | 30秒 | ❌ 不续期 | 心跳续期 | 防止死锁 |

### 续期策略详解

#### 设备缓存：固定 TTL（不续期）

```java
// ✅ 设备缓存读取时不续期
public DeviceCache get(String imei) {
    String key = String.format(DEVICE_KEY_TEMPLATE, imei);
    Object cached = redisTemplate.opsForValue().get(key);
    // ❌ 不调用 expire()，让缓存自然过期
    return (DeviceCache) cached;
}
```

**原因**：脉冲模式 Day 2-3 沉默期，设备不检查 → 缓存自然过期 → 释放内存

#### 共享缓存：滑动 TTL（续期）

```java
// ✅ 共享缓存读取时续期
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

**原因**：共享缓存被千万设备频繁访问，续期保证热点数据持续缓存，性能最优

### TTL 设置注意事项

1. **分层配置**：根据数据类型选择合适的 TTL 和续期策略
2. **必须设置 TTL**：除特殊需求外，所有 key 必须设置过期时间
3. **避免 TTL 集中**：大量 key 同时过期会导致 Redis 阻塞
4. **TTL 随机化**：对于批量 key，TTL 应加入随机偏移（±10%）
5. **监控过期率**：定期检查 key 过期情况，避免缓存雪崩
6. **主动失效**：共享缓存必须实现主动失效机制（管理后台修改时立即失效）

---

## 数据类型使用规范

### String 类型

**使用场景**：简单 KV、JSON 对象、计数器

```redis
# 简单 KV
SET fota:device:861234567890123 '{"id":123,"imei":"861234567890123"}' EX 86400

# 计数器
INCR fota:gray:count:101:20260217
```

**注意事项**：
- 使用 `StringRedisTemplate` 操作 String 类型
- 避免 Value 过大（单 Value 不超过 1MB）

### Hash 类型

**使用场景**：对象存储、字段更新频繁

```redis
# 设备属性
HSET fota:device:123:attr model "iPhone 13" osVersion "17.0"
HGET fota:device:123:attr model
```

**注意事项**：
- 适合字段数较少（< 1000）的场景
- 避免 Field 名过长

### List 类型

**使用场景**：消息队列、日志

```redis
# 检查日志
LPUSH fota:log:check:20260217 '{"imei":"123","timestamp":1234567890}'
LTRIM fota:log:check:20260217 0 9999
```

**注意事项**：
- 控制列表长度（定期 TRIM）
- 不用于大数据量存储

### Set 类型

**使用场景**：去重、标签

```redis
# 设备标签
SADD fota:device:123:tags "premium" "beta"
SISMEMBER fota:device:123:tags "premium"
```

**注意事项**：
- 注意内存占用（每个成员约 48 字节）
- 控制集合大小

### Sorted Set 类型

**使用场景**：排行榜、时间线

```redis
# 升级频率排行
ZADD fota:upgrade:rank:20260217 10 "device:123" 5 "device:456"
ZREVRANGE fota:upgrade:rank:20260217 0 9 WITHSCORES
```

**注意事项**：
- 注意 Score 范围（双精度浮点）
- 定期清理过期成员

---

## Bitmap 使用规范

### 基本概念

Bitmap 是基于 String 类型的位数组，每个位可以存储 0 或 1。

**优势**：
- 极高存储效率：1 亿设备 ≈ 12MB
- 支持位运算（AND、OR、XOR、NOT）

### 使用场景

#### 1. 设备活跃度跟踪

```redis
# 标记设备活跃（设备 ID 作为偏移量）
SETBIT fota:active:20260217 123456 1

# 检查设备是否活跃
GETBIT fota:active:20260217 123456

# 统计活跃设备数
BITCOUNT fota:active:20260217
```

#### 2. 多日活跃度统计

```redis
# 统计 3 日活跃设备（并集）
BITOP OR fota:tmp:active:3days fota:active:20260215 fota:active:20260216 fota:active:20260217
BITCOUNT fota:tmp:active:3days

# 清理临时键
EXPIRE fota:tmp:active:3days 60
```

#### 3. 沉默设备识别

```redis
# 统计连续 7 天未活跃设备
# 假设已有 fota:active:20260210 ~ fota:active:20260217
BITOP OR fota:tmp:active:week fota:active:20260210 fota:active:20260211 fota:active:20260212 fota:active:20260213 fota:active:20260214 fota:active:20260215 fota:active:20260216 fota:active:20260217
# 总设备数 - 活跃设备数 = 沉默设备数
```

### 最佳实践

1. **设备 ID 作为偏移量**
   - 使用数据库自增 ID（BIGSERIAL）
   - 避免使用 IMEI 等字符串（需要哈希映射）

2. **按日期分片**
   - 每日一个 key：`fota:active:yyyyMMdd`
   - 便于过期管理和批量操作

3. **临时键 TTL**
   - BITOP 产生的临时键必须设置短 TTL（60s）
   - 避免内存泄漏

4. **Bitmap 大小监控**
   - 定期检查 bitmap 占用内存
   - 单 bitmap 不超过 10MB（约 8000 万设备）

5. **异步统计**
   - BITOP 操作耗时，应异步执行
   - 避免在请求链路中同步调用

### Java 实现

```java
@Service
@RequiredArgsConstructor
public class RedisDeviceActivityBitmapRepository implements DeviceActivityBitmapRepository {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void markActive(LocalDate day, long deviceId) {
        String key = String.format(RedisKeyConstants.ACTIVE_BITMAP_KEY_TEMPLATE,
            day.format(DateTimeFormatter.BASIC_ISO_DATE));
        redisTemplate.opsForValue().setBit(key, deviceId, true);
    }

    @Override
    public boolean isActive(LocalDate day, long deviceId) {
        String key = String.format(RedisKeyConstants.ACTIVE_BITMAP_KEY_TEMPLATE,
            day.format(DateTimeFormatter.BASIC_ISO_DATE));
        return Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(key, deviceId));
    }
}
```

---

## 限流实现规范

### 限流算法选择

| 算法 | 适用场景 | 实现复杂度 | 推荐度 |
|------|---------|-----------|--------|
| 固定窗口 | 简单限流 | 低 | ⭐⭐⭐⭐⭐ |
| 滑动窗口 | 精确限流 | 中 | ⭐⭐⭐⭐ |
| 漏桶 | 流量整形 | 中 | ⭐⭐⭐ |
| 令牌桶 | 突发流量 | 中 | ⭐⭐⭐⭐ |

### 固定窗口实现（推荐）

**Lua 脚本**（保证原子性）：

```lua
-- fixed_window.lua
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])

local current = tonumber(redis.call('INCR', key))
if current == 1 then
    redis.call('EXPIRE', key, ttl)
end

if current > limit then
    return {0, limit, ttl}  -- {allowed, remaining, reset_after}
else
    return {1, limit - current, ttl}
end
```

**Java 实现**：

```java
@Service
@RequiredArgsConstructor
public class RedisDeviceRateLimiter implements DeviceRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> rateLimitScript;

    @Override
    public RateLimitDecision allow(String key, int maxRequests, Duration window) {
        List<Long> result = redisTemplate.execute(
            rateLimitScript,
            Collections.singletonList(key),
            String.valueOf(maxRequests),
            String.valueOf(window.getSeconds())
        );

        boolean allowed = result.get(0) == 1;
        int remaining = result.get(1).intValue();
        long resetAt = result.get(2).longValue();

        return RateLimitDecision.builder()
            .allowed(allowed)
            .remaining(remaining)
            .resetAtEpochSecond(resetAt)
            .build();
    }
}
```

### 限流 Key 设计

```redis
# 设备检查限流（IMEI + 分钟）
fota:ratelimit:check:861234567890123:202602171030

# IP 限流
fota:ratelimit:ip:127.0.0.1

# LastSeen 限频（每小时最多更新一次）
fota:ratelimit:lastseen:861234567890123
```

### 限流配置建议

| 限流类型 | 限制 | 窗口 | Key 格式 |
|---------|------|------|---------|
| 设备检查 | 10 次 | 1 分钟 | `fota:ratelimit:check:{imei}:{minute}` |
| 设备上报 | 30 次 | 1 分钟 | `fota:ratelimit:report:{imei}:{minute}` |
| IP 限流 | 100 次 | 1 分钟 | `fota:ratelimit:ip:{ip}:{minute}` |
| LastSeen 更新 | 1 次 | 1 小时 | `fota:ratelimit:lastseen:{imei}:{hour}` |

### 注意事项

1. **使用 Lua 保证原子性**：避免 INCR + EXPIRE 不是原子操作
2. **Key 包含时间窗口**：避免边界问题（如 `:{minute}`）
3. **限流降级**：Redis 不可用时的降级策略
4. **限流日志**：记录限流事件，便于分析

---

## 序列化规范

### 序列化器选择

| 场景 | 序列化器 | 优点 | 缺点 |
|------|---------|------|------|
| 简单对象 | StringRedisTemplate | 高效、可读 | 需手动序列化 |
| 复杂对象 | JSON（Jackson） | 可读性好 | 有性能开销 |
| 二进制数据 | ByteArray | 高效 | 不可读 |

### 推荐配置

```java
@Configuration
public class RedisConfiguration {

    // 1. String 序列化器（推荐用于 Bitmap、限流）
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        return template;
    }

    // 2. JSON 序列化器（用于复杂对象）
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 使用 String 序列化器作为 key 序列化器
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 使用 JSON 序列化器作为 value 序列化器
        GenericJackson2JsonRedisSerializer jsonSerializer = createJsonSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    private GenericJackson2JsonRedisSerializer createJsonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();

        // ⚠️ 注意：禁用 default typing，避免安全风险
        // objectMapper.activateDefaultTyping(...);

        // 配置序列化行为
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}
```

### 序列化注意事项

1. **优先使用 StringRedisTemplate**：Bitmap、限流等操作必须使用
2. **避免 default typing**：存在安全风险和兼容性问题
3. **版本兼容性**：DTO 变更时考虑反序列化兼容
4. **大对象拆分**：避免序列化大对象（> 10KB）

---

## 性能优化建议

### 1. 连接池配置

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 20      # 最大连接数
          max-idle: 10        # 最大空闲连接
          min-idle: 5         # 最小空闲连接
          max-wait: 3s        # 最大等待时间
        shutdown-timeout: 3s  # 关闭超时
      connect-timeout: 3s     # 连接超时
      timeout: 3s             # 命令执行超时
```

### 2. Pipeline 批量操作

```java
// 批量标记设备活跃
public void markActiveBatch(LocalDate day, List<Long> deviceIds) {
    String key = String.format(ACTIVE_BITMAP_KEY_TEMPLATE,
        day.format(DateTimeFormatter.BASIC_ISO_DATE));

    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
        for (Long deviceId : deviceIds) {
            connection.setBit(key.getBytes(), deviceId, true);
        }
        return null;
    });
}
```

### 3. 避免 Big Key

| Big Key 类型 | 阈值 | 解决方案 |
|-------------|------|---------|
| String | > 10KB | 拆分为多个 key |
| Hash | > 5000 字段 | 按业务分片 |
| List | > 10000 元素 | 定期 trim 或分片 |
| Set | > 10000 元素 | 按业务拆分 |
| Bitmap | > 10MB | 按时间或业务分片 |

### 4. 避免 O(N) 命令

```bash
# ❌ 避免
KEYS fota:device:*           # 阻塞 Redis
HGETALL fota:device:123:attr # Hash 字段过多时慢

# ✅ 推荐
SCAN 0 MATCH fota:device:* COUNT 100
HMGET fota:device:123:attr model osVersion
```

### 5. 合理使用 EXPIRE

```java
// ✅ 推荐：原子操作
redisTemplate.opsForValue().set(key, value, Duration.ofHours(1));

// ❌ 避免：非原子（可能 key 不存在但设置了 EXPIRE）
redisTemplate.opsForValue().set(key, value);
redisTemplate.expire(key, Duration.ofHours(1));
```

---

## 监控与告警

### 关键监控指标

| 指标 | 阈值 | 告警级别 | 说明 |
|------|------|---------|------|
| Redis 连接池使用率 | > 80% | 警告 | 可能连接池耗尽 |
| Redis 命令平均耗时 | > 10ms | 警告 | 性能下降 |
| Redis 慢查询 | > 100 | 警告 | 需优化查询 |
| Redis 内存使用率 | > 80% | 严重 | 可能 OOM |
| Bitmap 写入失败率 | > 1% | 严重 | 缓存不可用 |
| 限流命中率 | > 10% | 警告 | 可能被攻击 |

### 监控实现

```java
@Component
@RequiredArgsConstructor
public class RedisMetrics {

    private final MeterRegistry meterRegistry;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedRate = 60000)
    public void recordMetrics() {
        // 记录连接池指标
        // ...

        // 记录内存使用
        // ...

        // 记录慢查询
        // ...
    }
}
```

### 日志记录

```java
@Slf4j
@Service
public class RedisDeviceActivityBitmapRepository {

    public void markActive(LocalDate day, long deviceId) {
        try {
            // Redis 操作
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed: day={}, deviceId={}", day, deviceId, e);
            // 降级处理
        } catch (RedisException e) {
            log.warn("Redis operation failed: day={}, deviceId={}", day, deviceId, e);
        }
    }
}
```

---

## 参考资料

- Redis 官方文档：https://redis.io/docs/
- Spring Data Redis：https://docs.spring.io/spring-data/redis/docs/current/reference/html/
- Lettuce 客户端：https://lettuce.io/
- Redis Bitmap 最佳实践：https://redis.io/docs/data-types/bitmaps/
