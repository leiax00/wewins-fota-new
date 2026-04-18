# Sprint 5: 统计数据展示

> **时间**: 2026-04 (Week 11-13)
> **目标**: 在管理后台新增统计分析能力，覆盖产品、设备、策略、固件四个维度
> **范围**: 纯展示型功能，不涉及写操作

**Sprint Owner**: FOTA 后端组
**文档版本**: v1.1
**创建日期**: 2026-04-14
**修订日期**: 2026-04-15（根据技术评审意见修订）

---

## 📋 Sprint 概览

### 背景

当前管理后台缺乏统计分析能力，运营人员无法直观了解设备版本分布、设备升级轨迹、策略执行效果等关键信息。

### 目标

| 维度 | 核心诉求 |
|------|---------|
| **产品** | 各固件版本的设备数量分布；识别从未连接的设备 |
| **设备** | 追踪单台设备近 30 天的检查与升级轨迹（Timeline） |
| **策略** | 评估策略覆盖效果：影响/已升级/待升级/已检测/未检测；趋势图 |
| **固件** | 了解某个版本当前有多少设备在用，并可下钻查看设备列表 |

### 验收标准

- [ ] 产品页面可查看各版本设备分布 + 从未访问设备数
- [ ] 设备详情可查看 30 天内升级轨迹 Timeline
- [ ] 策略详情可查看六项统计数字 + 每日趋势图
- [ ] 固件列表新增设备数列，点击可下钻到设备列表（游标分页）
- [ ] 所有统计接口响应时间 < 3s
- [ ] 统计数据展示上次更新时间（由后端 `data_calculated_at` 字段返回），支持手动触发刷新

### 范围边界说明

> **跨区域统计暂不纳入 Sprint 5 范围**。本 Sprint 统计数据仅覆盖单区域（主区域）数据。多区域汇总方案待跨区域数据汇聚链路稳定后另起 Sprint 设计。

---

## 🗂️ 数据来源说明

统计数据来自两个数据源：

- **MySQL**：设备当前版本、策略影响范围、固件设备列表、统计快照表
- **ClickHouse**（近 30 天）：设备检查记录、升级事件、策略检测趋势

> ClickHouse 数据有 30 天 TTL，超出范围的历史数据不可查。

> **注意**：原方案中使用 Prometheus Gauge 存储业务统计趋势的方案已废弃，原因见下文数据架构说明。

---

## 🏗️ 数据架构：统计快照表方案

### 为什么废弃 Prometheus Gauge

原方案用 Micrometer Gauge（内存）存储业务统计数据，存在以下不可接受的问题：

| 问题 | 表现 |
|------|------|
| Pod 重启归零 | Gauge 存内存，重启后为 0，趋势图出现"跌零"毛刺 |
| 多实例数据冲突 | 多个实例各自维护内存 Gauge，Prometheus 聚合后数值异常 |
| 计算/抓取频率不匹配 | 每 5 分钟计算一次，每 15 秒抓取，两次计算之间均为过期数据 |
| 冷启动空窗 | 服务启动到定时任务首次运行最多 5 分钟内，所有 Gauge 为 0 |
| label 爆炸 | policy_id 作为 label，策略数万级后 Prometheus TSDB 性能急剧下降 |

**Prometheus 保留用于 RED 指标**（QPS、延迟、错误率），不承载业务统计趋势。

### 统计快照表设计

**MySQL：**

```sql
CREATE TABLE statistics_snapshot (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    scope         VARCHAR(20)  NOT NULL COMMENT 'product / policy / firmware',
    scope_id      BIGINT       NOT NULL COMMENT '对应的产品/策略/固件 ID',
    metric_key    VARCHAR(50)  NOT NULL COMMENT '指标名称，见下表',
    metric_value  BIGINT       NOT NULL DEFAULT 0,
    snapshot_hour DATETIME     NOT NULL COMMENT '数据时间（精确到整点，调度器写入时截断分秒）',
    calculated_at DATETIME     NOT NULL COMMENT '本行最后计算时间（带分钟，用于展示数据新鲜度）',
    deleted       SMALLINT     NOT NULL DEFAULT '0',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_metric_hour (scope, scope_id, metric_key, snapshot_hour),
    KEY idx_scope_id_hour (scope, scope_id, snapshot_hour),
    KEY idx_calculated_at (calculated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统计快照表，小时粒度，由定时任务写入，接口直接查询';
```

**PostgreSQL：**

