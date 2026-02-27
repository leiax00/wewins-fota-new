# 策略管理功能完善实施计划

## 上下文

当前策略管理界面缺失重要字段，导致无法实现完整的升级策略配置。数据库表`upgrade_policies`已包含所有字段，但前端和DTO层未实现。

### 现状问题
1. **时间配置**：界面只有`planTime`单点时间，需要改为`timeWindow`时间窗口
2. **源版本限制**：无法指定哪些源版本可以升级
3. **触发方式**：无法选择自动/手动触发模式
4. **目标设备**：无法按ID/批次/标签精确筛选设备
5. **planTime废弃**：直接切换到timeWindow，不做兼容

### 目标
实现完整的策略配置功能，支持：
- 时间窗口（RANGE固定范围 / DAILY每日周期）
- 源版本限制
- 触发模式（AUTO自动 / MANUAL手动）
- 目标设备筛选（ID列表 / 批次 / 标签 / 全量）
- 前端UI优化（单选卡片、二次确认）

---

## 核心设计决策

### 时间窗口DTO设计

**统一使用ISO8601 UTC时间戳**，避免时区问题：

```java
public class TimeWindowDTO {
    private String type;  // RANGE | DAILY
    private String startAt;  // ISO8601 UTC时间戳
    private String endAt;    // ISO8601 UTC时间戳
}
```

| type | startAt/endAt格式 | 示例 | 约定 |
|------|-------------------|------|------|
| **RANGE** | ISO8601 UTC | `2025-02-01T00:00:00Z` 到 `2025-02-10T23:59:59Z` | 可跨越多天，无时间跨度限制 |
| **DAILY** | ISO8601 UTC | `2025-02-01T02:00:00Z` 到 `2025-02-01T06:00:00Z` | 约定不超过24小时，支持跨日 |

**DAILY跨日示例**：
- 同一天：`2025-02-01T02:00:00Z` 到 `2025-02-01T06:00:00Z`
- 跨日：`2025-02-01T23:00:00Z` 到 `2025-02-02T02:00:00Z`

**优点**：
- 所有时间都是UTC，没有时区歧义
- 统一的数据结构，前后端解析简单
- DAILY的"每日重复"语义由执行逻辑实现，存储时只存一个示例时间段

### 目标模式互斥设计

**单一匹配源**，不支持组合：

| targetMode | 使用字段 | 列表内部关系 |
|-----------|---------|-------------|
| **ALL** | 无 | 全量匹配（需前端二次确认） |
| **DEVICE_IDS** | targetDeviceIds | OR（任一ID匹配即可） |
| **DEVICE_BATCHES** | targetDeviceBatchIds | OR（任一批次匹配即可） |
| **DEVICE_TAGS** | targetDeviceTags | AND（必须包含所有键值对） |

---

## 实施方案

### 一、数据库迁移

**文件**: `fota-service/src/main/resources/db/changelog/changes/20260226_04_upgrade_policy_target_mode_time_window.sql`

```sql
-- 新增字段
ALTER TABLE upgrade_policies
    ADD COLUMN IF NOT EXISTS target_mode VARCHAR(32) NOT NULL DEFAULT 'ALL';

ALTER TABLE upgrade_policies
    ADD COLUMN IF NOT EXISTS target_device_batch_ids JSONB;

COMMENT ON COLUMN upgrade_policies.target_mode IS '目标模式：ALL/DEVICE_IDS/DEVICE_BATCHES/DEVICE_TAGS';
COMMENT ON COLUMN upgrade_policies.target_device_batch_ids IS '目标设备批次ID列表（JSONB数组）';

-- 存量数据回填target_mode
UPDATE upgrade_policies
SET target_mode = CASE
    WHEN target_device_ids IS NOT NULL
        AND jsonb_typeof(target_device_ids) = 'array'
        AND jsonb_array_length(target_device_ids) > 0 THEN 'DEVICE_IDS'
    WHEN target_device_tags IS NOT NULL
        AND target_device_tags <> '{}'::jsonb THEN 'DEVICE_TAGS'
    ELSE 'ALL'
END
WHERE target_mode = 'ALL';  -- 只处理默认值，避免覆盖已设置的数据

-- 移除plan_time字段
ALTER TABLE upgrade_policies DROP COLUMN IF EXISTS plan_time;

-- 新增索引
CREATE INDEX IF NOT EXISTS idx_up_target_mode ON upgrade_policies(target_mode);
CREATE INDEX IF NOT EXISTS idx_up_target_device_batch_ids_gin ON upgrade_policies USING GIN (target_device_batch_ids);
CREATE INDEX IF NOT EXISTS idx_up_time_window_gin ON upgrade_policies USING GIN (time_window);
```

