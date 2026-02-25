-- JSON 字段配置化示例数据（MVP）
--
-- 约定：
--   value = JSON 字段 key
--   label = 字段展示名
--   extra = JsonFieldConfig（JSONB）
--
-- JsonFieldConfig 结构：
-- {
--   "kind": "json_field_definition",
--   "schemaVersion": 1,
--   "schema": {
--     "type": "string" | "textarea" | "number" | "boolean" | "select" | "i18n",
--     "required": boolean,
--     "defaultValue": any,
--     "placeholder": string,
--     "help": string,
--     "options": [{"label": string, "value": string|number}],
--     "textareaConfig": {
--       "minRows": number,
--       "maxRows": number
--     },
--     "i18nConfig": {
--       "allowCustomLocale": boolean,
--       "minLocales": number,
--       "defaultLocales": string[]
--     },
--     "validator": {
--       "pattern": string,
--       "min": number,
--       "max": number,
--       "minLength": number,
--       "maxLength": number,
--       "i18nMaxLength": number
--     }
--   }
-- }

-- ============================================
-- 设备标签字段定义示例（json_schema.device_tags）
-- ============================================
WITH dict_type AS (
    SELECT id
    FROM sys_dict_type
    WHERE code = 'json_schema.device_tags'
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
        -- 环境（单选下拉）
        (
            '环境',
            'env',
            'jsonSchema.deviceTags.env',
            20,
            '{
              "kind": "json_field_definition",
              "schemaVersion": 1,
              "schema": {
                "type": "select",
                "required": false,
                "defaultValue": "prod",
                "options": [
                  {"label": "生产", "value": "prod"},
                  {"label": "预发", "value": "staging"},
                  {"label": "测试", "value": "test"},
                  {"label": "开发", "value": "dev"}
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

-- ============================================
-- 固件版本标签字段定义示例（json_schema.firmware_tags）
-- ============================================
WITH dict_type AS (
    SELECT id
    FROM sys_dict_type
    WHERE code = 'json_schema.firmware_tags'
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
        -- 固件内部版本
        (
            '固件内部版本',
            'inner_version',
            'jsonSchema.firmwareTags.inner_version',
            10,
            '{
              "kind": "json_field_definition",
              "schemaVersion": 1,
              "schema": {
                "type": "string",
                "required": true,
                "placeholder": "固件TAG",
                "help": "固件的内部版本号"
              }
            }'
        ),
        -- 构建类型（单选下拉）
        (
            '构建类型',
            'build_type',
            'jsonSchema.firmwareTags.build_type',
            20,
            '{
              "kind": "json_field_definition",
              "schemaVersion": 1,
              "schema": {
                "type": "select",
                "required": false,
                "defaultValue": "release",
                "options": [
                  {"label": "正式版", "value": "release"},
                  {"label": "调试版", "value": "debug"}
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
