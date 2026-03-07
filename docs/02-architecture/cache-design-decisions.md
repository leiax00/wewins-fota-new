# 缓存设计决策记录

> **日期**: 2026-03-04
> **决策者**: FOTA Team
> **状态**: ✅ 已采纳

---

## 背景

FOTA 系统需要实现高性能的缓存机制，支撑 10,000+ QPS 和 99% 请求 < 50ms 的目标。在设计设备缓存、策略缓存和固件缓存时，面临以下选择：

1. **缓存模式选择**：Embedded Pattern vs Keys Pattern
2. **命名空间设计**：如何组织 Redis Key
3. **设备缓存内容**：缓存哪些字段

---

## 决策 1：列表缓存采用 Keys Pattern

### 问题描述

产品策略列表如何缓存？

**选项 A：Embedded Pattern（String JSON）**
```redis
Key: fota:cache:product:policy:1001:all
Value: [Policy1, Policy2, ..., Policy50]  # 完整对象列表
```

**选项 B：Keys Pattern（ID列表 + MGET）**
```redis
Key: fota:cache:list:product:policy:1001:all
Type: Set
Members: [1, 2, 3, ..., 50]  # ID 列表

Key: fota:policy:1
Key: fota:policy:2
...
```

### 决策结果

**采用 Keys Pattern** ✅

### 理由

1. **更新范围小**
   - Embedded Pattern：修改 1 个策略 → 重写整个列表（50KB）
   - Keys Pattern：修改 1 个策略 → 只更新 1 个对象（1KB）
   - **收益：更新范围减少 95%**

2. **并发安全性高**
   - Embedded Pattern：多人同时修改 → 丢失更新风险
   - Keys Pattern：每个对象独立更新 → 无冲突

3. **与现有架构一致**
   - 已有单对象缓存能力
   - 只需新增 ID 列表缓存

4. **性能可接受**
   - MGET 50 个键：~15,000 req/s（参考 Redis 官方测试）
   - 满足 10,000+ QPS 要求

### 实施计划

**阶段 1（立即）**：策略列表缓存
- 重构 `RedisPolicyCacheRepository`
- 迁移到 Keys Pattern

---

## 决策 2：命名空间简化

### 问题描述

列表缓存的 Key 是否需要 `ids` 层级？

**选项 A**：`fota:cache:list:product:policy:ids:{productId}:{type}`
**选项 B**：`fota:cache:list:product:policy:{productId}:{type}`

### 决策结果

**去掉 `ids` 层级** ✅

### 理由

1. **Redis Type 本身能区分**
   - Set = ID 列表
   - String = 完整对象
   - 不需要额外的 `ids` 标识

2. **命名更简洁**
   - 减少不必要的层级
   - 提高可读性

### 最终命名规范

```redis
# 单个对象缓存（Type: String）
fota:device:{imei}
fota:policy:{policyId}
fota:product:{productId}
fota:firmware:{versionId}

# 列表缓存（Type: Set）
fota:cache:list:product:policy:{productId}:{type}

# 缓存索引（Type: Set）
fota:cache:index:product:{productId}
fota:cache:index:policy:{policyId}
```

---

## 决策 3：设备缓存精简设计

### 问题描述

设备缓存是直接缓存完整 `Device` 对象，还是精简的 `DeviceCache`？

### 决策结果

**采用精简 DeviceCache** ✅

### 字段定义

```java
@Data
@Builder
public class DeviceCache implements Serializable {
    // 核心字段
    private Long deviceId;              // 标记活跃、索引
    private Long productId;             // 产品查询、策略匹配
    private Long currentVersionId;      // 版本匹配
    
    // 策略匹配字段
    private JsonNode tags;              // 测试设备判断 + 策略标签匹配
    private Long importBatchId;         // 批量导入标签匹配
    
    // 可选元数据
    private LocalDateTime cachedAt;     // 缓存时间戳
}
```

### 理由

1. **包含 check 流程必需字段**
   - `tags`：`isTestDevice()` 和 `policyMatcher.matchesTargetMode()` 使用
   - `importBatchId`：`policyMatcher.matchesTargetMode()` 使用

2. **内存占用可控**
   - 精简 DeviceCache：~500 bytes/设备
   - 完整 Device 对象：~1 KB/设备
   - 1000万设备：5GB vs 10GB

3. **与其他缓存风格一致**
   - 产品缓存、策略缓存、固件缓存都使用专用 Cache DTO
   - 不直接缓存实体对象

4. **避免不必要的字段**
   - `status`、`lastSeenAt` 等字段不在 check 流程中使用
   - 减少序列化开销

---

## 后果

### 正面影响

1. **性能提升**
   - 列表更新范围减少 95%
   - 设备缓存内存占用减少 50%

2. **并发安全**
   - Keys Pattern 避免丢失更新
   - 独立对象更新无冲突

3. **代码一致性**
   - 统一的命名规范
   - 统一的缓存模式

### 负面影响

1. **读取性能略降**
   - 列表缓存：1次 GET → 1次 SMEMBERS + 1次 MGET
   - 网络往返增加 1 次

2. **实现复杂度增加**
   - 需要维护 ID 列表和对象缓存的一致性
   - 策略列表缓存需要重构

### 缓解措施

1. **使用 Redis Pipeline** 批量操作
2. **充分测试** 确保一致性
3. **灰度发布** 降低风险

---

## 相关文档

- [Redis 缓存标准与规范](../03-standards/redis-cache-standards.md)
- [Redis Key 常量定义](../../fota-framework/fota-framework-cache/src/main/java/com/wewins/fota/cache/constant/RedisKeyConstants.java)
- [FOTA 系统架构](./fota-architecture.md)

---

## 变更历史

| 日期 | 决策 | 状态 |
|------|------|------|
| 2026-03-04 | 初始决策：Keys Pattern + 命名简化 + DeviceCache 精简 | ✅ 已采纳 |