```sql
CREATE TABLE statistics_snapshot (
    id            bigint                      NOT NULL GENERATED ALWAYS AS IDENTITY,
    scope         character varying(20)       NOT NULL,
    scope_id      bigint                      NOT NULL,
    metric_key    character varying(50)       NOT NULL,
    metric_value  bigint                      NOT NULL DEFAULT 0,
    snapshot_hour timestamp without time zone NOT NULL,
    calculated_at timestamp without time zone NOT NULL,
    deleted       smallint                    NOT NULL DEFAULT 0,
    created_at    timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_scope_metric_hour UNIQUE (scope, scope_id, metric_key, snapshot_hour)
);
CREATE INDEX idx_scope_id_hour ON statistics_snapshot (scope, scope_id, snapshot_hour);
CREATE INDEX idx_calculated_at ON statistics_snapshot (calculated_at);
COMMENT ON TABLE  statistics_snapshot IS '统计快照表，小时粒度，由定时任务写入，接口直接查询';
```

> **写入规则**：调度器每 5 分钟执行，`snapshot_hour = DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00')`（截断到整点），同一小时内多次执行 UPSERT 同一行（仅更新 `metric_value` 和 `calculated_at`），跨整点则插入新行。

**metric_key 枚举**：

| metric_key | scope | 说明 |
|-----------|-------|------|
| `affected_total` | policy | 策略影响设备总数 |
| `upgraded_total` | policy | 策略已升级设备数 |
| `checked_30d` | policy | 近30天有检查行为的设备数 |
| `upgrade_instructed_30d` | policy | 近30天收到升级指令（UPDATE）的设备数 |
| `device_count` | firmware | 该固件版本当前设备数 |
| `never_visited` | product | 从未访问的设备数 |

### 工作机制

```
@Scheduled(每 5 分钟) + RegionLeaderService.isLeader() 保证单实例执行
  → 增量计算统计值（不做全量设备扫描，见性能优化）
  → UPSERT statistics_snapshot（snapshot_hour 截断到整点）
         ↑ 当前值接口：取 snapshot_hour 最新一行
         ↓ 趋势图接口：按粒度 GROUP BY 聚合历史行
```

**分布式调度**：复用现有 `RegionLeaderService`，去掉其 `app.mode=region` 的条件限制，使其在所有模式下均可激活。

主区域 nodeCode 形如 `main-01`，`RegionCodeResolver` 解析出 `regionCode=main`，Redis key 为 `fota:region:leader:main`，选举逻辑与区域节点完全一致，无需新增任何基础设施。

调度器中判断 leader 后执行：

```java
@Scheduled(cron = "0 */5 * * * ?")
public void refreshMetrics() {
    if (!regionLeaderService.isLeader()) return;
    // 执行统计计算
}
```

> **前置改造**（Sprint 5 开发前）：移除 `RegionLeaderService` 上的 `@ConditionalOnProperty(name = "app.mode", havingValue = "region")` 注解。

### 动态粒度查询

趋势图接口支持 `granularity` 参数，后端按需 GROUP BY：

```
GET /api/admin/statistics/policies/{policyId}/trend
    ?days=7&granularity=hour   # 明确指定小时粒度（返回 168 个点）
    ?days=30&granularity=day   # 明确指定天粒度（返回 30 个点）
    ?days=7                    # 不传则自动推断（见下表）
```

**自动粒度推断规则**（不传 `granularity` 时）：

| days 范围 | 自动粒度 | 最大数据点数 |
|-----------|---------|------------|
| ≤ 3 天   | hour    | 72 点       |
| 4 ~ 30 天 | day     | 30 点       |
| > 30 天  | week    | ~13 点      |

**后端 SQL 示例**：

```java
// granularity → SQL 分组表达式映射
String groupExpr = switch (granularity) {
    case HOUR -> "DATE_FORMAT(snapshot_hour, '%Y-%m-%d %H:00:00')";
    case DAY  -> "DATE(snapshot_hour)";
    case WEEK -> "DATE_FORMAT(snapshot_hour, '%Y-%u')";
};
// SELECT {groupExpr} AS ts, MAX(metric_value) AS val
// FROM statistics_snapshot
// WHERE scope=? AND scope_id=? AND metric_key=?
//   AND snapshot_hour >= DATE_SUB(NOW(), INTERVAL ? DAY)
// GROUP BY ts ORDER BY ts
```

> `MAX(metric_value)` 取每个时间桶内最后一次计算值（同一小时/天最后写入的那次），也可改为 `AVG` 视业务语义决定。

### 数据保留策略

小时粒度数据量估算：1000 条策略 × 5 个指标 × 24h × 90 天 ≈ **1080 万行**，需要定期清理：

