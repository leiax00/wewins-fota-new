# Day 2 - 数据库架构设计最终总结

## 实施日期
2026-02-05

## 完成的工作

### 1. 审计字段和软删除（已完成）

**修改的表**（4张）
- products
- devices
- firmware_versions
- upgrade_policies

**添加的字段**
```sql
created_by BIGINT,
updated_by BIGINT,
deleted_at TIMESTAMP
```

**Java 实现**
- ✅ 4 个实体类添加审计字段
- ✅ AuditMetaObjectHandler（自动填充）
- ✅ UserContext（ThreadLocal 用户上下文）
- ✅ UserContextCleanupInterceptor（拦截器自动清理）
- ✅ WebMvcConfig（注册拦截器）

---

### 2. 设备标签功能（已完成）

**数据结构**
- devices.tags：JSONB 类型（List<String>）
- GIN 索引：idx_devices_tags_gin

**Java 实现**
- ✅ JsonbListStringTypeHandler（JSONB 类型处理器）
- ✅ Device.tags：List<String> + TypeHandler
- ✅ Device 实体类添加 @TableName(autoResultMap = true)

**查询示例**
```sql
-- 查询包含"测试设备"标签的设备
SELECT * FROM devices WHERE tags @> '["测试设备"]'::jsonb;

-- 查询同时包含多个标签的设备
SELECT * FROM devices WHERE tags @> '["测试设备", "CN"]'::jsonb;
```

---

### 3. 设备导入批次功能（已完成）⭐

**决策变更**
- ❌ 删除：分组功能（device_groups、device_group_members）
- ✅ 保留：标签功能（更灵活）
- ✅ 新增：导入批次功能（更实用）

**数据结构**
```sql
CREATE TABLE device_import_batches (
    id BIGSERIAL PRIMARY KEY,
    batch_name VARCHAR(255) NOT NULL,        -- 批次名称
    status VARCHAR(32) NOT NULL,              -- 状态（IMPORTING/SUCCESS/FAILED/PARTIAL）
    source_file VARCHAR(1024),                -- 导入文件
    total_count INTEGER DEFAULT 0,            -- 总数
    success_count INTEGER DEFAULT 0,          -- 成功数
    failed_count INTEGER DEFAULT 0,           -- 失败数
    error_message TEXT,                       -- 错误信息
    started_at TIMESTAMP,                     -- 开始时间
    finished_at TIMESTAMP,                    -- 结束时间
    -- 审计字段
    created_at TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP
);

-- devices 表添加外键
ALTER TABLE devices ADD COLUMN import_batch_id BIGINT;
```

**Java 实现**
- ✅ DeviceImportBatch 实体类
- ✅ DeviceImportBatchMapper
- ✅ Device.importBatchId 字段

**使用场景**
1. 设备批量导入（Excel/CSV）
2. 追踪设备导入来源
3. 批次管理（查询、删除、回滚）
4. 导入失败重试
5. 审计和追溯

---

## 表结构总览

### 核心表（5张）

| 表名 | 用途 | 主要字段 |
|------|------|---------|
| **products** | 产品表 | name, manufacturer, model, remark |
| **devices** | 设备表 | imei, product_id, **tags** (JSONB), **import_batch_id** |
| **firmware_versions** | 固件版本表 | product_id, version, file_url, md5, sha256 |
| **upgrade_policies** | 升级策略表 | product_id, name, remark, target_version_id, gray_rate, time_window |
| **device_import_batches** | 导入批次表 | batch_name, status, total_count, success_count, failed_count |

### 所有表统一字段

**审计字段**
- created_at, created_by
- updated_at, updated_by

**软删除**
- deleted_at (TIMESTAMP)
- @TableLogic 注解

---

## 设计亮点

### 1. 标签替代分组
**决策**：只保留标签，删除分组功能

**理由**
- ✅ 标签更灵活（一个设备可以有多个标签）
- ✅ 标签支持多维度分类（地区、测试设备、版本等）
- ✅ JSONB + GIN 索引性能优秀
- ✅ 减少表关联，查询更简单

### 2. 导入批次独立管理
**决策**：使用独立的批次表，而非简单的字符串字段

**理由**
- ✅ 支持批次状态跟踪（导入中、成功、失败）
- ✅ 支持导入统计（总数、成功数、失败数）
- ✅ 支持错误信息记录
- ✅ 便于批次回滚和重试
- ✅ 便于审计和问题追踪

### 3. TypeHandler + autoResultMap
**关键配置**
```java
@TableName(value = "devices", autoResultMap = true)
@TableField(typeHandler = JsonbListStringTypeHandler.class, jdbcType = JdbcType.OTHER)
private List<String> tags;
```

**理由**
- ✅ autoResultMap 确保 TypeHandler 在查询时生效
- ✅ TypeHandler 自动处理 JSONB 转换
- ✅ 类型安全，避免手动 JSON 处理

---

## 典型使用场景

### 场景 1：设备批量导入

