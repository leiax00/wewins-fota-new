# 升级策略使用指南

## 概述

`upgrade_policies` 表存储固件升级策略，支持灵活的设备筛选、版本过滤、时间窗口等配置。

---

## 核心概念

### 策略匹配流程

当一个设备检查更新时：
1. 找到所有匹配该产品的策略
2. 按条件筛选（源版本、设备ID/标签）
3. 匹配到多个策略时，按规则选择最优策略
4. **选择规则**：
   - 规则1：最晚发布的目标版本优先（`firmware_versions.created_at DESC`）
   - 规则2：同发布时间按策略优先级（`upgrade_policies.priority DESC`）

---

## 字段说明

### 1. source_versions（源版本列表）

**类型**：JSONB 数组
**用途**：指定哪些源版本可以升级到此目标版本

**示例**：
```json
["1.0.0", "1.0.1", "1.0.2-beta"]
```

**含义**：当前版本是 1.0.0、1.0.1 或 1.0.2-beta 的设备都可以升级

**NULL 值**：表示不限制源版本

---

### 2. target_device_ids（指定设备列表）

**类型**：JSONB 数组
**用途**：当设备数量 ≤10 个时，直接指定设备ID

**示例**：
```json
[1001, 1002, 1003]
```

**NULL 值**：表示不限制设备ID

---

### 3. target_device_tags（设备标签过滤）

**类型**：JSONB 对象
**用途**：当设备数量 >10 个时，通过标签筛选设备

**示例**：
```json
{
  "all": ["CN", "VIP"],
  "any": ["beta", "pilot"],
  "none": ["blocked"]
}
```

**含义**：
- `all`：必须包含（AND 逻辑）
- `any`：包含任一（OR 逻辑）
- `none`：必须不包含（NOT 逻辑）

**NULL 值**：表示不限制设备标签

---

### 4. trigger_mode（触发模式）

**类型**：VARCHAR(20)
**可选值**：
- `AUTO`：系统自动触发推送
- `MANUAL`：人工确认触发推送

**默认值**：`AUTO`

---

### 5. time_window（时间窗口）

**类型**：JSONB 对象
**用途**：指定策略生效的时间窗口

#### 类型 A：固定日期范围

```json
{
  "type": "range",
  "start_at": "2025-02-01T00:00:00+08:00",
  "end_at": "2025-02-10T23:59:59+08:00"
}
```

#### 类型 B：每天固定时间段

```json
{
  "type": "daily",
  "timezone": "Asia/Shanghai",
  "start_time": "02:00",
  "end_time": "06:00"
}
```

**NULL 值**：表示不限制时间

---

## Java 使用示例

