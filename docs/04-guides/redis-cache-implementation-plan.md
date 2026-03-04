# Redis 策略缓存实现方案

> **版本**: v1.1
> **设计日期**: 2026-03-03
> **更新日期**: 2026-03-03
> **状态**: 待实施

---

## 1. 背景与目标

### 1.1 背景

Sprint 3 核心链路开发已接近完成（85%），但 Redis 策略缓存层尚未实现，当前直接查询数据库。

### 1.2 设备检查周期方案（关键约束）

> ⚠️ **重要**: 根据最新业务需求，设备检查周期存在两种模式，需针对性设计 TTL 策略

#### 方案1: 脉冲式检查模式（推荐）

| 周期        | 检查行为 | 间隔 | 抖动 |
|-----------|---------|------|------|
| **Day 1** | ✅ 检查 | 2小时一次 | 0-2小时随机抖动 |
| **Day 2-3** | ❌ 不检查 | - | - |
| **Day 4** | ✅ 检查 | 2小时一次 | 0-2小时随机抖动 |
| **Day 5-6** | ❌ 不检查 | - | - |
| **重复**    | 循环以上模式 | - | - |

**特点**:
- 实际检查间隔: **2小时（含抖动0-4小时）**
- 沉默期: **48小时不检查**
- 周期性: **每周循环**

#### 方案2: 稳定检查模式

| 参数 | 值 |
|------|-----|
| **检查间隔** | 6小时一次 |
| **抖动** | 无 |
| **持续性** | 持续检查 |

#### 服务端动态控制

- 服务端可通过策略下发 `check_interval` 参数
- 可根据产品、设备标签、版本等条件动态调整
- **未来可扩展**: 支持更复杂的检查策略

### 1.3 其他关键约束

| 约束 | 说明 |
|------|------|
| **部署架构** | 2台机器，每台可能部署多实例 |
| **性能目标** | P99 < 50ms, QPS > 10,000 |
| **数据一致性** | 策略变更需快速生效 |

### 1.4 目标

- 减少数据库查询：5-7次 → 1-2次/请求
- 响应时间：30-60ms → 10-20ms
- 缓存命中率：> 95%（针对实际检查模式优化）

---

## 2. 核心决策

### 2.1 不使用 Caffeine 本地缓存

| 因素 | 分析 |
|------|------|
| **检测周期长** | 6小时+ 的检测周期意味着同一设备短时间内重复请求概率极低 |
| **多实例问题** | 2台机器 × 多实例 = 4+ 实例，Caffeine 会导致数据不一致 |
| **一致性方案复杂** | 需要 Redis Pub/Sub 通知所有实例失效，增加复杂度 |
| **收益有限** | 本地缓存命中率预期 <10% |
| **Redis 足够快** | Redis 单次查询 <1ms，已经满足性能需求 |

**结论**: 仅使用 Redis 缓存，放弃 Caffeine 本地缓存。

---

## 3. Redis 缓存键设计

> 详细设计见：[Redis缓存键设计方案.md](../03-design/Redis%E7%BC%93%E5%AD%98%E9%94%AE%E8%AE%BE%E8%AE%A1%E6%96%B9%E6%A1%88.md)

### 3.1 键命名规范

```
fota:{module}:{subtype}:{identifiers}
```

### 3.2 核心键设计

| 数据类型 | 键模板 | 示例 | 数据结构 |
|----------|--------|------|----------|
| **产品策略列表** | `fota:cache:list:product:policy:{productId}:{type}` | `fota:cache:list:product:policy:1001:prod` | Set |
| **单个策略详情** | `fota:policy:{policyId}` | `fota:policy:101` | String (JSON) |
| **产品信息** | `fota:product:{productId}` | `fota:product:1001` | String (JSON) |
| **产品型号索引** | `fota:cache:product:model:{model}` | `fota:cache:product:model:M476` | String |
| **固件详情** | `fota:firmware:{versionId}` | `fota:firmware:201` | String (JSON) |

### 3.3 缓存索引键（用于批量失效）

| 索引类型 | 键模板 | 数据结构 |
|----------|--------|----------|
| **产品缓存索引** | `fota:cache:index:product:{productId}` | Set |
| **策略缓存索引** | `fota:cache:index:policy:{policyId}` | Set |
| **固件缓存索引** | `fota:cache:index:firmware:{versionId}` | Set |

### 3.4 索引关联示例

```
fota:cache:index:product:1001 (SET)
    ├── fota:product:1001
    ├── fota:cache:list:product:policy:1001:all
    └── fota:cache:list:product:policy:1001:prod
```

---

## 4. TTL 策略（分层设计）

> 详细设计见：[ttl-strategy-design.md](../03-design/ttl-strategy-design.md)

### 4.1 TTL 设计原则

基于两种检查模式的特点和缓存类型，采用**分层 TTL 策略**：

1. **设备缓存**：固定 TTL（不续期）- 沉默期释放内存
2. **共享缓存**：滑动 TTL（续期）+ 主动失效 - 热点数据持续缓存
3. **分层配置**：不同类型数据采用不同 TTL
4. **兜底机制**：较长 TTL 防止主动失效失败

### 4.2 分层 TTL 配置

| 数据类型 | TTL | 续期策略 | 一致性保证 | 理由 |
|----------|-----|---------|-----------|------|
| **设备缓存** | **24小时** | ❌ 不续期 | 被动过期 | 每个设备独立，沉默期释放内存 |
| **策略缓存** | **24小时** | ✅ **续期** | 主动失效 | 热点数据持续缓存，24h兜底 |
| **产品信息** | **7天** | ✅ **续期** | 主动失效 | 产品信息极少变更 |
| **固件版本** | **30天** | ✅ **续期** | 主动失效 | 固件发布后基本不变 |

### 4.3 为什么共享缓存采用滑动TTL（续期）？

#### 核心洞察

共享缓存（策略、产品、固件）由于千万级设备访问时间分散，**几乎不会自然过期**：

```
脉冲模式 Day 1（2h间隔 + 0-2h抖动）

00:00 - 设备A检查 → 加载策略缓存（TTL=24h）
01:30 - 设备B检查 → 命中缓存
...
23:30 - 设备X检查 → 命中缓存（还有30分钟过期）
24:01 - 设备Y检查 → 缓存刚过期，重新加载

结果：固定TTL无法真正释放内存，反而增加了重新加载的开销
```