**更新**: `fota-service/src/main/resources/db/changelog/db.changelog-master.yaml` 添加此changeSet引用

---

### 二、后端实现

#### 2.1 新增 TimeWindowDTO

**新建**: `fota-service/src/main/java/com/wewins/fota/application/policy/dto/TimeWindowDTO.java`

```java
package com.wewins.fota.application.policy.dto;

import lombok.Data;

/**
 * 时间窗口 DTO
 * <p>
 * 统一使用 ISO8601 UTC 时间戳，避免时区问题
 * </p>
 */
@Data
public class TimeWindowDTO {
    /**
     * 类型：RANGE（固定范围） / DAILY（每日周期）
     */
    private String type;

    /**
     * 开始时间（ISO8601 UTC）
     */
    private String startAt;

    /**
     * 结束时间（ISO8601 UTC）
     */
    private String endAt;
}
```

#### 2.2 修改 UpgradePolicyReqDTO

**文件**: `fota-service/src/main/java/com/wewins/fota/application/policy/dto/UpgradePolicyReqDTO.java`

**删除**: `LocalDateTime planTime`

**新增**:
```java
/**
 * 触发模式：AUTO / MANUAL
 */
private String triggerMode;

/**
 * 时间窗口配置
 */
private TimeWindowDTO timeWindow;

/**
 * 允许升级的源版本列表
 */
private List<String> sourceVersions;

/**
 * 目标设备模式：ALL / DEVICE_IDS / DEVICE_BATCHES / DEVICE_TAGS
 */
private String targetMode;

/**
 * 目标设备ID列表
 */
private List<String> targetDeviceIds;

/**
 * 目标设备批次ID列表
 */
private List<String> targetDeviceBatchIds;

/**
 * 目标设备标签条件（与Device.tags结构一致：JSON对象KV匹配）
 */
private Map<String, Object> targetDeviceTags;
```

#### 2.3 同步修改 UpgradePolicyRespDTO

**文件**: `fota-service/src/main/java/com/wewins/fota/application/policy/dto/UpgradePolicyRespDTO.java`

与ReqDTO保持一致的字段变更。

#### 2.4 修改 UpgradePolicyAssembler

**文件**: `fota-service/src/main/java/com/wewins/fota/adapter/assembler/UpgradePolicyAssembler.java`

**关键变更**:
1. 注入 `ObjectMapper`
2. 新增辅助方法处理JSON转换：
   - `toJsonNode(List<String>)` - 列表转JsonNode
   - `toJsonNode(Map<String, Object>)` - Map转JsonNode
   - `toList(JsonNode, Class)` - JsonNode转列表
   - `toMap(JsonNode)` - JsonNode转Map
3. 更新转换方法，处理所有新增字段

#### 2.5 修改 UpgradePolicyAppServiceImpl

**文件**: `fota-service/src/main/java/com/wewins/fota/application/policy/impl/UpgradePolicyAppServiceImpl.java`

**新增常量**:
```java
private static final List<String> ALLOWED_TRIGGER_MODE = List.of("AUTO", "MANUAL");
private static final List<String> ALLOWED_TARGET_MODE = List.of("ALL", "DEVICE_IDS", "DEVICE_BATCHES", "DEVICE_TAGS");
private static final List<String> ALLOWED_TIME_WINDOW_TYPE = List.of("RANGE", "DAILY");
```

**新增校验方法**:
```java
private void normalizeAndValidateTriggerMode(UpgradePolicy policy)
private void normalizeAndValidateTargetScope(UpgradePolicy policy)
private void normalizeAndValidateTimeWindow(UpgradePolicy policy)
private void validatePositiveStringArray(List<String> list, int maxSize, String fieldName)
private OffsetDateTime parseUtcOffsetDateTime(String dateTimeStr)
```

**校验规则**:
1. `triggerMode`: 枚举校验 (AUTO | MANUAL)
2. `targetMode`: 枚举校验 + 互斥校验
   - 只能有一个目标列表非空
   - DEVICE_IDS: targetDeviceIds非空，其他为空
   - DEVICE_BATCHES: targetDeviceBatchIds非空，其他为空
   - DEVICE_TAGS: targetDeviceTags非空，其他为空
   - ALL: 所有目标列表为空
3. `timeWindow`:
   - type枚举校验 (RANGE | DAILY)
   - 必填startAt和endAt
   - 规则startAt < endAt
   - DAILY约定：时间差不超过24小时
