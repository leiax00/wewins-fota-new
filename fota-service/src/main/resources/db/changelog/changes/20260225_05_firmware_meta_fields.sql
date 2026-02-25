-- 固件版本元数据（meta）字段定义
-- 包含 i18n（多语言升级说明）和 changelog（更新日志）

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
        -- 多语言升级说明（i18n 字段）
        (
            '升级内容',
            'i18n',
            'jsonSchema.firmwareMeta.i18n',
            10,
            '{
              "kind": "json_field_definition",
              "schemaVersion": 1,
              "schema": {
                "type": "i18n",
                "required": false,
                "help": "多语言升级内容，key 为语言代码（BCP-47），value 为对应语言的描述文本",
                "validator": {
                  "i18nMaxLength": 2000
                },
                "i18nConfig": {
                  "allowCustomLocale": true,
                  "minLocales": 0,
                  "defaultLocales": ["en-US"]
                }
              }
            }'
        ),
        -- 更新日志（changelog 字段）
        (
            '更新日志',
            'changelog',
            'jsonSchema.firmwareMeta.changelog',
            20,
            '{
              "kind": "json_field_definition",
              "schemaVersion": 1,
              "schema": {
                "type": "textarea",
                "required": false,
                "placeholder": "请输入更新日志，每行一个更新点",
                "help": "固件版本的更新日志（纯文本，支持 Markdown 格式）",
                "textareaConfig": {
                  "minRows": 4,
                  "maxRows": 10
                },
                "validator": {
                  "minLength": 0,
                  "maxLength": 5000
                }
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
