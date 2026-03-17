# 设备标签和分组功能使用指南

## 概述

FOTA 系统支持设备标签和分组功能，用于灵活管理和筛选设备。

### 标签（Tags）
- 使用 JSONB 存储，支持高效的 JSON 查询
- 一个设备可以有多个标签（如：测试设备、CN、beta、5G设备）
- 支持标签组合查询

### 分组（Groups）
- 多对多关系，一个设备可以属于多个分组
- 支持按地区、业务线、试点等维度分组
- 软删除支持，保留历史记录

---

## 数据库表结构

### 1. devices 表（已添加 tags 字段）

```sql
CREATE TABLE IF NOT EXISTS devices (
    id BIGSERIAL PRIMARY KEY,
    imei VARCHAR(255) UNIQUE NOT NULL,
    product_id BIGINT,
    current_version_id BIGINT,
    status VARCHAR(50) NOT NULL,
    last_seen_at TIMESTAMP,
    tags JSONB,                                    -- 新增
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    ...
);

-- GIN 索引用于高效的 JSONB 查询
CREATE INDEX IF NOT EXISTS idx_devices_tags_gin ON devices USING GIN (tags);
```

### 2. device_groups 表

```sql
CREATE TABLE IF NOT EXISTS device_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    remark TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP
);

-- 分组名称唯一索引（过滤软删除）
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_groups_name
    ON device_groups(name) WHERE deleted_at IS NULL;
```

### 3. device_group_members 表（多对多关联）

```sql
CREATE TABLE IF NOT EXISTS device_group_members (
    id BIGSERIAL PRIMARY KEY,                     -- 代理主键
    device_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    CONSTRAINT uk_dgm_device_group UNIQUE (device_id, group_id)
);
```

---

## Java 代码示例

### 1. 设置设备标签

```java
import com.wewins.fota.entity.Device;
import com.wewins.fota.mapper.DeviceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Arrays;
import java.util.List;

@Service
public class DeviceService {

    @Autowired
    private DeviceMapper deviceMapper;

    /**
     * 创建设备并设置标签
     */
    public void createDeviceWithTags() {
        Device device = new Device();
        device.setImei("123456789012345");
        device.setProductId(1L);
        device.setStatus("ACTIVE");

        // 设置标签
        List<String> tags = Arrays.asList("测试设备", "CN", "beta", "5G设备");
        device.setTags(tags);

        // 插入数据库，TypeHandler 会自动将 List<String> 转为 JSONB
        deviceMapper.insert(device);

        // 数据库存储格式：["测试设备", "CN", "beta", "5G设备"]
    }

    /**
     * 添加标签到现有设备
     */
    public void addTagsToDevice(Long deviceId, List<String> newTags) {
        Device device = deviceMapper.selectById(deviceId);
        if (device != null) {
            List<String> currentTags = device.getTags();
            if (currentTags == null) {
                currentTags = new ArrayList<>();
            }
            currentTags.addAll(newTags);
            device.setTags(currentTags);
            deviceMapper.updateById(device);
        }
    }
}
```

### 2. 分组管理

```java
import com.wewins.fota.entity.DeviceGroup;
import com.wewins.fota.entity.DeviceGroupMember;
import com.wewins.fota.mapper.DeviceGroupMapper;
import com.wewins.fota.mapper.DeviceGroupMemberMapper;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class DeviceGroupService {

    @Autowired
    private DeviceGroupMapper deviceGroupMapper;

    @Autowired
    private DeviceGroupMemberMapper deviceGroupMemberMapper;

    /**
     * 创建设备分组
     */
    public void createGroup(String name, String remark) {
        DeviceGroup group = DeviceGroup.builder()
                .name(name)
                .remark(remark)
                .build();
        deviceGroupMapper.insert(group);
    }

    /**
     * 将设备添加到分组
     */
    public void addDeviceToGroup(Long deviceId, Long groupId) {
        DeviceGroupMember member = DeviceGroupMember.builder()
                .deviceId(deviceId)
                .groupId(groupId)
                .build();
        deviceGroupMemberMapper.insert(member);
    }

    /**
     * 将设备批量添加到分组
     */
    public void batchAddDevicesToGroup(List<Long> deviceIds, Long groupId) {
        List<DeviceGroupMember> members = deviceIds.stream()
                .map(deviceId -> DeviceGroupMember.builder()
                        .deviceId(deviceId)
                        .groupId(groupId)
                        .build())
                .collect(Collectors.toList());

        // 使用 MyBatis-Plus 的批量插入
        // 注意：PostgreSQL 需要配置 JDBC URL: reWriteBatchedInserts=true
        members.forEach(member -> deviceGroupMemberMapper.insert(member));
    }

    /**
     * 移除设备从分组（软删除）
     */
    public void removeDeviceFromGroup(Long deviceId, Long groupId) {
        QueryWrapper<DeviceGroupMember> wrapper = new QueryWrapper<>();
        wrapper.eq("device_id", deviceId)
               .eq("group_id", groupId);

        // MyBatis-Plus 会自动处理软删除（设置 deleted_at）
        deviceGroupMemberMapper.delete(wrapper);
    }
}
```