```java
// 1. 创建导入批次
DeviceImportBatch batch = DeviceImportBatch.builder()
    .batchName("20250205_首批测试设备")
    .status("IMPORTING")
    .totalCount(1000)
    .sourceFile("/uploads/devices_20250205.csv")
    .build();
deviceImportBatchMapper.insert(batch);

// 2. 批量插入设备
List<Device> devices = parseExcelFile(file);
devices.forEach(device -> {
    device.setImportBatchId(batch.getId());
    device.setTags(Arrays.asList("CN", "测试设备"));
    deviceMapper.insert(device);
});

// 3. 更新批次状态
batch.setSuccessCount(950);
batch.setFailedCount(50);
batch.setStatus("PARTIAL");
batch.setFinishedAt(LocalDateTime.now());
deviceImportBatchMapper.updateById(batch);
```

### 场景 2：查询某批次的所有设备

```java
// 查询某批次导入的设备
QueryWrapper<Device> wrapper = new QueryWrapper<>();
wrapper.eq("import_batch_id", batchId)
       .isNull("deleted_at");
List<Device> devices = deviceMapper.selectList(wrapper);
```

### 场景 3：批次回滚（删除某批次的所有设备）

```java
// 软删除某批次的所有设备
QueryWrapper<Device> wrapper = new QueryWrapper<>();
wrapper.eq("import_batch_id", batchId);
deviceMapper.delete(wrapper); // MyBatis-Plus 自动处理软删除
```

### 场景 4：按标签筛选设备

```java
// 查询所有带"测试设备"标签的设备
@Select("SELECT * FROM devices " +
        "WHERE deleted_at IS NULL " +
        "AND tags @> #{tagsJson}::jsonb")
List<Device> selectByTags(@Param("tagsJson") String tagsJson);

// 使用
String tagsJson = "[\"测试设备\"]";
List<Device> devices = deviceMapper.selectByTags(tagsJson);
```

---

## 性能优化

### 索引策略

| 表 | 索引 | 类型 | 用途 |
|---|------|------|------|
| devices | idx_devices_tags_gin | GIN | JSONB 标签查询 |
| devices | idx_devices_import_batch_id | B-tree | 按批次查询设备 |
| device_import_batches | uk_device_import_batches_name | UNIQUE | 批次名称唯一 |
| device_import_batches | idx_device_import_batches_status | B-tree | 按状态查询批次 |

### 查询优化建议

1. **标签查询**：使用 GIN 索引，性能优秀
2. **批次查询**：使用 import_batch_id 索引
3. **组合查询**：标签 + 批次，可以高效组合

---

## 文件清单

### SQL 脚本
- ✅ `fota-service/src/main/resources/db/changelog/changes/V1__init_core.sql`

### Java 实体类（6个）
- ✅ Product.java
- ✅ Device.java
- ✅ FirmwareVersion.java
- ✅ UpgradePolicy.java
- ✅ DeviceImportBatch.java
- ❌ ~~DeviceGroup.java~~（已删除）
- ❌ ~~DeviceGroupMember.java~~（已删除）

### Mapper 接口（6个）
- ✅ ProductMapper
- ✅ DeviceMapper
- ✅ FirmwareVersionMapper
- ✅ UpgradePolicyMapper
- ✅ DeviceImportBatchMapper
- ❌ ~~DeviceGroupMapper~~（已删除）
- ❌ ~~DeviceGroupMemberMapper~~（已删除）

### 配置类（6个）
- ✅ AuditMetaObjectHandler.java
- ✅ UserContext.java
- ✅ UserContextCleanupInterceptor.java
- ✅ WebMvcConfig.java
- ✅ JsonbListStringTypeHandler.java
- ✅ application.yml（MyBatis-Plus 配置）

### 文档
- ✅ day-02-audit-fields-improvements.md
- ✅ day-02-tags-groups-usage.md
- ✅ day-02-tags-groups-summary.md
- ✅ day-02-database.md（任务文档）

---

## Codex Review 关键修复

### High 优先级
✅ Device 实体类添加 `autoResultMap = true`

### Medium 优先级
✅ PostgreSQL 批量插入配置：`reWriteBatchedInserts=true`
✅ SQL 注入风险修复

### Low 优先级
ℹ️ TypeHandler 返回 null（业务层判空）
ℹ️ @MappedTypes(List.class) 可能误匹配（当前无风险）

---

## 待办事项

### 短期
- [ ] 编写单元测试（TypeHandler、Mapper）
- [ ] 编写集成测试（设备导入流程）
- [ ] 性能测试（百万级设备）

### 中期
- [ ] 实现设备导入服务（解析 Excel/CSV）
- [ ] 实现批次管理界面
- [ ] 实现批次回滚功能
- [ ] 标签管理界面

### 长期
- [ ] 导入任务队列（异步导入）
- [ ] 导入失败重试机制
- [ ] 批次导入性能优化
- [ ] 标签推荐系统

---

## 总结

Day 2 的工作已经完成，主要成果：

1. ✅ **审计字段和软删除**：所有表统一，便于追踪和审计
2. ✅ **设备标签功能**：灵活的分类方式，替代分组功能
3. ✅ **导入批次管理**：完整的批次状态跟踪和统计
4. ✅ **TypeHandler 实现**：JSONB 类型安全转换
5. ✅ **代码质量**：通过 Codex review 并修复关键问题

**架构决策**：
- 标签 > 分组（更灵活）
- 独立批次表 > 简单字段（更完整）
- TypeHandler + autoResultMap（类型安全）

**下一步**：
- 实现设备导入服务
- 编写测试用例
- 性能测试和优化

---

**最后更新**：2026-02-05
**作者**：FOTA Team
**审核人**：Codex AI
