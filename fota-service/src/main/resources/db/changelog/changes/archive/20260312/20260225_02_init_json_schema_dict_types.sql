-- 初始化 JSON 字段配置化字典类型（MVP）
-- 目标：为设备 tags、固件 tags/meta 字段提供配置化管理能力
--
-- 约定：
--   - 字典类型编码格式：json_schema.{entity}_{field}
--   - 字典项 value = JSON 字段名（如 "region"、"env"）
--   - 字典项 label = 字段显示名
--   - 字典项 extra = JsonFieldConfig（JSONB 格式，包含字段类型、校验规则等）
--
-- 使用说明：
--   1. 管理员在"系统管理 > 字典管理"中配置字段定义
--   2. 前端 JsonFieldEditor 组件根据字典配置动态生成表单
--   3. 用户填写表单后自动转换为 JSON 字符串存储

-- 设备标签字段定义
INSERT INTO sys_dict_type (code, name, i18n_key, status, description)
VALUES (
    'json_schema.device_tags',
    '设备标签字段定义',
    'jsonSchema.deviceTags',
    'active',
    '用于定义设备 tags 字段的配置化子字段（支持地区、环境、用户等级等）'
)
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    i18n_key = EXCLUDED.i18n_key,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 固件版本标签字段定义
INSERT INTO sys_dict_type (code, name, i18n_key, status, description)
VALUES (
    'json_schema.firmware_tags',
    '固件版本标签字段定义',
    'jsonSchema.firmwareTags',
    'active',
    '用于定义固件版本 tags 字段的配置化子字段（支持稳定性、构建类型等）'
)
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    i18n_key = EXCLUDED.i18n_key,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

-- 固件版本元数据字段定义
INSERT INTO sys_dict_type (code, name, i18n_key, status, description)
VALUES (
    'json_schema.firmware_meta',
    '固件版本元数据字段定义',
    'jsonSchema.firmwareMeta',
    'active',
    '用于定义固件版本 meta 字段的配置化子字段（支持 i18n、changelog 等）'
)
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    i18n_key = EXCLUDED.i18n_key,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;