---

## SQL 查询示例

### 1. 标签查询

#### 查询包含某个标签的设备

```sql
-- 查询所有带"测试设备"标签的设备
SELECT *
FROM devices
WHERE deleted_at IS NULL
  AND tags @> '["测试设备"]'::jsonb;
```

#### 查询包含多个标签的设备（AND 逻辑）

```sql
-- 查询同时包含"测试设备"和"CN"标签的设备
SELECT *
FROM devices
WHERE deleted_at IS NULL
  AND tags @> '["测试设备", "CN"]'::jsonb;
```

#### 查询包含任意一个标签的设备（OR 逻辑）

```sql
-- 查询包含"测试设备"或"beta"标签的设备
SELECT *
FROM devices
WHERE deleted_at IS NULL
  AND (tags ? '测试设备' OR tags ? 'beta');
```

#### 查询标签数量

```sql
-- 查询有多少个设备包含"CN"标签
SELECT COUNT(*)
FROM devices
WHERE deleted_at IS NULL
  AND tags @> '["CN"]'::jsonb;
```

---

### 2. 分组查询

#### 查询某个分组的所有设备

```sql
SELECT d.*
FROM devices d
JOIN device_group_members m ON m.device_id = d.id
WHERE m.group_id = :groupId
  AND d.deleted_at IS NULL
  AND m.deleted_at IS NULL;
```

#### 查询设备所属的所有分组

```sql
SELECT g.*
FROM device_groups g
JOIN device_group_members m ON m.group_id = g.id
WHERE m.device_id = :deviceId
  AND g.deleted_at IS NULL
  AND m.deleted_at IS NULL;
```

#### 查询分组下的设备数量

```sql
SELECT COUNT(*)
FROM device_group_members m
JOIN devices d ON d.id = m.device_id
WHERE m.group_id = :groupId
  AND d.deleted_at IS NULL
  AND m.deleted_at IS NULL;
```

---

### 3. 组合查询（标签 + 分组）

#### 查询某分组中包含特定标签的设备

```sql
SELECT d.*
FROM devices d
JOIN device_group_members m ON m.device_id = d.id
WHERE m.group_id = :groupId
  AND d.deleted_at IS NULL
  AND m.deleted_at IS NULL
  AND d.tags @> '["测试设备"]'::jsonb;
```

#### 查询某产品下某分组中包含特定标签的设备

```sql
SELECT d.*
FROM devices d
JOIN device_group_members m ON m.device_id = d.id
WHERE d.product_id = :productId
  AND m.group_id = :groupId
  AND d.deleted_at IS NULL
  AND m.deleted_at IS NULL
  AND d.tags @> '["beta"]'::jsonb;
```

---

## 升级策略筛选示例

在实际的升级策略中，可以基于标签和分组筛选目标设备：

