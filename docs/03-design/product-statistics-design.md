# 产品维度统计设计方案

> **状态**: 已确认  
> **创建日期**: 2026-04-18  
> **适用范围**: Sprint 5 产品统计模块

---

## 一、核心诉求

产品维度统计的本质是：**每个固件版本当前有多少台设备在用**。

设备表（`devices`）通过 `device_version_parts` 记录当前版本，天然可以 GROUP BY 聚合，无需 ClickHouse 事件流。

---

## 二、数据模型

### 涉及的表

| 表 | 作用 |
|----|------|
| `devices` | 设备主表，含 `product_id` |
| `device_version_parts` | 设备当前各 part 版本（快照统计与实时分布查询均含全部 part） |
| `firmware_versions` | 版本元信息（`version`, `internal_version`, `product_id`） |
| `stat_version_device_count` | **每日快照**，存储每个版本在某天的设备数（含所有 part） |

### `stat_version_device_count` 表结构

```sql
CREATE TABLE `stat_version_device_count` (
  `id`            bigint   NOT NULL AUTO_INCREMENT,
  `product_id`    bigint   NOT NULL,
  `version_id`    bigint   NOT NULL,
  `device_count`  bigint   NOT NULL DEFAULT 0,   -- 含所有 part，不限主分区
  `stat_time`     datetime NOT NULL,              -- 统计时间即计算时间，合并为一个字段
  `created_at`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_version_stat_time` (`version_id`, `stat_time`),
  KEY `idx_product_stat_time` (`product_id`, `stat_time`)
);
```

> `stat_time` 为统计执行时间（精确到小时），`snapshot_hour` 与 `calculated_at` 已合并。  
> 每个设备的所有 part 版本均单独计数，不再限制主分区（`is_primary = 1`）。

---

## 三、统计方式

### 3.1 每日定时快照（趋势数据来源）

**目的**：为版本趋势图提供历史数据点。

**触发方式**：每天凌晨定时任务（`0 0 1 * * ?`，凌晨 1 点）。

**执行逻辑**：

```sql
-- 查询每个版本 part 当前设备数（含全部 part，不限主分区）
SELECT
    fv.product_id                AS productId,
    dvp.version_id               AS versionId,
    COUNT(DISTINCT dvp.device_id) AS deviceCount
FROM device_version_parts dvp
JOIN firmware_versions fv ON fv.id = dvp.version_id AND fv.deleted = 0
WHERE dvp.version_id IS NOT NULL
GROUP BY fv.product_id, dvp.version_id
```

将结果按执行时间（精确到小时，如 `2026-04-18 01:00:00`）写入 `stat_version_device_count`，使用 `ON DUPLICATE KEY UPDATE` 幂等写入。

**规模评估**（以 100 产品 × 10 版本为例）：

| 指标 | 值 |
|------|---|
| 版本数上限 | 1000 |
| 每天写入行数 | 1000 |
| 年数据量 | 365,000 行 |
| 执行时间 | < 1s（单次 GROUP BY） |

**结论**：每天一次完全够用，不需要每小时甚至每 5 分钟执行。

### 3.2 单产品实时统计（版本分布卡片）

**触发方式**：用户打开某产品的统计抽屉时，前端调用 `/admin/statistics/products/{productId}/version-distribution`。

**执行逻辑**：

```sql
SELECT
    dvp.version_id                                          AS versionId,
    COALESCE(dvp.version, fv.version, 'UNKNOWN')           AS version,
    COALESCE(dvp.internal_version, fv.internal_version)    AS internalVersion,
    COUNT(DISTINCT d.id)                                    AS deviceCount
FROM devices d
LEFT JOIN device_version_parts dvp
    ON dvp.device_id = d.id
LEFT JOIN firmware_versions fv
    ON fv.id = dvp.version_id AND fv.deleted = 0
WHERE d.product_id = #{productId}
GROUP BY dvp.version_id,
         COALESCE(dvp.version, fv.version, 'UNKNOWN'),
         COALESCE(dvp.internal_version, fv.internal_version)
ORDER BY deviceCount DESC, (versionId IS NULL) ASC, versionId ASC
```

**特点**：

- 实时性强，不依赖快照
- 数据范围限定在单个 `product_id`，索引命中效率高
- `neverVisitedCount` 暂硬编码为 0（`devices` 表无"从未上报"专属标记）

---

## 四、API 设计

### 4.1 版本分布（实时）

```
GET /admin/statistics/products/{productId}/version-distribution
```

响应字段：

```json
{
  "data": {
    "versionDistributions": [
      { "versionId": 12, "version": "2.0.1", "internalVersion": "r210", "deviceCount": 3200, "percentage": 64.0 }
    ],
    "neverVisitedCount": 0,
    "totalDevices": 5000
  },
  "dataCalculatedAt": "2026-04-18T10:05:23",
  "scope": "product",
  "scopeId": 1
}
```

### 4.2 版本趋势（读快照）

```
GET /admin/statistics/products/{productId}/trend?days=7&granularity=day
```

读取 `stat_version_device_count` 近 N 天数据，按粒度（hour/day）聚合后返回时间序列。

### 4.3 设备列表（实时，游标分页）

```
GET /admin/statistics/products/{productId}/devices?cursor=&size=20&keyword=
```

---

## 五、调度策略变更

| 任务 | 旧频率 | 新频率 | 原因 |
|------|--------|--------|------|
| `refreshFirmwareDeviceCounts` | `0 */5 * * * ?`（每 5 分钟） | `0 0 1 * * ?`（每天凌晨 1 点） | 快照按天精度，高频写入无意义且浪费资源 |

> **注意**：如果未来产品数 × 版本数超过 10,000，可以考虑按产品拆分批次，每批 500 个版本，分批写入，避免单次大事务。

---

## 六、前端显示逻辑

### 版本标签去重

同一产品下存在 `version` + `internalVersion` 相同但 `versionId` 不同的版本时，前端通过 `versionLabelMap` 自动追加 `#versionId` 后缀区分：

```
2.0.1 (r210)           ← 唯一时直接展示
2.0.1 (r210) #12       ← 重复时追加版本 ID
2.0.1 (r210) #15
```

此逻辑在 `ProductStatisticsPanel.vue` 中的 `versionLabelMap` computed 实现，后端不感知。

---

## 七、待办事项

- [x] 将 `refreshFirmwareDeviceCounts` cron 表达式改为 `0 0 1 * * ?`
- [ ] `neverVisitedCount` 字段：待 `devices` 表增加"首次上报时间"或专属标记后补全
- [ ] 数据回填：部署后手动触发一次全量快照，确保趋势图有历史数据
