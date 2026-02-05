# FirmwareVersion Meta 字段使用指南

## 概述

为 `firmware_versions` 表添加了 `tags` 和 `meta` 字段，支持版本标签过滤和多语言元数据。

### 字段说明

#### tags（版本标签）
- **类型**：JSONB 数组
- **用途**：升级策略过滤
- **Java 类型**：`List<String>`
- **索引**：GIN 索引（高性能查询）

#### meta（扩展元数据）
- **类型**：JSONB 对象
- **用途**：多语言描述、changelog、扩展字段
- **Java 类型**：`JsonNode`
- **索引**：GIN 索引

---

## 数据结构示例

### tags 字段示例

```json
["stable", "beta", "critical", "security-fix"]
```

**常用标签**：
- `stable`：稳定版本
- `beta`：测试版本
- `critical`：关键更新（重要安全修复）
- `security-fix`：安全修复
- `bug-fix`：Bug 修复
- `feature`：新功能
- `rollback`：回滚版本

### meta 字段示例

```json
{
  "i18n": {
    "zh-CN": {
      "description": "修复蓝牙断连问题",
      "changelog": "1. 修复蓝牙断连\n2. 优化功耗\n3. 提升系统稳定性"
    },
    "en-US": {
      "description": "Fix Bluetooth disconnection",
      "changelog": "1. Fix Bluetooth drop\n2. Optimize power\n3. Improve system stability"
    }
  },
  "notes": {
    "min_app_version": "2.3.0",
    "requires_reboot": true,
    "file_size_mb": 45.6,
    "download_count": 0
  }
}
```

**字段说明**：
- `i18n`：多语言数据
  - `zh-CN`：简体中文
  - `en-US`：英语
  - 可扩展其他语言（ja-JP, ko-KR 等）
- `notes`：扩展字段
  - `min_app_version`：最低 App 版本要求
  - `requires_reboot`：是否需要重启
  - `file_size_mb`：文件大小（MB）
  - 其他自定义字段

---

## Java 使用示例

### 1. 创建固件版本

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.entity.FirmwareVersion;
import com.wewins.fota.mapper.FirmwareVersionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Arrays;

@Service
public class FirmwareVersionService {

    @Autowired
    private FirmwareVersionMapper firmwareVersionMapper;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 创建固件版本（包含标签和元数据）
     */
    public void createFirmwareVersion() {
        FirmwareVersion version = new FirmwareVersion();
        version.setProductId(1L);
        version.setVersion("1.2.0");
        version.setFileUrl("https://cdn.example.com/firmware/v1.2.0.bin");
        version.setFileSize(47657472L);
        version.setMd5("abc123...");
        version.setSha256("def456...");

        // 设置标签
        version.setTags(Arrays.asList("stable", "bug-fix"));

        // 构建元数据
        ObjectNode meta = OBJECT_MAPPER.createObjectNode();

        // 多语言数据
        ObjectNode zhCn = OBJECT_MAPPER.createObjectNode();
        zhCn.put("description", "修复蓝牙断连问题");
        zhCn.put("changelog", "1. 修复蓝牙断连\n2. 优化功耗");

        ObjectNode enUs = OBJECT_MAPPER.createObjectNode();
        enUs.put("description", "Fix Bluetooth disconnection");
        enUs.put("changelog", "1. Fix Bluetooth drop\n2. Optimize power");

        ObjectNode i18n = OBJECT_MAPPER.createObjectNode();
        i18n.set("zh-CN", zhCn);
        i18n.set("en-US", enUs);
        meta.set("i18n", i18n);

        // 扩展字段
        ObjectNode notes = OBJECT_MAPPER.createObjectNode();
        notes.put("min_app_version", "2.3.0");
        notes.put("requires_reboot", true);
        notes.put("file_size_mb", 45.6);
        meta.set("notes", notes);

        version.setMeta(meta);

        // 插入数据库
        firmwareVersionMapper.insert(version);
    }

    /**
     * 批量设置标签
     */
    public void addTagsToVersion(Long versionId, List<String> tags) {
        FirmwareVersion version = firmwareVersionMapper.selectById(versionId);
        if (version != null) {
            List<String> currentTags = version.getTags();
            if (currentTags == null) {
                currentTags = new ArrayList<>();
            }
            currentTags.addAll(tags);
            version.setTags(currentTags);
            firmwareVersionMapper.updateById(version);
        }
    }
}
```

### 2. 查询固件版本

```java
/**
 * 查询某个产品的稳定版本
 */
