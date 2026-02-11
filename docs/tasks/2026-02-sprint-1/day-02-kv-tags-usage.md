# KV 标签字段使用指南

## 概述

`tags` 字段采用 **KV（键值对）结构**，使用 JSONB 对象存储，支持灵活的元数据和复杂的过滤条件。

### 设计优势

相比数组结构，KV 对象的优势：
- ✅ 更丰富的信息（不仅仅是标签名）
- ✅ 支持标签属性（优先级、来源、时间等）
- ✅ 灵活的查询方式（按键值过滤）
- ✅ 易于扩展和演进

---

## 数据结构

### 设备标签示例

```json
{
  "environment": "测试",
  "region": "CN",
  "network": "5G",
  "user_level": "VIP",
  "tester": "张三",
  "test_phase": "alpha",
  "registered_at": "2025-02-05",
  "is_beta_user": true
}
```

### 版本标签示例

```json
{
  "stability": "stable",
  "priority": "high",
  "category": "security-fix",
  "verified_by": "security-team",
  "verified_at": "2025-02-05T10:30:00",
  "rollback_available": true,
  "min_api_level": 23,
  "requires_reboot": true
}
```

---

## Java 使用示例

### 1. 设置设备标签

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.entity.Device;
import com.wewins.fota.mapper.DeviceMapper;

@Service
public class DeviceService {

    @Autowired
    private DeviceMapper deviceMapper;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 创建设备并设置标签
     */
    public void createDeviceWithTags() {
        Device device = new Device();
        device.setImei("123456789012345");
        device.setProductId(1L);
        device.setStatus("ACTIVE");

        // 构建标签对象
        ObjectNode tags = OBJECT_MAPPER.createObjectNode();
        tags.put("environment", "测试");
        tags.put("region", "CN");
        tags.put("network", "5G");
        tags.put("user_level", "VIP");
        tags.put("tester", "张三");
        tags.put("test_phase", "alpha");
        tags.put("is_beta_user", true);

        device.setTags(tags);

        // 插入数据库
        deviceMapper.insert(device);
    }

    /**
     * 更新设备标签
     */
    public void updateDeviceTags(Long deviceId, Map<String, Object> newTags) {
        Device device = deviceMapper.selectById(deviceId);
        if (device != null) {
            // 获取现有标签
            ObjectNode tags = (ObjectNode) device.getTags();
            if (tags == null) {
                tags = OBJECT_MAPPER.createObjectNode();
            }

            // 合并新标签
            newTags.forEach((key, value) -> {
                if (value instanceof String) {
                    tags.put(key, (String) value);
                } else if (value instanceof Boolean) {
                    tags.put(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    tags.put(key, (Integer) value);
                } else if (value instanceof Long) {
                    tags.put(key, (Long) value);
                } else {
                    // 其他类型转为字符串
                    tags.put(key, String.valueOf(value));
                }
            });

            device.setTags(tags);
            deviceMapper.updateById(device);
        }
    }

    /**
     * 获取标签值
     */
    public String getTagValue(Device device, String key) {
        if (device.getTags() != null) {
            JsonNode valueNode = device.getTags().get(key);
            if (valueNode != null && !valueNode.isNull()) {
                return valueNode.asText();
            }
        }
        return null;
    }
}
```

### 2. 设置版本标签

```java
/**
 * 创建固件版本并设置标签
 */
public void createFirmwareVersionWithTags() {
    FirmwareVersion version = new FirmwareVersion();
    version.setProductId(1L);
    version.setVersion("1.2.0");
    version.setFileUrl("https://cdn.example.com/firmware/v1.2.0.bin");
    version.setFileSize(47657472L);
    version.setMd5("abc123...");
    version.setSha256("def456...");

    // 构建标签对象
    ObjectNode tags = OBJECT_MAPPER.createObjectNode();
    tags.put("stability", "stable");
    tags.put("priority", "high");
    tags.put("category", "security-fix");
    tags.put("verified_by", "security-team");
    tags.put("verified_at", "2025-02-05T10:30:00");
    tags.put("rollback_available", true);
    tags.put("min_api_level", 23);

    version.setTags(tags);
    firmwareVersionMapper.insert(version);
}
```

---

## SQL 查询示例

### 1. 精确匹配

```sql
-- 查询 environment = "测试" 的设备
SELECT *
FROM devices
WHERE deleted_at IS NULL
  AND tags->>'environment' = '测试';
```

```sql
-- 查询 stability = "stable" 的版本
SELECT *
FROM firmware_versions
WHERE deleted_at IS NULL
  AND tags->>'stability' = 'stable';
```

### 2. 多条件组合

```sql
-- 查询 CN 地区的 5G 设备
SELECT *
FROM devices
WHERE deleted_at IS NULL
  AND tags->>'region' = 'CN'
  AND tags->>'network' = '5G';
```

```sql
-- 查询高优先级的安全修复版本
SELECT *
FROM firmware_versions
WHERE deleted_at IS NULL
  AND tags->>'priority' = 'high'
  AND tags->>'category' = 'security-fix';
```

### 3. 存在性检查

```sql
-- 查询有 tester 标签的设备
SELECT *
FROM devices
WHERE deleted_at IS NULL
  AND tags ? 'tester';
```

```sql
-- 查询有 verified_by 标签的版本
SELECT *
FROM firmware_versions
WHERE deleted_at IS NULL
  AND tags ? 'verified_by';
```

### 4. 类型检查

```sql
-- 查询 is_beta_user = true 的设备
SELECT *
FROM devices
WHERE deleted_at IS NULL
  AND (tags->>'is_beta_user')::boolean = true;
```

```sql
-- 查询 min_api_level >= 23 的版本
SELECT *
FROM firmware_versions
WHERE deleted_at IS NULL
  AND (tags->>'min_api_level')::integer >= 23;
```

### 5. 复杂查询（组合）

```sql
-- 查询 CN 地区的 VIP 设备，且是 5G 网络
SELECT *
FROM devices
WHERE deleted_at IS NULL
  AND tags->>'region' = 'CN'
  AND tags->>'user_level' = 'VIP'
  AND tags->>'network' = '5G';
```

```sql
-- 查询稳定版本，优先级为 high，已验证
SELECT *
FROM firmware_versions
WHERE product_id = :productId
  AND deleted_at IS NULL
  AND tags->>'stability' = 'stable'
  AND tags->>'priority' = 'high'
  AND tags ? 'verified_by'
ORDER BY version DESC;
```

---

## 自定义 Mapper 方法

### DeviceMapper.java

```java
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /**
     * 根据标签键值查询设备
     */
    @Select("SELECT * FROM devices " +
            "WHERE deleted_at IS NULL " +
            "AND tags->>#{key} = #{value}")
    List<Device> selectByTag(
        @Param("key") String key,
        @Param("value") String value
    );

