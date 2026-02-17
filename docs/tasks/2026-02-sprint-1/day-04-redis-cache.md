# Day 4: Redis 缓存架构实施计划

> **时间**: 2026-02-17
> **目标**: 实现 Redis 缓存核心功能，包括设备活跃度跟踪、限流、策略缓存
> **预计时间**: 1天

---

## 📋 任务概览

### 核心目标
1. ✅ Redis 配置补齐（StringRedisTemplate、连接池优化）
2. ✅ 设备活跃度 Bitmap 实现（核心功能）
3. ✅ 限流功能实现（基于 Redis + Lua）
4. ✅ 集成到设备检查链路
5. ⏸️ 策略快照缓存（可延后到 Day 5）

### 验收标准
- [ ] Redis 配置优化完成（StringRedisTemplate、连接池参数完善）
- [ ] 设备活跃度 Bitmap 功能正常（SETBIT、GETBIT、BITCOUNT、BITOP）
- [ ] 限流功能正常（基于 Redis + Lua，支持每设备限流）
- [ ] UpgradeCheckService 集成 Bitmap 和限流
- [ ] Redis Key 常量补齐（Bitmap 相关）
- [ ] 简单集成测试通过

---

## 📅 任务分解

### Task 1: Redis 配置补齐 ⏱️ 1h

**状态**: ⏸️ 待开始

**实施步骤**:
1. 扩展 `RedisConfiguration`
   - 添加 `StringRedisTemplate` bean
   - 配置 `RedisScript` bean（用于 Lua 脚本）
   - 优化 Jackson 序列化器（移除安全风险）

2. 创建 `FotaCacheProperties`
   - 配置类：`@ConfigurationProperties(prefix = "fota.cache")`
   - 字段：TTL、Bitmap 保留天数、限流窗口大小

3. 优化 `application.yml`
   - Lettuce 连接池参数补齐（max-wait、time-between-eviction-runs）
   - 添加缓存配置项

**文件清单**:
- `fota-framework-cache/src/main/java/com/wewins/fota/cache/config/RedisConfiguration.java`（修改）
- `fota-framework-cache/src/main/java/com/wewins/fota/cache/config/FotaCacheProperties.java`（新建）
- `fota-service/src/main/resources/application.yml`（修改）

**验收标准**:
- [ ] `StringRedisTemplate` bean 可用
- [ ] 连接池配置参数完整
- [ ] 配置属性可正确绑定

---

### Task 2: 扩展 RedisKeyConstants ⏱️ 30m

**状态**: ⏸️ 待开始

**实施步骤**:
1. 添加 Bitmap 相关键模板
2. 添加策略快照相关键模板
3. 添加限流配额相关键模板
4. 定义 TTL 常量

**新增内容**:
```java
// Bitmap 相关
ACTIVE_BITMAP_KEY_TEMPLATE = "fota:active:%s"  // yyyyMMdd
BITOP_TEMP_KEY_TEMPLATE = "fota:tmp:bitop:%s:%s"

// 策略快照相关
POLICY_SNAPSHOT_KEY_TEMPLATE = "fota:pol:snap:%s:v%s"
POLICY_ACTIVE_VER_KEY_TEMPLATE = "fota:pol:active_ver:%s"

// 限流配额相关
POLICY_QUOTA_KEY_TEMPLATE = "fota:quota:policy:%s:%s"  // {date}:{policyId}
GRAY_COUNT_KEY_TEMPLATE = "fota:gray:count:%s"  // {date}:{policyId}
```

**文件清单**:
- `fota-framework-cache/src/main/java/com/wewins/fota/cache/constant/RedisKeyConstants.java`（修改）

**验收标准**:
- [ ] 所有键模板定义完整
- [ ] TTL 常量定义完整
- [ ] 命名符合规范

---

### Task 3: 设备活跃度 Bitmap 实现 ⏱️ 2h

**状态**: ⏸️ 待开始

**实施步骤**:
1. 创建 `DeviceActivityBitmapRepository` 接口
2. 创建 `RedisDeviceActivityBitmapRepository` 实现
3. 创建 Lua 脚本资源文件
4. 编写单元测试

**核心接口**:
```java
public interface DeviceActivityBitmapRepository {
    // 标记设备活跃（位图偏移量为设备 ID）
    void markActive(LocalDate day, long deviceId);

    // 检查设备是否活跃
    boolean isActive(LocalDate day, long deviceId);

    // 统计活跃设备数
    long countActive(LocalDate day);

    // 统计多日活跃设备（并集）
    long countActiveUnion(LocalDate endDay, int days);

    // 统计沉默设备数（多日未活跃）
    long countSilentDevices(LocalDate endDay, int silentDays);
}
```

**Redis 命令**:
- `SETBIT fota:active:20260217 {deviceId} 1`
- `GETBIT fota:active:20260217 {deviceId}`
- `BITCOUNT fota:active:20260217`
- `BITOP OR dest fota:active:20260215 fota:active:20260216`

**文件清单**:
- `fota-framework-cache/src/main/java/com/wewins/fota/cache/bitmap/DeviceActivityBitmapRepository.java`（新建）
- `fota-framework-cache/src/main/java/com/wewins/fota/cache/bitmap/RedisDeviceActivityBitmapRepository.java`（新建）
- `fota-framework-cache/src/main/resources/redis/bitmap/mark_active.lua`（新建）
- `fota-framework-cache/src/test/java/com/wewins/fota/cache/bitmap/RedisDeviceActivityBitmapRepositoryTest.java`（新建）

