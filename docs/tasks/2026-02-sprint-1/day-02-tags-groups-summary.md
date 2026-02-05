# Day 2 - 设备标签和分组功能总结

## 实施日期
2026-02-05

## 功能概述

为 FOTA 系统添加设备标签和分组功能，支持灵活的设备管理和筛选。

### 核心功能
- **标签（Tags）**：使用 JSONB 存储，支持多标签、高效查询
- **分组（Groups）**：多对多关系，支持多维度分组
- **组合查询**：支持标签和分组的组合筛选

---

## 完成的修改

### 1. 数据库表设计

#### devices 表（添加标签字段）
```sql
ALTER TABLE devices ADD COLUMN tags JSONB;
CREATE INDEX idx_devices_tags_gin ON devices USING GIN (tags);
```

#### device_groups 表（新建）
```sql
CREATE TABLE device_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    remark TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP
);
```

#### device_group_members 表（新建）
```sql
CREATE TABLE device_group_members (
    id BIGSERIAL PRIMARY KEY,
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

### 2. Java 实体类

#### Device.java
- 添加 `tags` 字段（`List<String>` 类型）
- 使用 `@TableField(typeHandler = JsonbListStringTypeHandler.class)`
- **关键修复**：添加 `@TableName(value = "devices", autoResultMap = true)`

#### DeviceGroup.java
- 新建分组实体类
- 包含审计字段和软删除支持

#### DeviceGroupMember.java
- 新建关联实体类
- 使用代理主键 `id` + 业务唯一约束 `(device_id, group_id)`

---

### 3. TypeHandler 实现

#### JsonbListStringTypeHandler.java
- 实现 `BaseTypeHandler<List<String>>`
- 使用 Jackson ObjectMapper 处理 JSON 序列化
- 支持 PostgreSQL JSONB 类型
- 完整的异常处理

**关键代码**：
```java
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbListStringTypeHandler extends BaseTypeHandler<List<String>> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject jsonbObject = new PGobject();
        jsonbObject.setType("jsonb");
        jsonbObject.setValue(OBJECT_MAPPER.writeValueAsString(parameter));
        ps.setObject(i, jsonbObject);
    }

    private List<String> parseJsonb(String json) throws SQLException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
    }
}
```

---

### 4. Mapper 接口

- DeviceGroupMapper：继承 BaseMapper
- DeviceGroupMemberMapper：继承 BaseMapper

---

### 5. 使用文档

创建了完整的使用指南：
- Java 代码示例
- SQL 查询示例
- 性能优化建议
- 常见问题解答

---

## Codex Review 修复

### High 优先级
✅ **Device 实体类添加 `autoResultMap = true`**
- 问题：TypeHandler 在读取时可能不生效
- 修复：`@TableName(value = "devices", autoResultMap = true)`

### Medium 优先级
✅ **文档中的 PostgreSQL 批量插入配置**
- 修复：`reWriteBatchedInserts=true`（而非 MySQL 的 `rewriteBatchedStatements=true`）

✅ **文档中的 SQL 注入风险**
- 修复：使用 MyBatis-Plus 参数化查询，避免字符串拼接

### Low 优先级
ℹ️ **TypeHandler 返回 null 而非空集合**
- 说明：当前实现可以接受，业务层需要判空

ℹ️ **@MappedTypes(List.class) 可能误匹配**
- 说明：当前无风险，如有其他 List 类型字段需要注意

---

## 设计决策

### 为什么选择 JSONB 存储标签？
1. **灵活性**：支持动态标签，无需修改表结构
2. **性能**：GIN 索引提供高效的 JSON 查询
3. **简洁性**：不需要额外的关联表

### 为什么选择多对多分组？
1. **灵活性**：一个设备可以属于多个分组
2. **多维度**：支持按地区、业务线、试点等维度同时分组
3. **查询效率**：通过关联表支持复杂的组合查询

### 为什么添加代理主键？
1. **MyBatis-Plus 支持**：完整支持 `selectById`、`updateById`
2. **性能**：主键索引更高效
3. **扩展性**：便于添加额外的关联字段

---

## 使用示例

### 设置标签
```java
Device device = new Device();
device.setImei("123456789012345");
device.setTags(Arrays.asList("测试设备", "CN", "beta"));
deviceMapper.insert(device);
```

### 查询标签（SQL）
```sql
SELECT * FROM devices
WHERE tags @> '["测试设备"]'::jsonb;
```

### 分组管理
```java
// 创建分组
DeviceGroup group = DeviceGroup.builder()
    .name("试点设备")
    .build();
deviceGroupMapper.insert(group);

// 添加设备到分组
DeviceGroupMember member = DeviceGroupMember.builder()
    .deviceId(1L)
    .groupId(group.getId())
    .build();
deviceGroupMemberMapper.insert(member);
```

---

## 性能优化

### 索引策略
- `tags` 字段：GIN 索引（`idx_devices_tags_gin`）
- `device_group_members.group_id`：B-tree 索引
- `device_group_members.device_id`：B-tree 索引

### 缓存建议
对于高频的升级策略查询，建议使用 Redis 缓存：
- 缓存策略：设备ID列表
- 过期时间：1小时
- 更新策略：策略变更时主动刷新

---

## 待优化项

### 短期
- [ ] 编写单元测试（TypeHandler、Mapper）
- [ ] 性能测试（百万级设备的标签查询）
- [ ] 标签规范化（大小写、格式）

### 中期
- [ ] 标签管理界面（CRUD）
- [ ] 分组管理界面（树形结构）
- [ ] 标签和分组的批量操作

### 长期
- [ ] 策略快照机制（Redis 缓存）
- [ ] 标签推荐系统
- [ ] 设备分组的层级支持

---

## 参考资料

- [PostgreSQL JSONB 文档](https://www.postgresql.org/docs/current/datatype-json.html)
- [MyBatis-Plus TypeHandler](https://baomidou.com/pages/1e8d70/)
- [PostgreSQL GIN 索引](https://www.postgresql.org/docs/current/indexes-types.html#INDEXES-TYPES-GIN)

---

**最后更新**：2026-02-05
**作者**：FOTA Team
**审核人**：Codex AI