```sql
-- 保留最近 90 天，超出部分按月分区或定期 DELETE
DELETE FROM statistics_snapshot
WHERE snapshot_hour < DATE_SUB(NOW(), INTERVAL 90 DAY);
```

建议按月分区（`PARTITION BY RANGE(YEAR(snapshot_hour) * 100 + MONTH(snapshot_hour))`），清理时直接 `DROP PARTITION`，避免大事务。

**不另建周/月快照表**：周、月粒度的趋势数据直接在查询层对小时表做 `GROUP BY WEEK` / `GROUP BY MONTH` 聚合即可。单策略单指标 90 天的小时行数约 1 万行，索引命中后聚合耗时极低，无需冗余存储。若未来出现"半年/年度趋势"需求，再另起设计专项汇总表。

---

## 🚀 性能优化方案

### 策略影响设备数计算优化

**问题**：每 5 分钟对所有策略重新计算影响范围，ALL 模式全表 COUNT，DEVICE_TAGS 模式需 JOIN，O(策略数 × 设备数) 复杂度不可接受。

**方案**：
- 策略创建/修改时，同步写入 `policy_stats.affected_total` 快照字段（事件驱动）
- 定时任务只做**增量事件聚合**（ClickHouse 查新增升级/检查记录），不重算影响范围
- `affected_total` 仅在策略目标条件变更时重算

**DEVICE_TAGS 场景索引要求**：

`device_tags` 表已有 `idx_device_tags_kv_device (tag_key, tag_value, device_id)` 复合索引，可直接覆盖标签匹配查询，无需额外添加。

### ClickHouse 趋势图优化

策略趋势图从 ClickHouse 物化视图读取，按**小时**预聚合，与 MySQL 快照表粒度保持一致：

```sql
-- 小时级物化视图（与 MySQL snapshot 粒度对齐）
-- 基于 device_check_logs 表（check_rst 字段为检查结果）
CREATE MATERIALIZED VIEW policy_hourly_stats_mv
ENGINE = AggregatingMergeTree()
ORDER BY (policy_id, event_hour)
AS SELECT
    policy_id,
    toStartOfHour(event_time)                        AS event_hour,
    uniqState(device_id)                             AS checked_devices,
    uniqStateIf(device_id, check_rst = 'UPDATE')     AS instructed_devices
FROM device_check_logs
WHERE policy_id IS NOT NULL
GROUP BY policy_id, event_hour;
```

查询时同样支持动态粒度，用 `toStartOfDay` / `toStartOfWeek` 二次聚合：

```sql
-- granularity=day 时，在应用层或 SQL 层聚合到天
SELECT toStartOfDay(event_hour) AS ts,
       uniqMerge(checked_devices)    AS checked,
       uniqMerge(instructed_devices) AS instructed
FROM policy_hourly_stats_mv
WHERE policy_id = ? AND event_hour >= now() - INTERVAL 30 DAY
GROUP BY ts ORDER BY ts;
```

### 固件设备列表游标分页

深分页（`LIMIT 20 OFFSET 500000`）导致全量扫描，改用游标分页：

```
GET /api/admin/statistics/firmware/{versionId}/devices
    ?cursor=<last_device_id>&size=20
```

响应体包含 `next_cursor`，无 `cursor` 参数时从头分页。

---

## 📊 统计口径说明（修订）

### 策略维度统计口径

| 指标 | 口径 | 数据源 |
|------|------|--------|
| 影响设备数 | 按策略 `target_mode` 匹配的设备总数（策略变更时同步写入快照） | MySQL |
| 已升级 | 当前版本 == 策略目标版本的设备数 | MySQL |
| 待升级 | 影响总数 - 已升级（查询层计算，不写快照表） | 计算值 |
| 近30天已检测 | `device_check_logs` 中 `policy_id` = 本策略，有任意检查行为（无论 `check_rst`）的去重设备数 | ClickHouse |
| 近30天收到升级指令 | `device_check_logs` 中 `policy_id` = 本策略且 `check_rst = 'UPDATE'` 的去重设备数 | ClickHouse |
| 近30天未检测 | 影响总数 - 近30天已检测 | 计算值 |

> **修订说明**：原"近30天已检测"口径仅计 `result = UPDATE` 的设备，漏计 `NO_UPDATE` 设备（设备在线但无需升级），属于逻辑错误。修订后以"有任意检查行为"为口径，新增"收到升级指令"指标单独展示。

### 产品维度"从未访问"口径

