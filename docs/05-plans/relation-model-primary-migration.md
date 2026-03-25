# 关联表为主模型的双数据库迁移方案

## 背景

当前仓库在 PostgreSQL 与 MySQL 之间存在两套不同的数据表达方式：

- PostgreSQL 仍以 `JSONB` 字段作为主存储模型
- MySQL 已开始引入部分拆行/关联表，但应用层尚未真正以这些表为主读写

这会导致几个问题：

- 两个数据库的“事实来源”不同
- 应用层需要维护两套查询与写入语义
- 迁移脚本、索引策略、测试覆盖难以收敛
- 很难保证管理后台、策略匹配、设备版本统计在两种数据库上语义一致

本方案明确采用：

- **关联表作为唯一主模型**
- `JSON/JSONB` 仅保留为过渡期兼容字段或展示快照
- PostgreSQL 与 MySQL 采用**尽量一致**的表结构和索引语义


## 设计原则

1. 主模型必须跨数据库一致
2. 应用读写路径只能有一个主事实来源
3. 复杂筛选条件应落在结构化列和关联表上，不依赖 JSON 方言能力
4. JSON 字段只保留给非结构化元数据，且不能承载核心业务约束
5. 迁移必须支持灰度切换，不能一次性硬切


## 建议的主模型边界

### 保留主表直接字段

以下字段继续保留在主表中：

- `products`: 基础属性
- `devices`: `imei`、`product_id`、`status`、`first_seen_at`、`last_seen_at`、`import_batch_id`
- `firmware_versions`: `product_id`、`version`、`internal_version`、文件元数据、`package_status`
- `upgrade_policies`: `product_id`、`name`、`status`、`priority`、`gray_rate`、`trigger_mode`、`target_mode`、`target_version_id`
- `device_import_batches`: 批次基础属性

### 改为关联表主存储的字段

以下字段不再以 JSON 作为主模型：

- `devices.tags`
- `devices.version_parts`
- `devices.initial_version_parts`
- `firmware_versions.tags`
- `upgrade_policies.source_versions`
- `upgrade_policies.target_imeis`
- `upgrade_policies.target_device_batch_ids`
- `upgrade_policies.target_device_tags`

### 继续保留 JSON 的字段

以下字段可以继续保留 JSON：

- `firmware_versions.meta`
- `upgrade_policies.time_window`
- `sys_permissions.menu_config`
- `sys_dict_item.extra`

原因：

- 这些字段偏展示或扩展配置
- 目前没有明显的高频结构化 join/filter 需求
- 强行拆表只会增加写入复杂度


## 统一后的目标表设计

### 1. 设备标签表

表名：`device_tags`

建议字段：

- `id`
- `device_id`
- `tag_key`
- `tag_value`
- `created_at`
- `updated_at`

约束与索引：

- 唯一约束：`(device_id, tag_key)`
- 查询索引：`(tag_key, tag_value, device_id)`
- 反查索引：`(device_id)`

用途：

- 策略标签匹配
- 设备标签筛选
- 标签统计

### 2. 设备当前版本分片表

表名：`device_version_parts`

建议字段：

- `id`
- `device_id`
- `part_name`
- `version_id`
- `version`
- `internal_version`
- `is_primary`
- `updated_at`

约束与索引：

- 唯一约束：`(device_id, part_name)`
- 查询索引：`(version_id)`
- 查询索引：`(part_name, version_id)`
- 查询索引：`(device_id, is_primary)`

用途：

- 当前版本查询
- 按 part 的设备分布统计
- 策略命中时验证源版本

### 3. 设备初始版本分片表

新增表：`device_initial_version_parts`

建议字段与 `device_version_parts` 基本一致：

- `id`
- `device_id`
- `part_name`
- `version_id`
- `version`
- `internal_version`
- `is_primary`
- `recorded_at`

约束与索引：

- 唯一约束：`(device_id, part_name)`
- 索引：`(device_id)`

原因：

- “当前版本”与“首次版本”语义不同
- 不建议混放一张表再靠 `snapshot_type` 区分，后续会拉低查询清晰度

### 4. 固件标签表

新增表：`firmware_version_tags`

建议字段：

- `id`
- `firmware_version_id`
- `tag_key`
- `tag_value`
- `created_at`
- `updated_at`

约束与索引：

- 唯一约束：`(firmware_version_id, tag_key)`
- 查询索引：`(tag_key, tag_value, firmware_version_id)`
- 索引：`(firmware_version_id)`

用途：

- 固件筛选
- 版本与设备标签匹配
- 条件校验

### 5. 策略源版本表

新增表：`upgrade_policy_source_versions`

建议字段：

- `policy_id`
- `source_version_id`
- `created_at`

约束与索引：