### 1. 创建升级策略

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.entity.UpgradePolicy;
import com.wewins.fota.mapper.UpgradePolicyMapper;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class UpgradePolicyService {

    @Autowired
    private UpgradePolicyMapper upgradePolicyMapper;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 创建升级策略（完整示例）
     */
    public void createUpgradePolicy() {
        UpgradePolicy policy = new UpgradePolicy();
        policy.setProductId(1L);
        policy.setName("安全补丁推送");
        policy.setRemark("修复蓝牙安全漏洞");
        policy.setTargetVersionId(100L);
        policy.setPriority(100);
        policy.setGrayRate(50);  // 50% 灰度
        policy.setTriggerMode("AUTO");

        // 设置源版本列表
        ArrayNode sourceVersions = OBJECT_MAPPER.createArrayNode();
        sourceVersions.add("1.0.0");
        sourceVersions.add("1.0.1");
        sourceVersions.add("1.0.2");
        policy.setSourceVersions(sourceVersions);

        // 设置指定设备列表（小规模）
        ArrayNode targetDeviceIds = OBJECT_MAPPER.createArrayNode();
        targetDeviceIds.add(1001);
        targetDeviceIds.add(1002);
        targetDeviceIds.add(1003);
        policy.setTargetDeviceIds(targetDeviceIds);

        // 设置时间窗口（每天凌晨2-6点）
        ObjectNode timeWindow = OBJECT_MAPPER.createObjectNode();
        timeWindow.put("type", "daily");
        timeWindow.put("timezone", "Asia/Shanghai");
        timeWindow.put("start_time", "02:00");
        timeWindow.put("end_time", "06:00");
        policy.setTimeWindow(timeWindow);

        upgradePolicyMapper.insert(policy);
    }

    /**
     * 创建基于标签的策略（大规模设备）
     */
    public void createTagBasedPolicy() {
        UpgradePolicy policy = new UpgradePolicy();
        policy.setProductId(1L);
        policy.setName("CN地区VIP用户推送");
        policy.setTargetVersionId(101L);
        policy.setPriority(80);
        policy.setTriggerMode("MANUAL");

        // 设置源版本列表
        ArrayNode sourceVersions = OBJECT_MAPPER.createArrayNode();
        sourceVersions.add("1.0.0");
        policy.setSourceVersions(sourceVersions);

        // 设置设备标签过滤
        ObjectNode targetDeviceTags = OBJECT_MAPPER.createObjectNode();
        ArrayNode allTags = OBJECT_MAPPER.createArrayNode();
        allTags.add("CN");
        allTags.add("VIP");
        targetDeviceTags.set("all", allTags);
        policy.setTargetDeviceTags(targetDeviceTags);

        upgradePolicyMapper.insert(policy);
    }
}
```

### 2. 查询匹配设备的策略

```java
/**
 * 查询匹配某个设备的所有升级策略
 */
public List<UpgradePolicy> findMatchingPolicies(Long deviceId) {
    // 使用自定义 Mapper 方法查询
    return upgradePolicyMapper.selectByDeviceId(deviceId);
}
```

---

## SQL 查询示例

### 1. 匹配某个设备的所有策略

```sql
SELECT p.*, fv.version as target_version
FROM upgrade_policies p
JOIN devices d ON d.id = :deviceId
JOIN firmware_versions fv ON fv.id = p.target_version_id
WHERE p.product_id = d.product_id
  AND p.deleted_at IS NULL
  AND d.deleted_at IS NULL

  -- 源版本过滤
  AND (p.source_versions IS NULL
       OR p.source_versions @> to_jsonb(ARRAY[d.current_version]::text[]))

  -- 设备ID过滤
  AND (p.target_device_ids IS NULL
       OR p.target_device_ids @> to_jsonb(ARRAY[d.id]::bigint[]))

  -- 设备标签过滤
  AND (p.target_device_tags IS NULL
       OR d.tags @> p.target_device_tags)

ORDER BY fv.created_at DESC, p.priority DESC;
```

### 2. 查询某个产品的所有策略

```sql
SELECT p.*, fv.version as target_version, fv.created_at as version_created_at
FROM upgrade_policies p
JOIN firmware_versions fv ON fv.id = p.target_version_id
WHERE p.product_id = :productId
  AND p.deleted_at IS NULL
ORDER BY fv.created_at DESC, p.priority DESC;
```

### 3. 查询自动触发的策略

```sql
SELECT *
FROM upgrade_policies
WHERE trigger_mode = 'AUTO'
  AND deleted_at IS NULL;
```

### 4. 查询指定源版本的策略

```sql
SELECT *
FROM upgrade_policies
WHERE source_versions @> '["1.0.0"]'::jsonb
  AND deleted_at IS NULL;
```

---

## 自定义 Mapper 方法

### UpgradePolicyMapper.java

```java
@Mapper
public interface UpgradePolicyMapper extends BaseMapper<UpgradePolicy> {