**验收标准**:
- [ ] SETBIT/GETBIT 正常工作
- [ ] BITCOUNT 统计准确
- [ ] BITOP 多日合并正确
- [ ] 单元测试覆盖率 ≥ 80%

---

### Task 4: 限流功能实现 ⏱️ 2h

**状态**: ⏸️ 待开始

**实施步骤**:
1. 创建 `RateLimitDecision` DTO
2. 创建 `DeviceRateLimiter` 接口
3. 创建 `RedisDeviceRateLimiter` 实现
4. 创建 Lua 脚本（INCR + EXPIRE 原子操作）
5. 编写单元测试

**核心接口**:
```java
public interface DeviceRateLimiter {
    // 检查是否允许请求（固定窗口限流）
    RateLimitDecision allow(String key, int maxRequests, Duration window);

    // LastSeen 限频（每小时最多更新一次）
    boolean allowLastSeenUpdate(String imei, Duration minInterval);
}
```

**返回值**:
```java
@Data
@Builder
public class RateLimitDecision {
    private boolean allowed;
    private int remaining;
    private long resetAtEpochSecond;
    private String reason;  // "RATE_LIMITED" 或 null
}
```

**Lua 脚本逻辑**:
```lua
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])
local current = tonumber(redis.call('INCR', key))
if current == 1 then
    redis.call('EXPIRE', key, ttl)
end
if current > limit then
    return {0, limit, ttl}
else
    return {1, limit - current, ttl}
end
```

**文件清单**:
- `fota-framework-cache/src/main/java/com/wewins/fota/cache/ratelimit/RateLimitDecision.java`（新建）
- `fota-framework-cache/src/main/java/com/wewins/fota/cache/ratelimit/DeviceRateLimiter.java`（新建）
- `fota-framework-cache/src/main/java/com/wewins/fota/cache/ratelimit/RedisDeviceRateLimiter.java`（新建）
- `fota-framework-cache/src/main/resources/redis/ratelimit/fixed_window.lua`（新建）
- `fota-framework-cache/src/test/java/com/wewins/fota/cache/ratelimit/RedisDeviceRateLimiterTest.java`（新建）

**验收标准**:
- [ ] 固定窗口限流正常工作
- [ ] 并发场景下限流准确（Lua 原子性）
- [ ] LastSeen 限频正常
- [ ] 单元测试覆盖率 ≥ 80%

---

### Task 5: 集成到 UpgradeCheckService ⏱️ 1.5h

**状态**: ⏸️ 待开始

**实施步骤**:
1. 查看现有 `UpgradeCheckService` 代码
2. 注入 `DeviceActivityBitmapRepository` 和 `DeviceRateLimiter`
3. 在 `checkUpgrade` 方法中集成：
   - 先限流检查
   - 标记设备活跃
   - 继续原有逻辑

**集成流程**:
```java
public UpgradeCheckResponse checkUpgrade(UpgradeCheckRequest request) {
    // 1. 限流检查
    RateLimitDecision decision = rateLimiter.allow(
        "upgrade:" + request.getImei(),
        10,  // 每分钟最多 10 次
        Duration.ofMinutes(1)
    );
    if (!decision.isAllowed()) {
        return UpgradeCheckResponse.builder()
            .decision(DecisionType.RATE_LIMITED)
            .retryAfterSeconds(decision.getResetAtEpochSecond() - epochNow())
            .build();
    }

    // 2. 标记设备活跃
    long deviceId = getDeviceId(request.getImei());
    bitmapRepository.markActive(LocalDate.now(), deviceId);

    // 3. 继续原有逻辑（策略匹配等）
    // ...
}
```

**文件清单**:
- `fota-service/src/main/java/com/wewins/fota/application/upgrade/UpgradeCheckService.java`（修改）

**验收标准**:
- [ ] 限流检查生效
- [ ] 设备活跃度正确标记
- [ ] 原有功能不受影响
- [ ] 错误处理完善（Redis 不可用时的降级策略）

---

### Task 6: Redis 缓存标准文档 ⏱️ 1h

**状态**: ⏸️ 待开始

**实施步骤**:
1. 创建 `docs/03-standards/redis-cache-standards.md`
2. 包含内容：
   - Redis Key 命名规范
   - TTL 设置原则
   - Bitmap 使用规范
   - 限流实现规范
   - 性能优化建议
   - 监控指标

**文件清单**:
- `docs/03-standards/redis-cache-standards.md`（新建）

**验收标准**:
- [ ] 文档结构完整
- [ ] 规范清晰可执行
- [ ] 包含示例代码

---

## 🚧 风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 | 状态 |
|------|------|------|----------|------|
| Lua 脚本调试困难 | 中 | 中 | 先用 Redis CLI 测试，再集成 | ⏸️ |
| Bitmap 大键内存问题 | 高 | 低 | 监控 bitmap 大小，限制统计窗口 | ⏸️ |
| Redis 连接池耗尽 | 高 | 低 | 完善连接池配置，添加监控 | ⏸️ |
| 序列化兼容性问题 | 中 | 低 | 使用 StringRedisTemplate，避免 Object 序列化 | ⏸️ |

---

## 📝 变更日志

### 2026-02-17
- ✅ 创建 Day 4 实施计划
- ✅ 任务分解完成
- ✅ 验收标准明确

---

## 🔗 相关文档

- [Sprint 1 总览](../../05-plans/sprint-1.md)
- [Redis 缓存标准](../../03-standards/redis-cache-standards.md)
- [FOTA 系统架构](../../02-architecture/fota-architecture.md)
- [产品需求](../../01-product/prd.md)