#### 方案对比

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| **固定TTL** | 有明确的TTL上限 | 热点数据仍需重新加载 | ❌ 不适合共享缓存 |
| **滑动TTL（续期）** | 热点数据持续缓存 | 需要续期操作 | ✅ **推荐** |
| **不设TTL** | 最简化逻辑 | 完全依赖主动失效 | ⚠️ 风险较高 |

#### 最终策略：滑动TTL + 主动失效 + 兜底过期

```
┌─────────────────────────────────────────────────────────┐
│              共享缓存的一致性保证机制                     │
├─────────────────────────────────────────────────────────┤
│ 1. 滑动TTL（续期）                                       │
│    - 热点数据持续保持缓存                                │
│    - 冷门数据自然过期                                    │
├─────────────────────────────────────────────────────────┤
│ 2. 主动失效（主要保证）                                  │
│    - 管理后台修改时立即失效                              │
│    - 运营修改立即生效                                    │
├─────────────────────────────────────────────────────────┤
│ 3. 兜底过期（保险机制）                                  │
│    - 较长TTL（24h/7d/30d）                              │
│    - 防止主动失效失败导致数据长时间过期                  │
└─────────────────────────────────────────────────────────┘
```

### 4.4 TTL 计算逻辑

#### 4.4.1 设备缓存：固定 TTL（不续期）

```java
// 设备缓存：固定24小时，不续期
public static final long DEVICE_CACHE_TTL_SECONDS = 24 * 60 * 60;

@Override
public DeviceCache get(String imei) {
    String key = String.format(DEVICE_KEY_TEMPLATE, imei);
    Object cached = redisTemplate.opsForValue().get(key);
    // ❌ 不续期，让缓存自然过期（沉默期释放内存）
    return (DeviceCache) cached;
}
```

#### 4.4.2 共享缓存：滑动 TTL（续期）

```java
// 策略缓存：24小时，续期
public static final long POLICY_CACHE_TTL_SECONDS = 24 * 60 * 60;

public List<UpgradePolicy> getProductPolicies(Long productId, boolean includeTest) {
    String key = String.format(PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, includeTest ? "all" : "prod");
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

### 4.5 TTL 随机化（防缓存雪崩）

```java
/**
 * 10% 随机偏移，避免同一时间大量缓存失效
 * 
 * @param baseTtl 基础 TTL（秒）
 * @return 随机化后的 TTL（秒）
 */
public static long getRandomizedTtl(long baseTtl) {
    double randomFactor = 0.1; // ±10%
    long randomOffset = (long) (baseTtl * randomFactor * (Math.random() - 0.5));
    return Math.max(1, baseTtl + randomOffset);
}

// 使用示例
long baseTtl = POLICY_CACHE_TTL_SECONDS; // 24小时
long actualTtl = getRandomizedTtl(baseTtl); // 21.6h ~ 26.4h
```

### 4.6 检查模式对比分析

| 检查模式 | 检查间隔 | 最大抖动 | 设备缓存 TTL | 共享缓存 TTL | 缓存命中率预期 |
|---------|---------|---------|------------|------------|--------------|
| **脉冲模式** | 2小时 | 0-2小时 | 24h（不续期） | 24h（续期） | > 99% |
| **稳定模式** | 6小时 | 无 | 24h（不续期） | 24h（续期） | > 99% |
| **服务端控制** | 可变 | 取决于配置 | 24h（不续期） | 24h（续期） | > 99% |

**结论**: 滑动TTL + 主动失效是最优方案，能同时满足性能与一致性需求。

---

## 5. 方案完整性评估

### 5.1 针对脉冲模式的适用性分析

| 需求 | 当前方案 | 评估 |
|------|---------|------|
| **Day 1 检查窗口覆盖** | 24小时 TTL 覆盖 2 个完整检查周期 | ✅ **充分** |
| **Day 2-3 沉默期处理** | 设备缓存自然过期，共享缓存续期保持 | ✅ **合理** |
| **周期性重复** | 设备缓存每周重建，共享缓存持续保持 | ✅ **符合预期** |
| **缓存命中率** | Day 1: 99%+ | ✅ **达标** |

### 5.2 针对稳定模式的适用性分析

| 需求 | 当前方案 | 评估 |
|------|---------|------|
| **6小时检查间隔** | 24小时 TTL 覆盖 4 个检查周期 | ✅ **充分** |
| **无抖动场景** | TTL 有 10% 随机化 | ✅ **合理** |
| **缓存命中率** | 预期 > 99% | ✅ **优秀** |

### 5.3 齈对服务端动态控制的扩展性

| 功能 | 当前支持 | 未来扩展 | 优先级 |
|------|---------|---------|--------|
| **分层 TTL 配置** | ✅ 已实现 | - | P0 |
| **共享缓存续期** | ✅ 已实现 | - | P0 |
| **主动失效机制** | ✅ 已实现 | - | P0 |

### 5.4 潜在问题与解决方案

#### 问题 1: TTL 是否应该更长？

| 方案 | TTL | 优点 | 缺点 | 结论 |
|------|-----|------|------|------|
| **当前方案（分层）** | 24h/7d/30d | 分层优化 | 配置复杂度稍高 | ✅ **推荐** |
| **统一 24h** | 24小时 | 配置简单 | 无法区分数据重要性 | ❌ 不推荐 |
| **延长到 7天** | 7天 | 缓存命中率高 | 策略变更延迟风险 | ❌ 不推荐 |

#### 问题 2: 脉冲模式 Day 2 的缓存处理

**当前方案**: 设备缓存24小时后自然过期，共享缓存续期保持

**优点**:
- ✅ 设备缓存沉默期自然过期，释放内存
- ✅ 共享缓存续期保持，热点数据持续缓存
- ✅ 主动失效保证一致性

**结论**: 当前方案合理，分层策略优化性能

#### 问题 3: 多实例部署的缓存一致性

| 方案 | 实现复杂度 | 一致性 | 性能 | 结论 |
|------|-----------|--------|------|------|
| **仅 Redis（当前）** | 低 | 最终一致 | 高 | ✅ **推荐** |
| **Redis + Caffeine** | 高 | 强一致难保证 | 最高 | ❌ 过度设计 |
| **Redis + Pub/Sub 失效** | 中 | 较强一致 | 中 | ❌ 收益有限 |

### 5.5 方案合理性总结

#### ✅ 优点

1. **分层 TTL 设计合理**: 24h/7d/30d 针对不同数据类型优化
2. **共享缓存续期策略**: 热点数据持续缓存，性能最优
3. **扩展性好**: 支持未来动态 TTL 调整
4. **实现简单**: 仅用 Redis，避免多级缓存复杂性
5. **容错性强**: 主动失效 + 兜底过期双重保障

#### ⚠️ 注意事项

1. **脉冲模式**: Day 2-3 沉默期设备缓存自然过期，共享缓存续期保持（预期行为）
2. **续期逻辑**: 需确保共享缓存读取时正确续期
3. **监控告警**: 需监控缓存命中率和续期成功率，若 < 95% 鯺调整

#### 🎯 最终结论

**当前方案完整且合理，推荐实施。** 分层 TTL + 共享缓存续期是最优方案。

---

## 6. 缓存架构

```
┌─────────────────────────────────────────────────────────┐
│                    UpgradeCheckService                   │
└─────────────────────────┬───────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                  Redis 缓存层（分层策略）                 │
│                                                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │ 策略缓存    │  │ 产品缓存    │  │ 版本缓存    │     │
│  │ TTL: 24h    │  │ TTL: 7d     │  │ TTL: 30d    │     │
│  │ (滑动续期)  │  │ (滑动续期)  │  │ (滑动续期)  │     │
│  └─────────────┘  └─────────────┘  └─────────────┘     │
│                                                         │
│  ┌─────────────┐  ┌─────────────────────────────┐      │
│  │ 设备缓存    │  │ 缓存索引 (SET)               │      │
│  │ TTL: 24h    │  │ 支持批量失效                 │      │
│  │ (固定不续期)│  └─────────────────────────────┘      │
│  └─────────────┘                                        │
│                                                         │
│  一致性保证: 主动失效 + 兜底过期                         │
│  命中率预期: 99%+ (共享缓存续期保持)                     │
└─────────────────────────┬───────────────────────────────┘
                          │ 未命中 (约1%)
                          ▼
