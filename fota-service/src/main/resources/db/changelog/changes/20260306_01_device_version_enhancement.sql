-- 20260306_01_device_version_enhancement.sql
-- FOTA 系统：设备版本字段增强
-- 创建日期: 2026-03-06
-- 作者: FOTA 团队
--
-- 变更说明:
-- 1. 添加 version_parts JSONB 字段（多部分版本存储）
-- 2. 添加 first_seen_at 字段（首次上线时间，默认 NULL）
-- 3. 添加 initial_version_parts JSONB 字段（初始版本记录）
-- 4. 移除冗余字段 current_version_id（使用 version_parts 替代）
-- 5. 创建 GIN 索引支持 JSONB 查询

-- ============================================================================
-- 1. 添加新字段
-- ============================================================================
ALTER TABLE devices 
ADD COLUMN IF NOT EXISTS version_parts JSONB 
DEFAULT '{"parts": {}, "primaryPart": "main"}'::jsonb,
ADD COLUMN IF NOT EXISTS first_seen_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS initial_version_parts JSONB 
DEFAULT '{"parts": {}, "primaryPart": "main"}'::jsonb;

COMMENT ON COLUMN devices.version_parts IS 
'多部分版本信息（JSONB）: {"parts": {"main": {"versionId": 101, "version": "1.0.0", "updatedAt": "xxx"}}, "primaryPart": "main"}';

COMMENT ON COLUMN devices.first_seen_at IS 
'设备第一次上线时间（首次检测时间）';

COMMENT ON COLUMN devices.initial_version_parts IS 
'设备第一次上线的版本信息（JSONB）: {"parts": {"main": {"versionId": 101, "version": "1.0.0", "updatedAt": "xxx"}}, "primaryPart": "main"}';

-- ============================================================================
-- 2. 移除冗余字段 current_version_id
-- ============================================================================
ALTER TABLE devices DROP COLUMN IF EXISTS current_version_id;

-- ============================================================================
-- 3. 创建 GIN 索引
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_devices_version_parts_gin 
ON devices USING GIN (version_parts);

CREATE INDEX IF NOT EXISTS idx_devices_initial_version_parts_gin 
ON devices USING GIN (initial_version_parts);

CREATE INDEX IF NOT EXISTS idx_devices_first_seen_at 
ON devices (first_seen_at);

-- ============================================================================
-- 4. 添加 part 字段到固件元数据字典
-- ============================================================================
WITH dict_type AS (
    SELECT id
    FROM sys_dict_type
    WHERE code = 'json_schema.firmware_meta'
    LIMIT 1
)
INSERT INTO sys_dict_item (dict_type_id, label, value, i18n_key, sort_order, status, extra)
SELECT
    dict_type.id,
    t.label,
    t.value,
    t.i18n_key,
    t.sort_order,
    'active',
    t.extra::jsonb
FROM dict_type
JOIN (
    VALUES
        (
            '分区固件标识',
            'part',
            'jsonSchema.firmwareMeta.part',
            3,
            '{
              "kind": "json_field_definition",
              "schemaVersion": 1,
              "schema": {
                "type": "select",
                "required": false,
                "defaultValue": "main",
                "help": "分区固件标识，用于多部分固件升级（主固件、引导加载器等）",
                "options": [
                  {"label": "主固件", "value": "main"},
                  {"label": "引导加载器", "value": "bootloader"},
                  {"label": "固件", "value": "firmware"},
                  {"label": "应用", "value": "app"},
                  {"label": "系统", "value": "system"}
                ]
              }
            }'
        )
) AS t(label, value, i18n_key, sort_order, extra) ON TRUE
ON CONFLICT (dict_type_id, value) DO UPDATE
SET
    label = EXCLUDED.label,
    i18n_key = EXCLUDED.i18n_key,
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status,
    extra = EXCLUDED.extra,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================================
-- 5. 验证查询示例
-- ============================================================================
-- 查询特定部分的版本
-- SELECT * FROM devices 
-- WHERE version_parts->'parts'->'main'->>'versionId' = '101';

-- 查询有bootloader部分的所有设备
-- SELECT * FROM devices 
-- WHERE version_parts->'parts' ? 'bootloader';

-- 查询首次上线时间在某日期之后的设备
-- SELECT * FROM devices 
-- WHERE first_seen_at > '2026-01-01'
-- ORDER BY first_seen_at DESC;