- 主键或唯一约束：`(policy_id, source_version_id)`
- 索引：`(source_version_id, policy_id)`

用途：

- 结构化表达允许升级的源版本范围
- 支持精确 join 校验

### 6. 策略目标设备 IMEI 表

新增表：`upgrade_policy_target_devices`

建议字段：

- `policy_id`
- `imei`
- `created_at`

约束与索引：

- 主键或唯一约束：`(policy_id, imei)`
- 索引：`(imei, policy_id)`

用途：

- 指定设备投放
- 目标设备快速命中

### 7. 策略目标批次表

新增表：`upgrade_policy_target_batches`

建议字段：

- `policy_id`
- `batch_id`
- `created_at`

约束与索引：

- 主键或唯一约束：`(policy_id, batch_id)`
- 索引：`(batch_id, policy_id)`

### 8. 策略目标标签表

新增表：`upgrade_policy_target_tags`

建议字段：

- `id`
- `policy_id`
- `tag_key`
- `tag_value`
- `operator`
- `created_at`

约束与索引：

- 唯一约束：`(policy_id, tag_key)`
- 查询索引：`(tag_key, tag_value, policy_id)`
- 索引：`(policy_id)`

说明：

- 当前业务只有等值 AND 匹配时，`operator` 可先固定为 `EQ`
- 预留 `operator` 是为了以后扩展 `IN`、`PREFIX`、`EXISTS`


## 主表字段调整建议

### devices

保留：

- `imei`
- `product_id`
- `status`
- `first_seen_at`
- `last_seen_at`
- `import_batch_id`

迁移期保留但降级为兼容字段：

- `tags`
- `version_parts`
- `initial_version_parts`

最终建议：

- 新代码不再依赖这 3 个 JSON 字段
- 稳定后删除

### firmware_versions

保留：

- `product_id`
- `version`
- `internal_version`
- `package_status`
- `file_name`
- `file_url`
- `file_size`
- `md5`
- `sha256`
- `package_uploaded_at`
- `meta`

迁移期保留：

- `tags`

最终建议：

- `tags` 删除，由 `firmware_version_tags` 替代

### upgrade_policies

保留：

- `product_id`
- `name`
- `status`
- `priority`
- `gray_rate`
- `trigger_mode`
- `target_mode`
- `target_version_id`
- `time_window`
- `remark`

迁移期保留：

- `source_versions`
- `target_imeis`
- `target_device_batch_ids`
- `target_device_tags`

最终建议：

- 以上 4 个 JSON 字段删除


## 应用层改造建议

### 一、聚合模型调整

不建议继续把关联数据隐藏在 JSON 字符串转换器里。

建议新增明确的子对象：

- `DeviceTag`
- `DeviceVersionPart`
- `DeviceInitialVersionPart`
- `FirmwareVersionTag`
- `UpgradePolicySourceVersion`
- `UpgradePolicyTargetDevice`
- `UpgradePolicyTargetBatch`
- `UpgradePolicyTargetTag`

领域实体建议调整为：

- `Device.tags` 继续暴露为 `Map<String, String>`，但底层来自 `device_tags`
- `Device.versionParts` 继续暴露为 `DeviceVersionParts`，但底层来自 `device_version_parts`
- `FirmwareVersion.tags` 继续暴露为 `Map<String, String>`，但底层来自 `firmware_version_tags`
- `UpgradePolicy.sourceVersions/targetImeis/targetDeviceBatchIds/targetDeviceTags` 保持现有领域结构，底层由多个关联表组装

这样可以尽量减少上层业务改动，同时完成底层持久化模型切换。

### 二、Repository 改造原则

`create/update` 需要改为聚合保存：

1. 保存主表
2. 删除旧关联
3. 批量插入新关联
4. 同事务提交

适用对象：

- `UpgradePolicyRepositoryImpl`
- `FirmwareVersionRepositoryImpl`
- `DeviceRepositoryImpl`

### 三、查询路径改造原则

管理后台查询：

- 列表页只查主表
- 明细页按需加载关联表

设备检查路径：

- 设备标签从 `device_tags` 装载
- 当前版本从 `device_version_parts` 装载
- 策略条件从 `upgrade_policy_*` 表装载

缓存构建：

- Redis 快照从关联表构建，不再依赖 JSON 字段反序列化


## 迁移步骤

### Phase 1: 双库补齐统一表结构

PostgreSQL 与 MySQL 同时补齐以下表：

- `device_tags`
- `device_version_parts`
- `device_initial_version_parts`
- `firmware_version_tags`
- `upgrade_policy_source_versions`
- `upgrade_policy_target_devices`
- `upgrade_policy_target_batches`
- `upgrade_policy_target_tags`

要求：

- 表名、列名、唯一约束保持一致
- 自增策略可因数据库不同而不同，但主键语义一致