┌─────────────────────────────────────────────────────────┐
│                  PostgreSQL 数据库                       │
│                  降级方案                                │
└─────────────────────────────────────────────────────────┘
```

---

## 7. 实施计划

### 7.1 Phase 1: 更新键常量和 TTL（P0 - 立即实施）

**修改文件**: `fota-framework-cache/.../RedisKeyConstants.java`

```java
// 新增键常量
public static final String PRODUCT_POLICY_LIST_KEY_TEMPLATE = "fota:cache:list:product:policy:%s:%s";
public static final String PRODUCT_MODEL_INDEX_KEY_TEMPLATE = "fota:cache:product:model:%s";
public static final String PRODUCT_CACHE_INDEX_KEY_TEMPLATE = "fota:cache:index:product:%s";
public static final String POLICY_CACHE_INDEX_KEY_TEMPLATE = "fota:cache:index:policy:%s";
public static final String FIRMWARE_CACHE_INDEX_KEY_TEMPLATE = "fota:cache:index:firmware:%s";

// 分层 TTL（设备缓存固定，共享缓存滑动续期）
public static final long DEVICE_CACHE_TTL_SECONDS = 24 * 60 * 60;      // 24小时（固定，不续期）
public static final long POLICY_CACHE_TTL_SECONDS = 24 * 60 * 60;       // 24小时（滑动，续期）
public static final long PRODUCT_CACHE_TTL_SECONDS = 7 * 24 * 60 * 60;   // 7天（滑动，续期）
public static final long FIRMWARE_CACHE_TTL_SECONDS = 30 * 24 * 60 * 60; // 30天（滑动，续期）
public static final long CACHE_INDEX_TTL_SECONDS = 24 * 60 * 60;        // 24小时（与策略一致）
```

**验证**:
- [ ] TTL 常量已更新
- [ ] 单元测试通过
- [ ] 本地启动验证

### 7.2 Phase 2: 实现策略缓存（P0 - 本周完成）

**新建文件**:

| 文件 | 说明 |
|------|------|
| `PolicyCacheRepository.java` | 策略缓存接口 |
| `RedisPolicyCacheRepository.java` | Redis 实现 |
| `CacheIndexService.java` | 缓存索引服务 |
| `RandomizedTtlUtil.java` | TTL 随机化工具 |

**修改文件**:

| 文件 | 说明 |
|------|------|
| `UpgradePolicyRepositoryImpl.java` | 集成缓存层 |
| `PolicyCacheEvictListener.java` | 策略变更监听器 |

**关键代码示例**:

```java
// RandomizedTtlUtil.java
public class RandomizedTtlUtil {
    private static final double RANDOM_FACTOR = 0.1; // ±10%
    
    public static long getRandomizedTtl(long baseTtl) {
        long randomOffset = (long) (baseTtl * RANDOM_FACTOR * (Math.random() - 0.5));
        return Math.max(1, baseTtl + randomOffset);
    }
}

// RedisPolicyCacheRepository.java
@Service
@RequiredArgsConstructor
public class RedisPolicyCacheRepository {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    public List<UpgradePolicy> getProductPolicies(Long productId, boolean includeTest) {
        String type = includeTest ? "all" : "prod";
        String key = String.format(PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, type);
        
        String json = redisTemplate.opsForValue().get(key);
        if (json != null) {
            return deserializePolicies(json);
        }
        
        return null; // 缓存未命中
    }
    