public List<FirmwareVersion> getStableVersions(Long productId) {
    QueryWrapper<FirmwareVersion> wrapper = new QueryWrapper<>();
    wrapper.eq("product_id", productId)
           .isNull("deleted_at");

    // 标签过滤需要使用自定义 SQL
    return firmwareVersionMapper.selectByProductAndTags(productId, Arrays.asList("stable"));
}
```

### 3. 获取多语言 Changelog

```java
/**
 * 获取指定语言的 changelog
 */
public String getChangelog(Long versionId, String language) {
    FirmwareVersion version = firmwareVersionMapper.selectById(versionId);
    if (version != null && version.getMeta() != null) {
        JsonNode i18n = version.getMeta().path("i18n");
        JsonNode langNode = i18n.path(language);
        if (langNode.has("changelog")) {
            return langNode.get("changelog").asText();
        }
    }
    return null;
}

/**
 * 获取中文 changelog（带默认值）
 */
public String getChangelogZhCN(Long versionId) {
    String changelog = getChangelog(versionId, "zh-CN");
    return changelog != null ? changelog : "暂无更新日志";
}
```

---

## SQL 查询示例

### 1. 按标签查询

```sql
-- 查询带 "stable" 标签的版本
SELECT *
FROM firmware_versions
WHERE deleted_at IS NULL
  AND tags @> '["stable"]'::jsonb
ORDER BY version DESC;
```

```sql
-- 查询带多个标签的版本（同时满足）
SELECT *
FROM firmware_versions
WHERE deleted_at IS NULL
  AND tags @> '["stable", "security-fix"]'::jsonb;
```

```sql
-- 查询带任意一个标签的版本（满足其一）
SELECT *
FROM firmware_versions
WHERE deleted_at IS NULL
  AND (tags ? 'stable' OR tags ? 'beta');
```

### 2. 按 meta 查询

```sql
-- 查询需要重启的版本（notes.requires_reboot = true）
SELECT *
FROM firmware_versions
WHERE deleted_at IS NULL
  AND meta->'notes'->>'requires_reboot' = 'true';
```

```sql
-- 查询中文描述
SELECT
    id,
    version,
    meta->'i18n'->'zh-CN'->>'description' AS description_zh_cn
FROM firmware_versions
WHERE id = :versionId;
```

### 3. 组合查询（产品 + 标签）

```sql
-- 查询某产品的 stable 版本
SELECT *
FROM firmware_versions
WHERE product_id = :productId
  AND deleted_at IS NULL
  AND tags @> '["stable"]'::jsonb
ORDER BY version DESC
LIMIT 1;
```

### 4. 统计查询

```sql
-- 统计各标签的版本数量
SELECT
    jsonb_array_elements_text(tags) AS tag,
    COUNT(*) AS count
FROM firmware_versions
WHERE deleted_at IS NULL
GROUP BY tag
ORDER BY count DESC;
```

---

## 自定义 Mapper 方法

### FirmwareVersionMapper.java

```java
/**
 * FirmwareVersion Mapper 接口
 */
@Mapper
public interface FirmwareVersionMapper extends BaseMapper<FirmwareVersion> {

    /**
     * 根据标签查询版本
     */
    @Select("SELECT * FROM firmware_versions " +
            "WHERE product_id = #{productId} " +
            "AND deleted_at IS NULL " +
            "AND tags @> #{tagsJson}::jsonb " +
            "ORDER BY version DESC")
    List<FirmwareVersion> selectByProductAndTags(
        @Param("productId") Long productId,
        @Param("tagsJson") String tagsJson
    );

    /**
     * 查询某产品的最新版本（按标签）
     */
    @Select("SELECT * FROM firmware_versions " +
            "WHERE product_id = #{productId} " +
            "AND deleted_at IS NULL " +
            "AND tags @> #{tagsJson}::jsonb " +
            "ORDER BY version DESC " +
            "LIMIT 1")
    FirmwareVersion selectLatestVersionByTags(
        @Param("productId") Long productId,
        @Param("tagsJson") String tagsJson
    );

    /**
     * 查询带特定标签的所有版本
     */
    @Select("SELECT * FROM firmware_versions " +
            "WHERE deleted_at IS NULL " +
            "AND tags @> #{tagsJson}::jsonb")
    List<FirmwareVersion> selectByTags(@Param("tagsJson") String tagsJson);
}
```

### 使用示例

```java
// 查询某产品的 stable 版本
String tagsJson = "[\"stable\"]";
List<FirmwareVersion> versions = firmwareVersionMapper.selectByProductAndTags(1L, tagsJson);