### Phase 2: 数据回填

从旧 JSON 字段回填到关联表：

- `devices.tags -> device_tags`
- `devices.version_parts -> device_version_parts`
- `devices.initial_version_parts -> device_initial_version_parts`
- `firmware_versions.tags -> firmware_version_tags`
- `upgrade_policies.source_versions -> upgrade_policy_source_versions`
- `upgrade_policies.target_imeis -> upgrade_policy_target_devices`
- `upgrade_policies.target_device_batch_ids -> upgrade_policy_target_batches`
- `upgrade_policies.target_device_tags -> upgrade_policy_target_tags`

要求：

- 回填脚本可重复执行
- 使用幂等写法
- 对非法 JSON 做错误记录，不中断全量迁移

### Phase 3: 应用进入双写

改造仓储层：

- 写主表时同步写关联表
- 读操作仍可先从旧 JSON 字段读取，作为保底

建议顺序：

1. 先改 `UpgradePolicy`
2. 再改 `FirmwareVersion`
3. 最后改 `Device`

原因：

- 策略数据规模更小，验证成本最低
- 设备数据量最大，放最后更稳妥

### Phase 4: 读路径切到关联表

切换顺序：

1. 后台明细查询
2. 后台列表与筛选
3. Redis 快照构建
4. 设备升级检查主链路

要求：

- 保留开关，例如 `app.persistence.relation-model-enabled`
- 切换期间支持快速回退

### Phase 5: 停止 JSON 双写

当以下条件满足时：

- 双库读写均稳定
- 回填校验通过
- 新旧模型比对无差异

则：

- 停止更新 JSON 字段
- 仅保留只读兼容一段时间

### Phase 6: 删除旧 JSON 字段

删除：

- `devices.tags`
- `devices.version_parts`
- `devices.initial_version_parts`
- `firmware_versions.tags`
- `upgrade_policies.source_versions`
- `upgrade_policies.target_imeis`
- `upgrade_policies.target_device_batch_ids`
- `upgrade_policies.target_device_tags`


## 回填校验建议

每类对象都做新旧比对：

- 设备标签数量比对
- 设备主版本 part 比对
- 固件标签 KV 比对
- 策略源版本数量比对
- 策略目标 IMEI 数量比对
- 策略目标批次数量比对
- 策略标签 KV 比对

建议输出校验表：

- 总记录数
- 成功回填数
- 差异数
- 非法 JSON 数
- 缺失外键目标数


## 风险与处理

### 风险 1：双写不一致

处理：

- 强制仓储层事务提交
- 更新采用“先删后插”或按唯一键 UPSERT
- 上线初期增加一致性巡检任务

### 风险 2：设备量大导致回填过慢

处理：

- 按主键分页回填
- 单批控制 500 到 2000 条
- 只在低峰时段执行

### 风险 3：策略匹配性能下降

处理：

- 提前建立 `(tag_key, tag_value, policy_id)` 与 `(tag_key, tag_value, device_id)` 组合索引
- 设备检查链路优先走缓存快照，不直接实时 join 全库

### 风险 4：领域层被持久化细节污染

处理：

- 领域对象继续保留 `Map/Set/VO`
- 关联表的装配逻辑收敛在 repository/converter


## 不建议的方案

### 不建议 1：只让 MySQL 使用关联表，PostgreSQL 保持 JSONB

原因：

- 事实来源分裂
- 代码仓储层会持续分叉
- 功能测试需要双套断言

### 不建议 2：把所有 JSON 都拆成表

原因：

- `meta`、`menu_config`、`extra` 这类字段并非强结构化核心模型
- 全拆会明显增加复杂度，收益不足

### 不建议 3：设备当前版本和初始版本共用一张表再加类型字段

原因：

- 两者生命周期不同
- 查询语义不同
- 后续更容易产生误更新


## 推荐实施顺序

1. 统一 PG/MySQL 关联表基线
2. 优先迁移 `upgrade_policies`
3. 迁移 `firmware_versions.tags`
4. 迁移 `devices.tags`
5. 迁移 `devices.version_parts` 与 `devices.initial_version_parts`
6. 读路径切换
7. 删除旧 JSON 字段


## 结论

如果确定“关联表为主模型”，那么 PostgreSQL 应当与 MySQL 采用**相同或几乎相同**的关联表设计。

目标不是让两个数据库“都能跑”，而是让它们：

- 主数据结构一致
- 应用读写路径一致
- 缓存构建逻辑一致
- 迁移与测试策略一致

从当前仓库状态看，最合理的落地方式是：

- 保留少量真正需要灵活结构的 JSON 字段
- 将设备标签、设备版本分片、固件标签、策略目标条件与源版本集合全部迁移为关联表主模型
- 用分阶段双写和读切换方式完成迁移