    public void cacheProductPolicies(Long productId, boolean includeTest, List<UpgradePolicy> policies) {
        String type = includeTest ? "all" : "prod";
        String key = String.format(PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, type);
        String json = serializePolicies(policies);
        
        long ttl = getRandomizedTtl(POLICY_CACHE_TTL_SECONDS);
        redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttl));
        
        // 更新缓存索引
        updateCacheIndex(productId, key);
    }
}
```

### 7.3 Phase 3: 实现产品信息缓存（P1 - 下周）

**修改文件**: `ProductRepositoryImpl.java` - 添加 Redis 缓存

### 7.4 Phase 4: 添加监控和降级（P1 - 下周）

**新建文件**:

| 文件 | 说明 |
|------|------|
| `CacheFallbackService.java` | 降级服务 |
| `CacheWarmupService.java` | 缓存预热 |
| `CacheMetrics.java` | 监控指标 |

### 7.5 Phase 5: 动态 TTL 支持（P2 - 未来迭代）

**新建文件**:

| 文件 | 说明 |
|------|------|
| `DynamicTtlProvider.java` | 动态 TTL 计算服务 |

**修改文件**:

| 文件 | 说明 |
|------|------|
| `UpgradeResponseBuilder.java` | 提取 check_interval 逻辑 |
| `FotaCacheProperties.java` | 添加动态 TTL 配置 |
| `RedisPolicyCacheRepository.java` | 集成动态 TTL |

**配置示例**:

```yaml
# application.yml
fota:
  cache:
    dynamic-ttl:
      enabled: false  # 默认关闭，使用静态 TTL
      min-ttl: 14400  # 最小 TTL: 4小时
      max-ttl: 86400  # 最大 TTL: 24小时
      jitter-factor: 2.0  # 抖动系数（针对脉冲模式）
      safety-factor: 2    # 安全系数（覆盖N个检查周期）
```

---

## 8. 主动失效机制（关键特性）

> ⚠️ **重要**: 管理后台的任何修改必须立即失效缓存，否则运营修改要等 8 小时才能生效

### 8.1 失效场景全景图

```
┌─────────────────────────────────────────────────────────┐
│              管理后台操作 → 缓存失效映射                   │
├─────────────────────────────────────────────────────────┤
│ 产品修改                                                 │
│   ├─ 产品基本信息变更                                    │
│   │   └─ 失效: 产品缓存 + 相关策略缓存                   │
│   ├─ 产品状态变更（启用/停用）                           │
│   │   └─ 失效: 产品缓存 + 所有策略缓存 + 设备缓存        │
│   └─ 产品型号变更                                        │
│       └─ 失效: 产品型号索引 + 产品缓存                   │
├─────────────────────────────────────────────────────────┤
│ 策略修改                                                 │
│   ├─ 策略创建/更新/删除                                  │
│   │   └─ 失效: 策略缓存 + 产品策略列表缓存               │
│   ├─ 策略灰度比例调整                                    │
│   │   └─ 失效: 策略缓存（立即生效）                      │
│   ├─ 策略时间窗口调整                                    │
│   │   └─ 失效: 策略缓存（立即生效）                      │
│   └─ 策略熔断/停止                                       │
│       └─ 失效: 策略缓存 + 产品策略列表缓存               │
├─────────────────────────────────────────────────────────┤
│ 固件修改                                                 │
│   ├─ 固件版本上传/发布                                   │
│   │   └─ 失效: 固件缓存 + 相关策略缓存                 │
│   ├─ 固件标签变更（stable/beta等）                       │
│   │   └─ 失效: 固件缓存                   │
│   └─ 固件状态变更（READY/DISABLED等）                    │
│       └─ 失效: 固件缓存 + 相关策略缓存                   │
├─────────────────────────────────────────────────────────┤
│ 设备修改                                                 │
│   ├─ 设备信息更新（版本、标签等）                        │
│   │   └─ 失效: 设备缓存                                  │
│   ├─ 设备批量导入                                        │
│       └─ 失效: 批量设备缓存                              │
│   └─ 设备删除                                            │
│       └─ 失效: 设备缓存                                  │
└─────────────────────────────────────────────────────────┘
```

### 8.2 失效策略详解

#### 8.2.1 产品修改失效

| 操作 | 失效的缓存键 | 失效方式 |
|------|------------|---------|
| **产品基本信息修改** | `fota:product:{productId}` | 直接删除 |
| | `fota:cache:list:product:policy:{productId}:all` | 通过索引批量删除 |
| | `fota:cache:list:product:policy:{productId}:prod` | 通过索引批量删除 |
| **产品状态变更** | `fota:product:{productId}` | 直接删除 |
| | `fota:cache:index:product:{productId}` | 获取所有关联键并删除 |
| | `fota:device:*` (该产品的设备) | 按需删除（可选） |
| **产品型号变更** | `fota:cache:product:model:{oldModel}` | 删除旧索引 |
| | `fota:cache:product:model:{newModel}` | 删除新索引（如果存在） |

**实现示例**：

```java
@Service
@RequiredArgsConstructor
public class ProductCacheInvalidator {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheIndexService cacheIndexService;
    
    /**
     * 产品修改后失效所有相关缓存
     */
    public void invalidateOnProductChange(Long productId) {
        // 1. 删除产品基本信息缓存
        String productKey = String.format(PRODUCT_KEY_TEMPLATE, productId);
        redisTemplate.delete(productKey);
        
        // 2. 通过索引删除所有关联的策略缓存
        cacheIndexService.invalidateProductPolicyCache(productId);
        
        log.info("产品缓存已失效: productId={}", productId);
    }
    