    /**
     * 根据多个标签条件查询
     */
    @Select("SELECT * FROM devices " +
            "WHERE deleted_at IS NULL " +
            "AND tags->>#{key1} = #{value1} " +
            "AND tags->>#{key2} = #{value2}")
    List<Device> selectByTags(
        @Param("key1") String key1,
        @Param("value1") String value1,
        @Param("key2") String key2,
        @Param("value2") String value2
    );

    /**
     * 查询包含某个键的设备
     */
    @Select("SELECT * FROM devices " +
            "WHERE deleted_at IS NULL " +
            "AND tags ? #{key}")
    List<Device> selectByTagKey(@Param("key") String key);
}
```

### FirmwareVersionMapper.java

```java
@Mapper
public interface FirmwareVersionMapper extends BaseMapper<FirmwareVersion> {

    /**
     * 根据标签查询版本
     */
    @Select("SELECT * FROM firmware_versions " +
            "WHERE product_id = #{productId} " +
            "AND deleted_at IS NULL " +
            "AND tags->>#{key} = #{value} " +
            "ORDER BY version DESC")
    List<FirmwareVersion> selectByTag(
        @Param("productId") Long productId,
        @Param("key") String key,
        @Param("value") String value
    );

    /**
     * 查询多个标签条件的版本
     */
    @Select("SELECT * FROM firmware_versions " +
            "WHERE product_id = #{productId} " +
            "AND deleted_at IS NULL " +
            "AND tags->>'stability' = #{stability} " +
            "AND tags->>'priority' = #{priority} " +
            "ORDER BY version DESC")
    List<FirmwareVersion> selectByStabilityAndPriority(
        @Param("productId") Long productId,
        @Param("stability") String stability,
        @Param("priority") String priority
    );
}
```

---

## 标签命名规范

### 推荐的键名

#### 设备标签
| 键名 | 类型 | 示例值 | 说明 |
|------|------|--------|------|
| environment | String | 测试/生产 | 环境类型 |
| region | String | CN/US/JP | 地区 |
| network | String | 4G/5G/WiFi | 网络类型 |
| user_level | String | VIP/普通 | 用户等级 |
| tester | String | 张三 | 测试人员 |
| test_phase | String | alpha/beta | 测试阶段 |
| registered_at | String | 2025-02-05 | 注册日期 |
| is_beta_user | Boolean | true | 是否测试用户 |

#### 版本标签
| 键名 | 类型 | 示例值 | 说明 |
|------|------|--------|------|
| stability | String | stable/beta/alpha | 稳定性级别 |
| priority | String | high/medium/low | 优先级 |
| category | String | security-fix/bug-fix/feature | 更新类别 |
| verified_by | String | security-team | 验证团队 |
| verified_at | String | 2025-02-05T10:30:00 | 验证时间 |
| rollback_available | Boolean | true | 是否可回滚 |
| min_api_level | Integer | 23 | 最低 API 级别 |
| requires_reboot | Boolean | true | 是否需要重启 |

---

## 使用场景

### 场景 1：升级策略 - 筛选目标设备

```java
/**
 * 根据标签筛选目标设备
 */