```sql
-- 排除新注册设备（24小时内），避免将正常新设备计入"从未访问"
SELECT COUNT(*) FROM devices
WHERE product_id = ? 
  AND last_seen_at IS NULL
  AND created_at < NOW() - INTERVAL 24 HOUR
```

---

## 📐 功能设计

### 1. 产品维度

**展示内容**：
- 各固件版本的设备数量及占比（版本数 ≤ 8 时饼图，> 8 时强制柱状图，避免标签重叠）
- 从未访问设备数（`last_seen_at IS NULL AND created_at < now() - 24h`）

**交互**：在产品详情页新增"统计"Tab，含刷新按钮 + 更新时间

---

### 2. 设备维度

**展示内容**：
- 近 N 天（7/15/30 天可切换）的检查记录和升级事件，合并为时间线
- 每条事件显示：时间、类型（CHECK / DL_START / DL_OK / UP_OK 等）、结果、关联策略
- 单设备 Timeline 返回上限 500 条，超出时展示"加载更多"分页

**示意**：
```
◉ 2026-04-14 10:23  CHECK → UPDATE  策略#42  v2.0.5 → v2.1.0
◎ 2026-04-14 10:25  DL_START
● 2026-04-14 10:31  UP_OK  ✓ 升级成功
◉ 2026-04-10 09:11  CHECK → NO_UPDATE
```

**交互**：在设备详情页新增"升级轨迹"Tab，使用 `el-timeline` 组件

---

### 3. 策略维度

**展示内容**：
- 六项统计卡片：影响设备数、已升级、待升级、近30天已检测、近30天收到升级指令、近30天未检测
- 每日趋势折线图：升级指令设备数 + 检测设备数（可切换 7/15/30 天）

**交互**：在策略详情页新增"统计"Tab，含刷新按钮 + `data_calculated_at` 展示时间

---

### 4. 固件维度

**展示内容**：
- 固件版本列表新增"设备数"列
- 点击设备数，弹出设备列表 Drawer（支持搜索、游标分页）

**交互**：复用固件版本列表页面，新增列 + Drawer 组件

---

## 🔌 接口清单

| 接口 | 说明 |
|------|------|
| `GET /api/admin/statistics/products/{productId}/version-distribution` | 产品版本分布（含从未访问数） |
| `GET /api/admin/statistics/devices/{imei}/timeline?days=30&cursor=&size=50` | 设备升级轨迹（游标分页） |
| `GET /api/admin/statistics/policies/{policyId}/summary` | 策略统计汇总（六项指标） |
| `GET /api/admin/statistics/policies/{policyId}/trend?days=30&granularity=hour\|day\|week` | 策略趋势（granularity 不传时自动推断） |
| `GET /api/admin/statistics/firmware/{versionId}/device-count` | 固件当前设备数 |
| `GET /api/admin/statistics/firmware/{versionId}/devices?cursor=&size=20&keyword=` | 固件设备列表（游标分页） |
| `POST /api/admin/statistics/refresh?scope=&id=` | 手动触发指定 scope 的统计刷新 |

### 统一响应体规范

所有统计接口响应体必须包含 `data_calculated_at` 字段，前端展示"更新于 xx 秒前"：

```json
{
  "data": { ... },
  "data_calculated_at": "2026-04-15T10:23:00Z",
  "scope": "policy",
  "scope_id": 42
}
```

趋势图数据格式统一为（`ts` 格式随粒度变化：hour → `"2026-04-14 10:00:00"`，day → `"2026-04-14"`，week → `"2026-15"`）：

```json
{
  "trend": [
    { "ts": "2026-04-14", "checked": 1200, "instructed": 340 },
    { "ts": "2026-04-13", "checked": 980,  "instructed": 210 }
  ]
}
```

---

## 🔒 手动刷新设计

```
POST /api/admin/statistics/refresh
     ?scope=policy&id={policyId}    # 刷新指定策略
     ?scope=product&id={productId}  # 刷新指定产品
     ?scope=all                     # 全量刷新（需 ADMIN 权限码）
```

**锁机制**：

| 锁 | Redis Key 示例 | TTL | 作用 |
|----|---------------|-----|------|
| 防抖锁 | `stats:debounce:policy:42` | 30s | 限制同一 scope 30 秒内只能触发一次（用户行为限频） |
| 执行锁 | `stats:running:policy:42` | 120s | 防止手动刷新与定时任务并发执行，tryLock 失败直接返回"计算中" |

- 前端触发后禁用刷新按钮，展示 loading 状态
- 使用 `AbortController` 取消上一个未完成的请求
- 刷新完成后展示最新的 `data_calculated_at`