    /**
     * 产品状态变更（更强力的失效）
     */
    public void invalidateOnProductStatusChange(Long productId) {
        // 1. 常规失效
        invalidateOnProductChange(productId);
        
        // 2. 可选：失效该产品的所有设备缓存（如果设备缓存包含产品信息）
        // 注意：这可能导致大量缓存失效，谨慎使用
        // deviceCacheRepository.evictByProduct(productId);
        
        log.warn("产品状态变更，缓存已强力失效: productId={}", productId);
    }
}
```

#### 8.2.2 策略修改失效

| 操作 | 失效的缓存键 | 失效方式 |
|------|------------|---------|
| **策略创建/更新/删除** | `fota:policy:{policyId}` | 直接删除 |
| | `fota:cache:list:product:policy:{productId}:all` | 删除产品策略列表 |
| | `fota:cache:list:product:policy:{productId}:prod` | 删除产品策略列表 |
| **策略灰度调整** | `fota:policy:{policyId}` | 直接删除（立即生效） |
| | `fota:cache:list:product:policy:{productId}:*` | 删除产品策略列表 |

**实现示例**：

```java
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
        
        // 2. 删除产品的策略列表缓存（all 和 prod 两种）
        String allPoliciesKey = String.format(PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, "all");
        String prodPoliciesKey = String.format(PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, "prod");
        redisTemplate.delete(List.of(allPoliciesKey, prodPoliciesKey));
        
        log.info("策略缓存已失效: productId={}, policyId={}", productId, policyId);
    }
    
    /**
     * 策略批量修改（批量失效）
     */
    public void invalidateOnBatchPolicyChange(Long productId) {
        // 删除产品所有策略相关缓存
        String allPoliciesKey = String.format(PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, "all");
        String prodPoliciesKey = String.format(PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, "prod");
        redisTemplate.delete(List.of(allPoliciesKey, prodPoliciesKey));
        
        log.warn("产品所有策略缓存已失效: productId={}", productId);
    }
}
```

#### 8.2.3 固件修改失效

| 操作 | 失效的缓存键 | 失效方式 |
|------|------------|---------|
| **固件版本发布** | `fota:firmware:{versionId}` | 直接删除 |

**实现示例**：

```java
@Service
@RequiredArgsConstructor
public class FirmwareCacheInvalidator {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 固件发布后失效缓存
     */
    public void invalidateOnFirmwarePublish(Long productId, Long versionId) {
        // 1. 删除固件详情
        String firmwareKey = String.format(FIRMWARE_KEY_TEMPLATE, versionId);
        redisTemplate.delete(firmwareKey);
        
        log.info("固件缓存已失效: productId={}, versionId={}", productId, versionId);
    }
}
```

#### 8.2.4 设备修改失效

| 操作 | 失效的缓存键 | 失效方式 |
|------|------------|---------|
| **设备信息更新** | `fota:device:{imei}` | 直接删除 |
| **设备批量导入** | `fota:device:{imei1}`, `fota:device:{imei2}`, ... | 批量删除 |
| **设备删除** | `fota:device:{imei}` | 直接删除 |

**实现示例**：

```java
// 已在 RedisDeviceCacheRepository 中实现
@Override
public void evict(String imei) {
    String key = String.format(DEVICE_KEY_TEMPLATE, imei);
    redisTemplate.delete(key);
    log.debug("设备缓存已删除: imei={}", imei);
}

@Override
public void evictBatch(String[] imeis) {
    String[] keys = Arrays.stream(imeis)
        .map(imei -> String.format(DEVICE_KEY_TEMPLATE, imei))
        .toArray(String[]::new);
    redisTemplate.delete(List.of(keys));
    log.info("批量删除设备缓存: count={}", imeis.length);
}
```

### 8.3 事件驱动的失效机制

使用 Spring Event 解耦，确保业务代码不直接依赖缓存逻辑：

```java
// ==================== 事件定义 ====================

public record ProductChangedEvent(Long productId, ChangeType changeType) {}
public record PolicyChangedEvent(Long productId, Long policyId, ChangeType changeType) {}
public record FirmwareChangedEvent(Long productId, Long versionId, String tag, ChangeType changeType) {}
public record DeviceChangedEvent(String imei, ChangeType changeType) {}

public enum ChangeType {
    CREATED, UPDATED, DELETED, STATUS_CHANGED, BATCH_IMPORTED
}

// ==================== 事件监听器 ====================

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheInvalidationListener {
    
    private final ProductCacheInvalidator productCacheInvalidator;
    private final PolicyCacheInvalidator policyCacheInvalidator;
    private final FirmwareCacheInvalidator firmwareCacheInvalidator;
    private final DeviceCacheRepository deviceCacheRepository;
    
    @EventListener
    @Async  // 异步执行，不阻塞业务逻辑
    public void onProductChanged(ProductChangedEvent event) {
        log.info("收到产品变更事件: productId={}, type={}", event.productId(), event.changeType());
        
        switch (event.changeType()) {
            case STATUS_CHANGED -> productCacheInvalidator.invalidateOnProductStatusChange(event.productId());
            default -> productCacheInvalidator.invalidateOnProductChange(event.productId());
        }
    }
    
    @EventListener
    @Async
    public void onPolicyChanged(PolicyChangedEvent event) {
        log.info("收到策略变更事件: productId={}, policyId={}, type={}", 
            event.productId(), event.policyId(), event.changeType());
        
        policyCacheInvalidator.invalidateOnPolicyChange(event.productId(), event.policyId());
    }
    
    @EventListener
    @Async
    public void onFirmwareChanged(FirmwareChangedEvent event) {
        log.info("收到固件变更事件: productId={}, versionId={}, type={}", 
            event.productId(), event.versionId(), event.changeType());
        
        firmwareCacheInvalidator.invalidateOnFirmwarePublish(
            event.productId(), event.versionId());
    }
    
    @EventListener
    @Async
    public void onDeviceChanged(DeviceChangedEvent event) {
        log.debug("收到设备变更事件: imei={}, type={}", event.imei(), event.changeType());
        
        deviceCacheRepository.evict(event.imei());
    }
}

// ==================== 业务代码触发事件 ====================

@Service
@RequiredArgsConstructor
public class PolicyService {
    
    private final ApplicationEventPublisher eventPublisher;
    private final PolicyRepository policyRepository;
    
    @Transactional
    public void updatePolicy(Long policyId, PolicyUpdateDTO dto) {
        // 1. 更新数据库
        UpgradePolicy policy = policyRepository.findById(policyId);
        policy.setGrayRate(dto.getGrayRate());
        policyRepository.save(policy);
        
        // 2. 发布缓存失效事件
        eventPublisher.publishEvent(
            new PolicyChangedEvent(policy.getProductId(), policyId, ChangeType.UPDATED)
        );
        
        log.info("策略已更新: policyId={}, grayRate={}", policyId, dto.getGrayRate());
    }
}
```

### 8.4 失效监控与告警

```java
@Component
@RequiredArgsConstructor
public class CacheInvalidationMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public void recordInvalidation(String cacheType, String operation, int keyCount) {
        meterRegistry.counter("cache.invalidation",
            "type", cacheType,
            "operation", operation
        ).increment(keyCount);
        