```java
/**
 * 根据标签和分组筛选设备
 */
public List<Device> findDevicesForUpgrade(Long productId, List<Long> groupIds, List<String> requiredTags) {
    QueryWrapper<Device> wrapper = new QueryWrapper<>();

    // 基础条件
    wrapper.eq("product_id", productId)
           .eq("status", "ACTIVE")
           .isNull("deleted_at");

    // 分组条件（使用子查询，避免 SQL 注入）
    if (groupIds != null && !groupIds.isEmpty()) {
        // 方式1：使用 MyBatis-Plus 的 exists 子查询（推荐）
        wrapper.exists("SELECT 1 FROM device_group_members m " +
                      "WHERE m.device_id = t.id " +
                      "AND m.group_id IN ({0})", groupIds);

        // 方式2：使用自定义 Mapper 方法（更灵活）
        // return deviceMapper.selectByProductAndGroups(productId, groupIds);
    }

    // 标签条件需要在 SQL 中处理，因为 MyBatis-Plus 不直接支持 JSONB 操作
    // 建议使用自定义 Mapper 方法

    return deviceMapper.selectList(wrapper);
}
```

### 自定义 Mapper 方法

```java
/**
 * DeviceMapper.java
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /**
     * 根据标签查询设备
     */
    @Select("SELECT * FROM devices " +
            "WHERE deleted_at IS NULL " +
            "AND tags @> #{tagsJson}::jsonb")
    List<Device> selectByTags(@Param("tagsJson") String tagsJson);

    /**
     * 根据分组和标签查询设备
     */
    @Select("SELECT d.* FROM devices d " +
            "JOIN device_group_members m ON m.device_id = d.id " +
            "WHERE m.group_id = #{groupId} " +
            "AND d.deleted_at IS NULL " +
            "AND m.deleted_at IS NULL " +
            "AND d.tags @> #{tagsJson}::jsonb")
    List<Device> selectByGroupAndTags(@Param("groupId") Long groupId,
                                       @Param("tagsJson") String tagsJson);
}
```

使用示例：

```java
// 查询包含"测试设备"和"CN"标签的设备
String tagsJson = "[\"测试设备\", \"CN\"]";
List<Device> devices = deviceMapper.selectByTags(tagsJson);

// 查询某分组中包含"beta"标签的设备
String tagsJson = "[\"beta\"]";
List<Device> devices = deviceMapper.selectByGroupAndTags(1L, tagsJson);
```

---

## 性能优化建议

### 1. 标签规范化
- 统一标签大小写（建议大写）
- 使用标准化的标签值
- 避免使用特殊字符

```java
// 不推荐
List<String> tags = Arrays.asList("测试设备", "cn", "Beta");

// 推荐
List<String> tags = Arrays.asList("TEST_DEVICE", "CN", "BETA");
```

### 2. 索引优化
- `tags` 字段已创建 GIN 索引，支持高效的 JSONB 查询
- `device_group_members` 的 `group_id` 和 `device_id` 已创建索引

### 3. 缓存策略
对于高频的升级策略查询，建议使用 Redis 缓存：

```java
/**
 * 将策略匹配的设备ID列表缓存到 Redis
 */
public void cacheStrategyDevices(Long policyId, List<Long> deviceIds) {
    String key = "strategy:devices:" + policyId;
    redisTemplate.opsForSet().add(key, deviceIds.toArray());
    redisTemplate.expire(key, 1, TimeUnit.HOURS);
}

/**
 * 从缓存获取策略设备
 */
public Set<Long> getCachedStrategyDevices(Long policyId) {
    String key = "strategy:devices:" + policyId;
    return redisTemplate.opsForSet().members(key);
}
```

---

## 常见问题

### Q1: TypeHandler 报错怎么办？

**A**: 确保已添加 Jackson 依赖：

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

### Q2: JSONB 查询性能如何？

**A**: GIN 索引可以显著提升查询性能。对于百万级设备，标签查询通常在毫秒级。

### Q3: 如何处理标签的并发更新？

**A**: 建议使用乐观锁或版本号控制：

```java
// 使用版本号防止并发冲突
@Version
private Integer version;
```

---

## 总结

- **标签**：适合灵活的多维度分类（测试、地区、版本等）
- **分组**：适合固定的组织结构（部门、业务线等）
- **组合使用**：可以实现复杂的设备筛选逻辑
- **性能优化**：GIN 索引 + Redis 缓存

---

**最后更新**：2026-02-05
**作者**：FOTA Team