    /**
     * 查询匹配某个设备的所有策略
     */
    @Select("SELECT p.*, fv.version as target_version, fv.created_at as version_created_at " +
            "FROM upgrade_policies p " +
            "JOIN devices d ON d.id = #{deviceId} " +
            "JOIN firmware_versions fv ON fv.id = p.target_version_id " +
            "WHERE p.product_id = d.product_id " +
            "  AND p.deleted_at IS NULL " +
            "  AND d.deleted_at IS NULL " +
            "  AND (p.source_versions IS NULL " +
            "       OR p.source_versions @> to_jsonb(ARRAY[d.current_version]::text[])) " +
            "  AND (p.target_device_ids IS NULL " +
            "       OR p.target_device_ids @> to_jsonb(ARRAY[d.id]::bigint[])) " +
            "  AND (p.target_device_tags IS NULL " +
            "       OR d.tags @> p.target_device_tags) " +
            "ORDER BY fv.created_at DESC, p.priority DESC")
    List<PolicyWithVersionDTO> selectByDeviceId(@Param("deviceId") Long deviceId);

    /**
     * 查询某个产品最晚的策略
     */
    @Select("SELECT p.* " +
            "FROM upgrade_policies p " +
            "JOIN firmware_versions fv ON fv.id = p.target_version_id " +
            "WHERE p.product_id = #{productId} " +
            "  AND p.deleted_at IS NULL " +
            "ORDER BY fv.created_at DESC, p.priority DESC " +
            "LIMIT 1")
    UpgradePolicy selectLatestByProductId(@Param("productId") Long productId);
}
```

---

## 策略匹配规则详解

### 场景：一个设备匹配多个策略

假设设备的当前版本是 `1.0.0`，有两个匹配的策略：

| 策略 | 目标版本 | 优先级 | 目标版本创建时间 |
|------|---------|--------|----------------|
| 策略 A | 2.0.0 | 100 | 2025-02-01 |
| 策略 B | 2.1.0 | 80 | 2025-02-05 |

### 选择结果
✅ **选择策略 B**

**原因**：
- 策略 B 的目标版本（2.1.0）创建时间更晚（2025-02-05 > 2025-02-01）
- 即使策略 A 的优先级更高（100 > 80），也要先看发布时间

### 场景：同一天发布的多个策略

| 策略 | 目标版本 | 优先级 | 目标版本创建时间 |
|------|---------|--------|----------------|
| 策略 A | 2.0.0 | 100 | 2025-02-05 10:00 |
| 策略 B | 2.1.0 | 80 | 2025-02-05 10:00 |

### 选择结果
✅ **选择策略 A**

**原因**：
- 目标版本创建时间相同
- 策略 A 的优先级更高（100 > 80）

---

## 时间窗口处理

### 检查策略是否在有效期内

```java
/**
 * 检查策略是否在有效期内
 */
public boolean isPolicyActive(UpgradePolicy policy) {
    if (policy.getTimeWindow() == null) {
        return true;  // 无时间限制
    }

    JsonNode timeWindow = policy.getTimeWindow();
    String type = timeWindow.get("type").asText();

    if ("range".equals(type)) {
        return checkRangeTimeWindow(timeWindow);
    } else if ("daily".equals(type)) {
        return checkDailyTimeWindow(timeWindow);
    }

    return true;
}

/**
 * 检查固定日期范围窗口
 */
private boolean checkRangeTimeWindow(JsonNode timeWindow) {
    try {
        String startAt = timeWindow.get("start_at").asText();
        String endAt = timeWindow.get("end_at").asText();

        LocalDateTime start = LocalDateTime.parse(startAt);
        LocalDateTime end = LocalDateTime.parse(endAt);
        LocalDateTime now = LocalDateTime.now();

        return now.isAfter(start) && now.isBefore(end);
    } catch (Exception e) {
        return true;  // 解析失败，默认有效
    }
}

/**
 * 检查每天固定时间段窗口
 */
private boolean checkDailyTimeWindow(JsonNode timeWindow) {
    try {
        String startTime = timeWindow.get("start_time").asText();
        String endTime = timeWindow.get("end_time").asText();
        String timezone = timeWindow.get("timezone").asText();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timezone));
        LocalTime current = now.toLocalTime();

        LocalTime start = LocalTime.parse(startTime);
        LocalTime end = LocalTime.parse(endTime);

        return !current.isBefore(start) && !current.isAfter(end);
    } catch (Exception e) {
        return true;  // 解析失败，默认有效
    }
}
```

---

## 完整流程示例

### 设备检查更新流程

```java
/**
 * 设备检查更新（完整流程）
 */