        log.info("缓存失效统计: type={}, operation={}, count={}", cacheType, operation, keyCount);
    }
}
```

**监控指标**：

| 指标 | 说明 | 告警阈值 |
|------|------|---------|
| `cache.invalidation{type=policy}` | 策略缓存失效次数 | > 100/小时（可能策略频繁变更） |
| `cache.invalidation{type=product}` | 产品缓存失效次数 | > 50/小时 |
| `cache.invalidation{type=device}` | 设备缓存失效次数 | > 1000/小时（可能批量操作） |

---

## 9. 缓存使用策略（分层设计）

### 9.1 分层 TTL 策略

**决策**: ✅ 采用**分层 TTL 策略**（设备缓存不续期，共享缓存续期）

**核心理念**：
- **设备缓存**：固定 TTL，沉默期释放内存
- **共享缓存**：滑动 TTL（续期），热点数据持续缓存
- **一致性保证**：主动失效为主，兜底过期为辅

### 9.2 设备缓存：固定 TTL（不续期）

| 策略 | 配置 | 理由 |
|------|------|------|
| **TTL** | 24小时 | 覆盖脉冲模式整个周期 |
| **续期** | ❌ 不续期 | 沉默期释放内存 |
| **一致性** | 被动过期 | 设备信息相对稳定 |

**实现原则**：

```java
// ✅ 推荐：固定 TTL，不续期
@Override
public DeviceCache get(String imei) {
    String key = String.format(DEVICE_KEY_TEMPLATE, imei);
    Object cached = redisTemplate.opsForValue().get(key);
    // ❌ 不调用 expire()，让缓存自然过期
    // 原因：沉默期该设备不检查，缓存自然过期释放内存
    return (DeviceCache) cached;
}

// 写入时设置固定 TTL
@Override
public void put(String imei, DeviceCache cache) {
    String key = String.format(DEVICE_KEY_TEMPLATE, imei);
    cache.setCachedAt(LocalDateTime.now());
    redisTemplate.opsForValue().set(key, cache,
        Duration.ofSeconds(DEVICE_CACHE_TTL_SECONDS)); // 固定24h
}
```

**内存释放效果**：

| 时间 | 设备缓存状态 | 内存占用 |
|------|------------|---------|
| **Day 1** | 持续加载和命中 | ~10GB |
| **Day 2** | 开始自然过期 | ~5GB |
| **Day 3** | 大部分过期 | ~1GB |
| **Day 4** | 几乎全部过期 | ~100MB |
| **Day 4 检查开始** | 重新加载 | ~10GB |

### 9.3 共享缓存：滑动 TTL（续期）+ 主动失效

| 策略 | 配置 | 理由 |
|------|------|------|
| **TTL** | 24h/7d/30d（分层） | 兜底过期，防止主动失效失败 |
| **续期** | ✅ **续期** | 热点数据持续缓存 |
| **一致性** | **主动失效**（主要） | 管理后台修改立即生效 |

**实现原则**：

```java
// ✅ 推荐：滑动 TTL（续期）
public List<UpgradePolicy> getProductPolicies(Long productId, boolean includeTest) {
    String key = String.format(PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, 
        includeTest ? "all" : "prod");
    String json = redisTemplate.opsForValue().get(key);
    
    if (json != null) {
        // ✅ 续期：热点数据持续保持缓存
        long ttl = getRandomizedTtl(POLICY_CACHE_TTL_SECONDS);
        redisTemplate.expire(key, Duration.ofSeconds(ttl));
        return deserializePolicies(json);
    }
    
    return null;
}

// 写入时设置 TTL
public void cacheProductPolicies(Long productId, boolean includeTest, 
                                  List<UpgradePolicy> policies) {
    String key = String.format(PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, 
        includeTest ? "all" : "prod");
    String json = serializePolicies(policies);
    
    long ttl = getRandomizedTtl(POLICY_CACHE_TTL_SECONDS);
    redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttl));
    
    // 更新缓存索引
    updateCacheIndex(productId, key);
}
```

**为什么续期是正确的？**

| 场景 | 固定TTL的后果 | 滑动TTL（续期）的后果 |
|------|--------------|---------------------|
| **热点产品** | 24h后仍需重新加载（旧方案） | ✅ 持续缓存（续期），性能最优 |
| **冷门产品** | 自然过期 | ✅ 自然过期，释放内存 |
| **策略变更** | 最多24h延迟（旧方案） | ✅ 主动失效，立即生效 |
| **内存占用** | ~10MB | ~10MB（相同，可忽略） |

**关键理解**：

```
共享缓存（策略/产品/固件）的特点：
1. 所有设备共享同一份数据
2. 千万设备访问时间分散（0-2h抖动）
3. 几乎每时每刻都有设备在访问
4. 固定TTL无法真正释放内存，反而增加重新加载开销

因此：滑动TTL（续期）+ 主动失效是最优方案
- 热点数据持续缓存（性能最优）
- 冷门数据自然过期（内存释放）
- 主动失效保证一致性（运营修改立即生效）
```

### 9.4 懒加载优先原则

**决策**: ✅ **不主动预热**，依赖懒加载

**原因**:

1. **预热收益极低**:
   - 脉冲模式：Day 2-3 沉默期，预热数据会过期
   - 设备分布：千万级设备，无法预测哪些会检查
   - TTL 限制：设备缓存24h后过期

2. **懒加载足够好**:
   - 首次访问触发加载（1 次数据库查询）
   - 后续持续命中缓存（共享缓存续期）
   - 脉冲模式 Day 1 第一次检查后，后续检查都命中缓存

**如果必须预热，仅由 Leader 预热产品策略**：

```java
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "region")
public class CacheWarmupCoordinator {
    
    private final RegionLeaderService leaderService;
    private final RedisDistributedLockService lockService;
    
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // 1. 只有 Leader 才执行预热
        if (!leaderService.isLeader()) {
            log.info("非 Leader 实例，跳过预热");
            return;
        }
        
        // 2. 尝试获取分布式锁
        boolean acquired = lockService.tryLock("fota:warmup:lock", 300);
        if (!acquired) {
            return;
        }
        