// 查询某产品的最新 stable 版本
FirmwareVersion latest = firmwareVersionMapper.selectLatestVersionByTags(1L, tagsJson);

// 查询所有带 "critical" 标签的版本
String criticalTags = "[\"critical\"]";
List<FirmwareVersion> criticalVersions = firmwareVersionMapper.selectByTags(criticalTags);
```

---

## 升级策略中的标签过滤

在升级策略中使用标签过滤目标版本：

```java
/**
 * 根据升级策略筛选目标版本
 */
public List<FirmwareVersion> findTargetVersions(UpgradePolicy policy) {
    // 获取策略要求的版本标签
    List<String> requiredTags = policy.getVersionTags();

    // 转换为 JSON
    String tagsJson = OBJECT_MAPPER.writeValueAsString(requiredTags);

    // 查询符合条件的版本
    List<FirmwareVersion> versions = firmwareVersionMapper.selectByProductAndTags(
        policy.getProductId(),
        tagsJson
    );

    return versions;
}
```

---

## API 响应示例

### 获取版本列表（包含多语言）

```java
@GetMapping("/versions/{productId}")
public List<FirmwareVersionDTO> getVersions(
    @PathVariable Long productId,
    @RequestParam(defaultValue = "zh-CN") String lang
) {
    List<FirmwareVersion> versions = firmwareVersionMapper.selectList(
        new QueryWrapper<FirmwareVersion>()
            .eq("product_id", productId)
            .isNull("deleted_at")
            .orderByDesc("version")
    );

    return versions.stream()
        .map(version -> toDTO(version, lang))
        .collect(Collectors.toList());
}

private FirmwareVersionDTO toDTO(FirmwareVersion version, String lang) {
    FirmwareVersionDTO dto = new FirmwareVersionDTO();
    dto.setId(version.getId());
    dto.setVersion(version.getVersion());
    dto.setFileUrl(version.getFileUrl());
    dto.setFileSize(version.getFileSize());
    dto.setTags(version.getTags());

    // 提取多语言数据
    if (version.getMeta() != null) {
        JsonNode i18n = version.getMeta().path("i18n");
        JsonNode langNode = i18n.path(lang);

        if (langNode.has("description")) {
            dto.setDescription(langNode.get("description").asText());
        }
        if (langNode.has("changelog")) {
            dto.setChangelog(langNode.get("changelog").asText());
        }

        // 提取扩展字段
        JsonNode notes = version.getMeta().path("notes");
        if (notes.has("min_app_version")) {
            dto.setMinAppVersion(notes.get("min_app_version").asText());
        }
        if (notes.has("requires_reboot")) {
            dto.setRequiresReboot(notes.get("requires_reboot").asBoolean());
        }
    }

    return dto;
}
```

---

## 性能优化

### 索引使用

| 索引 | 类型 | 用途 |
|------|------|------|
| idx_fv_tags_gin | GIN | 标签查询（@> 操作符） |
| idx_fv_meta_gin | GIN | 元数据查询 |
| idx_fv_product_id | B-tree | 产品查询 |
| idx_fv_deleted_at | Partial | 软删除过滤 |

### 查询优化建议

1. **标签查询**：使用 `@>` 操作符 + GIN 索引
2. **元数据查询**：避免深度嵌套查询，考虑冗余字段
3. **多语言**：在应用层缓存常用语言的翻译
4. **组合查询**：产品 + 标签，索引效率高

---

## 常见问题

### Q1: 如何添加新的语言支持？

**A**: 在 `meta.i18n` 下添加新的语言节点：

```java
ObjectNode jaJp = OBJECT_MAPPER.createObjectNode();
jaJp.put("description", "日本語の説明");
jaJp.put("changelog", "変更ログ");

i18n.set("ja-JP", jaJp);
```

### Q2: 标签查询性能如何？

**A**: GIN 索引提供高效查询，百万级版本下标签查询通常在毫秒级。

### Q3: 如何处理缺失的翻译？

**A**: 在应用层处理，返回默认语言或空值：

```java
String getDescription(JsonNode meta, String lang) {
    JsonNode desc = meta.path("i18n").path(lang).path("description");
    if (desc.isMissingNode()) {
        // 返回英文作为默认
        desc = meta.path("i18n").path("en-US").path("description");
    }
    return desc.asText(null);
}
```

---

## 总结

- **tags**：版本标签，用于策略过滤（高性能）
- **meta**：多语言元数据，灵活扩展
- **GIN 索引**：支持高效的 JSONB 查询
- **TypeHandler**：自动处理 JSONB 转换

---

**最后更新**：2026-02-05
**作者**：FOTA Team