public UpgradeCheckResponse checkUpgrade(Long deviceId) {
    // 1. 查询设备信息
    Device device = deviceMapper.selectById(deviceId);
    if (device == null) {
        throw new DeviceNotFoundException();
    }

    // 2. 查询匹配的所有策略
    List<PolicyWithVersionDTO> policies = upgradePolicyMapper.selectByDeviceId(deviceId);

    // 3. 遍历策略，找到第一个匹配的
    for (PolicyWithVersionDTO policy : policies) {
        // 3.1 检查灰度比例
        if (!checkGrayRate(device, policy)) {
            continue;
        }

        // 3.2 检查时间窗口
        if (!isPolicyActive(policy.getPolicy())) {
            continue;
        }

        // 3.3 检查触发模式
        if ("MANUAL".equals(policy.getPolicy().getTriggerMode())) {
            // 手动策略需要人工确认
            return UpgradeCheckResponse.manualRequired(policy);
        }

        // 3.4 找到匹配的策略，返回升级信息
        FirmwareVersion targetVersion = firmwareVersionMapper.selectById(
            policy.getPolicy().getTargetVersionId()
        );

        return UpgradeCheckResponse.success(targetVersion);
    }

    // 4. 没有匹配的策略
    return UpgradeCheckResponse.noUpdate();
}

/**
 * 检查灰度比例
 */
private boolean checkGrayRate(Device device, PolicyWithVersionDTO policy) {
    int grayRate = policy.getPolicy().getGrayRate();
    if (grayRate >= 100) {
        return true;  // 100% 灰度，全部升级
    }

    if (grayRate <= 0) {
        return false;  // 0% 灰度，不升级
    }

    // 计算设备是否命中灰度
    int bucket = Math.abs(device.getImei().hashCode()) % 100;
    return bucket < grayRate;
}
```

---

## 最佳实践

### 1. 策略命名规范

- **安全补丁**：`安全补丁_20250205_CVE-2025-12345`
- **功能升级**：`功能升级_新蓝牙驱动_v2.1.0`
- **灰度测试**：`灰度测试_v2.1.0_CN地区_50%`

### 2. 优先级设置

| 场景 | 建议优先级 |
|------|-----------|
| 安全修复（critical） | 100 |
| 重要功能 | 80-90 |
| 普通功能 | 50-70 |
| 灰度测试 | 30-40 |

### 3. 时间窗口设置

**推荐**：
- **安全补丁**：固定日期范围，尽快推送
- **功能升级**：每天凌晨2-6点，减少影响
- **灰度测试**：分阶段，每阶段1-2天

### 4. 灰度比例设置

| 阶段 | 灰度比例 | 时长 |
|------|---------|------|
| 内测 | 5% | 1-2天 |
| 小规模灰度 | 20% | 2-3天 |
| 大规模灰度 | 50% | 3-5天 |
| 全量 | 100% | - |

---

## 常见问题

### Q1: 如何处理策略冲突？

**A**: 按规则自动选择：
1. 最晚发布的目标版本优先
2. 同发布时间按策略优先级

### Q2: 如何回滚版本？

**A**: 创建回滚策略：
```json
{
  "source_versions": ["2.1.0", "2.1.1"],
  "target_version_id": <1.5.0的版本ID>,
  "priority": 200  // 高优先级
}
```

### Q3: 如何紧急停止策略？

**A**:
1. 软删除策略（设置 deleted_at）
2. 或将灰度比例设为 0

---

## 总结

- **源版本列表**：指定允许升级的版本
- **设备筛选**：支持 ID 列表和标签过滤
- **多策略选择**：最晚发布 > 同期优先级
- **时间窗口**：支持范围和每日时间段
- **触发模式**：自动/手动

---

**最后更新**：2026-02-05
**作者**：FOTA Team