        try {
            // 3. 检查是否已完成预热（24小时内）
            String status = redisTemplate.opsForValue().get("fota:warmup:status");
            if ("completed".equals(status)) {
                return;
            }
            
            // 4. 预热热点产品策略（而非设备数据）
            warmupHotProductPolicies();
            
            // 5. 标记完成（24小时有效）
            redisTemplate.opsForValue().set("fota:warmup:status", "completed", 
                Duration.ofHours(24));
        } finally {
            lockService.unlock("fota:warmup:lock");
        }
    }
}
```

### 9.5 动态 TTL 不实施

**决策**: ❌ **不实现动态 TTL**，保持静态配置（分层）

**关键理解**: `check_interval` ≠ `cache TTL`

```
┌─────────────────────────────────────────────────────────┐
│                  三层独立控制                             │
├─────────────────────────────────────────────────────────┤
│ 1. 限流层（RedisDeviceRateLimiter）                      │
│    - 控制访问频率                                         │
│    - 快速失败                                             │
│    - 返回 retry-after                                     │
├─────────────────────────────────────────────────────────┤
│ 2. 设备行为层（check_interval）                          │
│    - 控制设备下次检查时间                                 │
│    - 分散流量（抖动）                                     │
│    - 服务端动态调整                                       │
├─────────────────────────────────────────────────────────┤
│ 3. 数据新鲜度层（cache TTL）                             │
│    - 控制数据有效期                                       │
│    - 分层配置（24h/7d/30d）                              │
│    - 不受 check_interval 影响                            │
└─────────────────────────────────────────────────────────┘
```

**为什么动态 TTL 不适合**：

| 场景 | 动态 TTL 的后果 | 静态 TTL 的后果 |
|------|----------------|----------------|
| **高负载，下发 check_interval=24h** | TTL = 48h，策略变更延迟太长 | TTL = 24h（续期），策略变更主动失效 |
| **脉冲模式 Day 1** | TTL 可能覆盖到 Day 3 沉默期 | TTL = 24h（续期），持续缓存 |
| **策略紧急变更** | 延迟不可控（1-48h） | 主动失效，立即生效 |

**结论**：分层静态 TTL + 滑动续期 + 主动失效是最优方案。

---

## 10. 降级策略

```java
public Product getProductWithFallback(Long productId) {
    // Redis 不可用时直接查数据库
    if (!redisAvailable) {
        return productRepository.findById(productId);
    }

    try {
        // 尝试从缓存获取
        Product cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }
    } catch (Exception e) {
        log.warn("Redis access failed, fallback to DB");
    }

    // 缓存未命中，查数据库并回写
    Product product = productRepository.findById(productId);
    if (product != null) {
        redisTemplate.opsForValue().set(key, product, randomizedTtl);
    }
    return product;
}
```

---

## 11. 监控指标

| 指标 | 阈值 | 告警级别 |
|------|------|----------|
| 缓存命中率 | < 95% | 警告 |
| 缓存延迟 P95 | > 50ms | 警告 |
| Redis 内存使用率 | > 80% | 严重 |
| 降级到数据库次数 | > 100/小时 | 严重 |
| 缓存失效次数（策略） | > 100/小时 | 警告 |

---

## 12. 验证清单

### 单元测试
- [ ] 缓存命中场景
- [ ] 缓存未命中场景
- [ ] 缓存主动失效
- [ ] 索引批量失效
- [ ] Redis 不可用时降级
- [ ] TTL 随机化
- [ ] 脉冲模式缓存过期与重建
- [ ] 稳定模式缓存持续性
- [ ] **策略修改后立即失效**
- [ ] **产品修改后立即失效**
- [ ] **固件修改后立即失效**
- [ ] **设备修改后立即失效**

### 性能测试
- [ ] 压测 QPS > 10,000
- [ ] P99 响应时间 < 50ms
- [ ] 缓存命中率 > 95%（脉冲模式）
- [ ] 缓存命中率 > 98%（稳定模式）

### 一致性测试
- [ ] 策略变更后，缓存立即失效
- [ ] 多实例部署，缓存一致性验证
- [ ] 脉冲模式周期性循环，缓存重建正确
- [ ] **管理后台修改，设备立即感知**

---

## 13. 关键文件清单

| 文件 | 路径 | 说明 |
|------|------|------|
| RedisKeyConstants | `fota-framework-cache/.../constant/RedisKeyConstants.java` | 键常量定义 |
| RedisDeviceCacheRepository | `fota-service/infra/cache/RedisDeviceCacheRepository.java` | 参考实现 |
| UpgradePolicyRepositoryImpl | `fota-service/infra/persistence/.../UpgradePolicyRepositoryImpl.java` | 待添加缓存 |
| UpgradeCheckService | `fota-service/.../UpgradeCheckService.java` | 核心服务 |
| UpgradeResponseBuilder | `fota-service/.../UpgradeResponseBuilder.java` | check_interval 逻辑 |
| **ProductCacheInvalidator** | `fota-service/infra/cache/ProductCacheInvalidator.java` | **产品缓存失效（新建）** |
| **PolicyCacheInvalidator** | `fota-service/infra/cache/PolicyCacheInvalidator.java` | **策略缓存失效（新建）** |
| **FirmwareCacheInvalidator** | `fota-service/infra/cache/FirmwareCacheInvalidator.java` | **固件缓存失效（新建）** |
| **CacheInvalidationListener** | `fota-service/infra/cache/CacheInvalidationListener.java` | **事件监听器（新建）** |

---

## 14. 总结

### 14.1 方案核心决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| **缓存架构** | 仅 Redis，不用 Caffeine | 设备检查周期长，本地缓存命中率低 |
| **TTL 时长** | 分层（24h/7d/30d） | 不同数据类型不同需求 |
| **TTL 策略** | 分层：设备固定，共享滑动 | 设备沉默期释放，共享热点保持 |
| **缓存预热** | 不预热（或仅 Leader 预热产品策略） | 懒加载足够，预热收益低 |
| **动态 TTL** | 不实现 | check_interval 控制设备行为，不应影响数据新鲜度 |
| **主动失效** | ✅ 必须实现 | 管理后台修改必须立即生效 |
| **共享缓存续期** | ✅ 必须实现 | 热点数据持续缓存，性能最优 |

### 14.2 分层 TTL 策略详解

#### 设备缓存：固定 TTL（不续期）

| 策略 | 配置 | 理由 |
|------|------|------|
| **TTL** | 24小时 | 覆盖脉冲模式整个周期 |
| **续期** | ❌ 不续期 | 沉默期自然过期，释放内存 |
| **一致性** | 被动过期 | 设备信息相对稳定 |

**内存效果**：
```
Day 1: ~10GB（设备持续加载）
Day 2: ~5GB（开始过期）
Day 3: ~1GB（大部分过期）
Day 4: ~100MB → 重新加载
```

#### 共享缓存：滑动 TTL（续期）+ 主动失效

| 策略 | 配置 | 理由 |
|------|------|------|
| **TTL** | 24h/7d/30d（分层） | 兜底过期，防止主动失效失败 |
| **续期** | ✅ **续期** | 热点数据持续缓存，性能最优 |
| **一致性** | **主动失效**（主要） | 管理后台修改立即生效 |

**性能效果**：
```
热点产品：持续缓存（续期），性能最优
冷门产品：自然过期（无访问），释放内存
策略变更：主动失效，立即生效
```

### 14.3 针对两种检查模式的适用性

#### 脉冲式检查模式（推荐）

| 评估项 | 结果 |
|--------|------|
| **Day 1 检查窗口** | ✅ 24h TTL 覆盖 2h 间隔 + 2h 抖动 |
| **Day 2-3 沉默期** | ✅ 设备缓存自然过期，共享缓存续期保持 |
| **周期性重复** | ✅ 设备缓存每周重建，共享缓存持续保持 |
| **缓存命中率** | ✅ Day 1 > 99%，共享缓存持续有效 |

#### 稳定检查模式

| 评估项 | 结果 |
|--------|------|
| **6h 检查间隔** | ✅ 24h TTL > 6h，覆盖 4 个检查周期 |
| **无抖动场景** | ✅ TTL 随机化仍有效防雪崩 |
| **缓存命中率** | ✅ 预期 > 99%，优秀 |

#### 服务端动态控制

| 评估项 | 结果 |
|--------|------|
| **当前支持** | ✅ 分层静态 TTL 配置已实现 |
| **未来扩展** | ⚠️ 动态 TTL 不推荐实施 |
| **实施优先级** | ❌ 不实施动态 TTL |

### 14.4 主动失效的重要性

> ⚠️ **关键**: 没有主动失效，运营修改要等很长时间才能生效，这是不可接受的

**必须实现的失效场景**：

1. ✅ **产品修改** → 立即失效产品缓存 + 策略缓存
2. ✅ **策略修改** → 立即失效策略缓存 + 产品策略列表
3. ✅ **固件修改** → 立即失效固件缓存 + 相关策略缓存
4. ✅ **设备修改** → 立即失效设备缓存

**实现方式**：Spring Event + 异步监听器

### 14.5 分层 TTL 策略的优势

**关键优势**：
1. ✅ 设备缓存沉默期释放内存（主要内存占用）
2. ✅ 共享缓存续期保持热点数据（性能最优）
3. ✅ 主动失效机制保证运营修改立即生效
4. ✅ 兜底过期防止主动失效失败
5. ✅ 实现简单，避免过度设计

**内存与性能对比**：

| 指标 | 固定TTL（旧方案） | 分层TTL（新方案） |
|------|-----------------|------------------|
| **设备缓存内存** | 沉默期有效释放 | 沉默期有效释放（相同） |
| **共享缓存内存** | ~10MB（可忽略） | ~10MB（相同） |
| **数据库查询** | 24h后重新加载（旧方案） | 持续缓存（**更优**） |
| **一致性保证** | 主动失效 + 兜底过期 | 主动失效 + 兜底过期（相同） |

### 14.6 最终结论

**✅ 当前方案完整且合理，推荐立即实施。**

**分层策略总结**：
```
设备缓存 → 固定TTL（不续期）→ 沉默期释放内存
共享缓存 → 滑动TTL（续期）→ 热点数据持续缓存
主动失效 → 修改时立即失效 → 运营修改立即生效
兜底过期 → 较长TTL → 防止主动失效失败
```

**潜在优化**:
- ⚠️ 若监控发现缓存命中率 < 95%，需检查续期逻辑
- ⚠️ 若发现主动失效失败，需增强监控和告警
- ⚠️ 若共享缓存内存占用异常增长，可考虑缩短 TTL

---

## 15. 参考文档

- [Redis缓存键设计方案.md](../03-design/ttl-strategy-design.md) - 完整的键设计方案
- [ttl-strategy-design.md](../03-design/ttl-strategy-design.md) - 完整的 TTL 策略设计
- [PRD - 设备检查周期](../01-product/prd.md) - 业务需求文档

---

## 附录：TTL 配置常量（参考）

```java
// RedisKeyConstants.java

// ========== 设备缓存（固定TTL，不续期）==========
public static final long DEVICE_CACHE_TTL_SECONDS = 24 * 60 * 60;  // 24小时

// ========== 共享缓存（滑动TTL，续期）==========
public static final long POLICY_CACHE_TTL_SECONDS = 24 * 60 * 60;      // 24小时
public static final long PRODUCT_CACHE_TTL_SECONDS = 7 * 24 * 60 * 60; // 7天
public static final long FIRMWARE_CACHE_TTL_SECONDS = 30 * 24 * 60 * 60; // 30天

// ========== 缓存索引（与数据相同）==========
public static final long CACHE_INDEX_TTL_SECONDS = 24 * 60 * 60;  // 24小时
```

### 实现示例

```java
// RedisPolicyCacheRepository.java

@Service
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
        
        // 更新缓存索引
        updateCacheIndex(productId, key);
    }
    
    /**
     * TTL 随机化（防缓存雪崩）
     */
    private long getRandomizedTtl(long baseTtl) {
        double randomFactor = 0.1; // ±10%
        long randomOffset = (long) (baseTtl * randomFactor * (Math.random() - 0.5));
        return Math.max(1, baseTtl + randomOffset);
    }
}
```