public List<Device> findTargetDevices(UpgradePolicy policy) {
    QueryWrapper<Device> wrapper = new QueryWrapper<>();
    wrapper.eq("product_id", policy.getProductId())
           .eq("status", "ACTIVE")
           .isNull("deleted_at");

    // 添加标签过滤
    // 示例：只推送给 CN 地区的 5G VIP 用户
    wrapper.apply("tags->>'region' = 'CN'")
           .apply("tags->>'network' = '5G'")
           .apply("tags->>'user_level' = 'VIP'");

    return deviceMapper.selectList(wrapper);
}
```

### 场景 2：版本管理 - 筛选目标版本

```java
/**
 * 查找适合某设备的版本
 */
public FirmwareVersion findTargetVersion(Device device) {
    // 获取设备的地区信息
    String region = getTagValue(device, "region");
    String network = getTagValue(device, "network");

    // 根据设备属性查找合适的版本
    QueryWrapper<FirmwareVersion> wrapper = new QueryWrapper<>();
    wrapper.eq("product_id", device.getProductId())
           .isNull("deleted_at")
           .eq("tags->>'stability'", "stable");

    // 如果是 VIP 用户，优先推送高优先级版本
    if ("VIP".equals(getTagValue(device, "user_level"))) {
        wrapper.eq("tags->>'priority'", "high");
    }

    wrapper.orderByDesc("version")
           .last("LIMIT 1");

    return firmwareVersionMapper.selectOne(wrapper);
}
```

### 场景 3：设备分组 - 基于标签分组

```java
/**
 * 根据标签分组统计
 */
public Map<String, Long> groupDevicesByTag(String tagKey) {
    List<Device> devices = deviceMapper.selectList(
        new QueryWrapper<Device>().isNull("deleted_at")
    );

    return devices.stream()
        .filter(device -> device.getTags() != null)
        .filter(device -> device.getTags().has(tagKey))
        .collect(Collectors.groupingBy(
            device -> device.getTags().get(tagKey).asText(),
            Collectors.counting()
        ));
}
```

---

## 性能优化

### 索引策略

虽然 tags 字段有 GIN 索引，但对于特定键的查询，可以考虑：

1. **高频键值单独索引**（如果查询非常频繁）：
```sql
-- 为 environment 键创建表达式索引
CREATE INDEX idx_devices_tags_environment
ON devices ((tags->>'environment'));

-- 为 stability 键创建表达式索引
CREATE INDEX idx_fv_tags_stability
ON firmware_versions ((tags->>'stability'));
```

2. **部分索引**（只索引常用值）：
```sql
-- 只索引 production 环境的设备
CREATE INDEX idx_devices_tags_production
ON devices ((tags->>'environment'))
WHERE tags->>'environment' = '生产';
```

### 查询优化建议

1. **优先使用等值查询**：`tags->>'key' = 'value'`
2. **避免深度嵌套**：`tags->'a'->'b'->>'c'` 性能较差
3. **考虑冗余字段**：对于高频查询的键，可以考虑冗余到单独字段

---

## 常见问题

### Q1: 如何修改标签值？

**A**:
```java
Device device = deviceMapper.selectById(deviceId);
ObjectNode tags = (ObjectNode) device.getTags();
tags.put("environment", "生产");  // 修改
tags.remove("tester");             // 删除
device.setTags(tags);
deviceMapper.updateById(device);
```

### Q2: 如何查询包含某个键但不关心值的设备？

**A**:
```sql
SELECT * FROM devices
WHERE deleted_at IS NULL
  AND tags ? 'environment';
```

### Q3: KV 标签和数组标签哪个更好？

**A**:
- **KV 对象**：更灵活，适合需要存储元数据的场景（推荐）
- **数组**：更简单，适合只需要标签名的场景

---

## 总结

- **结构**：JSONB 对象（KV 键值对）
- **Java 类型**：JsonNode
- **TypeHandler**：JsonNodeTypeHandler
- **查询**：支持精确匹配、存在性检查、类型检查
- **索引**：GIN 索引 + 表达式索引（可选）

---

**最后更新**：2026-02-05
**作者**：FOTA Team