4. 列表字段: 去重、非空、数量上限
   - sourceVersions上限: 50
   - targetDeviceIds上限: 1000
   - targetDeviceBatchIds上限: 100
5. targetDeviceTags: JSON对象格式，与Device.tags结构一致

---

### 三、新增批次查询API

#### 3.1 新增 DeviceImportBatchController

**新建**: `fota-service/src/main/java/com/wewins/fota/adapter/api/admin/DeviceImportBatchController.java`

```java
@RestController
@RequestMapping("/api/admin/device-import-batches")
@RequiredArgsConstructor
public class DeviceImportBatchController {

    private final DeviceImportBatchService deviceImportBatchService;

    /**
     * 分页查询批次列表
     */
    @GetMapping
    @PreAuthorize("@rbac.has('fota:device:read')")
    public ApiResponse<PageResponse<DeviceImportBatchRespDTO>> pageBatches(
        @ModelAttribute DeviceImportBatchPageReqDTO reqDTO) {
        // 实现
    }

    /**
     * 获取批次详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@rbac.has('fota:device:read')")
    public ApiResponse<DeviceImportBatchRespDTO> getBatch(@PathVariable Long id) {
        // 实现
    }
}
```

#### 3.2 新增相关DTO和Service

- `DeviceImportBatchReqDTO.java`
- `DeviceImportBatchRespDTO.java`
- `DeviceImportBatchPageReqDTO.java`
- `DeviceImportBatchAppService.java`
- `DeviceImportBatchAssembler.java`

---

### 四、前端实现

#### 4.1 修改 API 类型定义

**文件**: `fota-ui/src/api/policy.ts`

```typescript
// 新增类型
export type TriggerMode = 'AUTO' | 'MANUAL'
export type TargetMode = 'ALL' | 'DEVICE_IDS' | 'DEVICE_BATCHES' | 'DEVICE_TAGS'
export type TimeWindowType = 'RANGE' | 'DAILY'

export interface TimeWindowDTO {
  type: TimeWindowType
  startAt: string  // ISO8601 UTC
  endAt: string    // ISO8601 UTC
}

// 修改 UpgradePolicyItem 和 UpgradePolicyPayload
// 删除 planTime
// 新增: triggerMode, timeWindow, sourceVersions, targetMode, targetDeviceIds, targetDeviceBatchIds, targetDeviceTags
```

#### 4.2 新增批次API

**文件**: `fota-ui/src/api/deviceImportBatch.ts`

```typescript
export interface DeviceImportBatchItem {
  id: number
  batchName: string
  status: string
  totalCount: number
  successCount: number
  failedCount: number
  createdAt: string
}

export const pageBatches = (params: Record<string, unknown>) => {
  return get<PageResult<DeviceImportBatchItem>>('/admin/device-import-batches', { params })
}
```

#### 4.3 修改 PolicyListView.vue

**文件**: `fota-ui/src/views/policy/PolicyListView.vue`

**表单结构调整**:

```
基础信息区:
- 产品选择
- 目标固件版本
- 策略名称

触发配置区:
- 触发模式: [AUTO 自动] [MANUAL 手动]
- 时间窗口:
  - 类型切换: [RANGE 固定范围] [DAILY 每日周期]
  - 统一使用日期时间选择器，自动转为UTC ISO8601格式

生效范围区:
- 源版本指定: 多行文本输入（逗号或换行分隔）

- 目标模式（单选卡片2x2布局）:
  [ ALL     全量设备    ] [ DEVICE_IDS  设备ID列表 ]
  [ BATCHES 批次筛选    ] [ DEVICE_TAGS  标签筛选  ]

- 根据选择显示对应输入区:
  - ALL: 警告提示 + 预计影响设备数
  - DEVICE_IDS: 多行文本输入（设备ID，逗号或换行分隔）
  - DEVICE_BATCHES: 多选下拉框
  - DEVICE_TAGS: 键值对编辑器（JSON对象）

高级选项区:
- 灰度比例
- 备注
```

**列表展示增强**:
- 新增列：触发方式、源版本、目标范围、时间窗口
- 删除列：计划时间