**权限码**：`scope=all` 须持有 `statistics:refresh:all` 权限码，普通运营只能刷新 policy/product/firmware 范围。

---

## ⚠️ 运维与可观测性

### 定时任务失败监控

`BusinessMetricsScheduler` 执行失败时：
- 写入 `statistics_task_error` 日志表（记录失败时间、scope、错误信息）
- 通过现有 Prometheus `fota_scheduler_error_total` 计数器上报，触发告警

### ClickHouse 不可用降级

| 情况 | 降级策略 |
|------|---------|
| ClickHouse 查询超时（>5s） | 返回 MySQL 快照数据，响应体加 `"degraded": true` |
| ClickHouse 完全不可用 | 返回最近一次快照数据（最多 30 分钟前），前端展示"数据可能不是最新" |

### 策略/产品删除后的快照清理

策略禁用/删除时，触发 `statistics_snapshot` 对应行的软删除（`deleted` 标记），不做物理删除以保留历史趋势。

---

## ✅ 任务拆解

### 后端

- [ ] **数据库**：创建 `statistics_snapshot` 表（MySQL + PostgreSQL 各一份 migration）+ `upgrade_policies` 表新增 `affected_total` 快照字段（`BIGINT NOT NULL DEFAULT 0`）；`device_tags` 已有覆盖索引，无需改动
- [ ] `StatisticsMapper.xml`（MySQL）：版本分布、策略影响/升级数、固件设备列表（游标分页）、快照表 UPSERT
- [ ] `TimelineMapper.xml`（ClickHouse）：设备 Timeline 合并查询 `device_check_logs` + `device_upgrade_events`（通过 `request_id` 关联，游标分页）、策略检测/升级趋势（走 `policy_hourly_stats_mv`）
- [ ] `ProductStatisticsAppService` + DTO
- [ ] `DeviceTimelineAppService` + DTO
- [ ] `PolicyStatisticsAppService` + DTO（六项指标口径已修订）
- [ ] `FirmwareStatisticsAppService` + DTO
- [ ] `StatisticsController`（六个端点 + 手动刷新端点，响应体含 `data_calculated_at`）
- [ ] 移除 `RegionLeaderService` 上的 `app.mode=region` 条件限制，使其在 main 模式下也生效
- [ ] `BusinessMetricsScheduler`：每 5 分钟，判断 `isLeader()` 后执行，增量计算并写快照表
- [ ] `StatisticsRefreshService`：手动触发刷新（双锁：防抖锁 30s + 执行锁 120s，tryLock 失败返回"计算中"）
- [ ] ClickHouse 物化视图：`policy_hourly_stats_mv`（小时级预聚合；天/周粒度在查询层对 MV 做二次 GROUP BY，不另建天级/周级 MV）

### 前端

- [ ] `api/statistics.ts`：封装六个接口，定义 `RefreshScope = 'product' | 'policy' | 'firmware' | 'all'`
- [ ] `StatisticsTabContainer.vue`：公共容器组件（刷新按钮 + `data_calculated_at` 展示 + loading 状态 + AbortController）
- [ ] `ProductVersionChart.vue`：产品版本分布图（版本数 > 8 自动切柱状图）
- [ ] `DeviceTimeline.vue`：设备升级轨迹 Timeline（游标分页 + 空状态 + 错误状态）
- [ ] `PolicyStatisticsPanel.vue`：策略统计卡片（六项）+ 趋势图
- [ ] `FirmwareDeviceDrawer.vue`：固件设备列表抽屉（游标分页 + 关键词搜索）
- [ ] 集成到产品/设备/策略/固件现有页面（复用 `StatisticsTabContainer`）
- [ ] 国际化（`zh-CN.ts` / `en-US.ts`）：含"更新于 N 秒前"、Timeline 事件类型映射等词条

---

## ⚠️ 注意事项

- 策略"影响设备数"需按 `target_mode`（ALL / DEVICE_IDS / DEVICE_BATCHES / DEVICE_TAGS）分支计算，在策略变更时同步写入快照字段，**定时任务不重算此值**
- ClickHouse Timeline 单设备上限 500 条，超出使用游标分页
- 统计接口可按需加短时缓存（建议 30s ~ 2min），避免重复扫描大表
- 设备注销后，相关统计数字在下次定时任务执行后自动反映（注销设备不计入活跃统计）
- 前端"刷新"按钮触发后展示 loading，刷新完成后展示 `data_calculated_at`（"更新于 xx 秒前"）