**关键函数**:
```typescript
// 时间处理
const toUtcIso = (date: Date) => date.toISOString()

// DAILY模式辅助
const createDailyTimeWindow = (startTime: string, endTime: string, baseDate: Date) => {
  // 创建当日的时间窗口，返回ISO8601格式
  const start = new Date(baseDate)
  const [sh, sm, ss] = startTime.split(':').map(Number)
  start.setHours(sh, sm, ss || 0, 0)

  const end = new Date(baseDate)
  const [eh, em, es] = endTime.split(':').map(Number)
  end.setHours(eh, em, es || 0, 0)

  // 处理跨日
  if (end < start) {
    end.setDate(end.getDate() + 1)
  }

  return {
    startAt: start.toISOString(),
    endAt: end.toISOString()
  }
}

// 摘要展示
const getTriggerModeLabel = (mode: TriggerMode) => ...
const getSourceVersionsSummary = (versions: string[]) => ...
const getTargetSummary = (policy: UpgradePolicyItem) => ...
const getTimeWindowSummary = (window: TimeWindowDTO) => ...

// 表单校验
const validateTargetMode = () => {
  if (form.targetMode === 'ALL') return true
  const field = form.targetMode === 'DEVICE_IDS' ? 'targetDeviceIds'
             : form.targetMode === 'DEVICE_BATCHES' ? 'targetDeviceBatchIds'
             : 'targetDeviceTags'
  return form[field] && form[field].length > 0
}

const validateTimeWindow = () => {
  if (!form.timeWindow) return true
  if (!form.timeWindow.startAt || !form.timeWindow.endAt) return false
  const start = new Date(form.timeWindow.startAt)
  const end = new Date(form.timeWindow.endAt)

  if (start >= end) return false

  // DAILY模式：检查不超过24小时
  if (form.timeWindow.type === 'DAILY') {
    const hours = (end.getTime() - start.getTime()) / (1000 * 60 * 60)
    if (hours > 24) return false
  }

  return true
}

// ALL模式二次确认
const submitForm = async () => {
  if (form.targetMode === 'ALL') {
    await ElMessageBox.confirm('此策略将影响所有设备，是否确认？', '风险提示', { type: 'warning' })
  }
  // ... 提交逻辑
}
```

#### 4.4 修改国际化文件

**文件**: `fota-ui/src/locales/zh-CN.ts` 和 `en-US.ts`

新增翻译键覆盖所有新字段和提示信息。

---

### 五、关键文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `fota-service/src/main/resources/db/changelog/changes/20260226_04_upgrade_policy_target_mode_time_window.sql` | 新建 | 数据库迁移 |
| `fota-service/src/main/java/com/wewins/fota/application/policy/dto/TimeWindowDTO.java` | 新建 | 时间窗口DTO（统一使用时间戳） |
| `fota-service/src/main/java/com/wewins/fota/application/policy/dto/UpgradePolicyReqDTO.java` | 修改 | 新增字段 |
| `fota-service/src/main/java/com/wewins/fota/application/policy/dto/UpgradePolicyRespDTO.java` | 修改 | 新增字段 |
| `fota-service/src/main/java/com/wewins/fota/adapter/assembler/UpgradePolicyAssembler.java` | 修改 | JSON转换逻辑 |
| `fota-service/src/main/java/com/wewins/fota/application/policy/impl/UpgradePolicyAppServiceImpl.java` | 修改 | 校验逻辑 |
| `fota-service/src/main/java/com/wewins/fota/adapter/api/admin/DeviceImportBatchController.java` | 新建 | 批次查询API |
| `fota-ui/src/api/policy.ts` | 修改 | 类型定义 |
| `fota-ui/src/api/deviceImportBatch.ts` | 新建 | 批次API |
| `fota-ui/src/views/policy/PolicyListView.vue` | 修改 | 表单和列表 |
| `fota-ui/src/locales/zh-CN.ts` | 修改 | 中文翻译 |
| `fota-ui/src/locales/en-US.ts` | 修改 | 英文翻译 |

---

### 六、验证测试

#### 6.1 后端单元测试
- DTO转换测试
- 校验规则测试（枚举、互斥、时间格式）
- JSON序列化测试

#### 6.2 前端组件测试
- 表单状态切换测试
- 校验规则测试
- 摘要展示测试

#### 6.3 集成测试场景
1. **RANGE时间窗口** - 创建跨天策略，验证UTC时间戳正确存储
2. **DAILY时间窗口** - 创建每日策略，验证不超过24小时限制
3. **DAILY跨日** - 创建23:00-02:00跨日策略
4. **目标模式互斥** - 切换模式验证其他字段清空
5. **ALL模式确认** - 验证二次确认弹窗
6. **前后端一致性** - 创建策略后查询，验证字段正确回显

---

### 七、实施顺序

1. **数据库迁移** - 优先执行，其他依赖
2. **后端DTO和Service** - 核心逻辑
3. **批次查询API** - 前端需要
4. **前端API和类型** - 接口定义
5. **前端表单和列表** - UI实现
6. **测试验证** - 确保质量
