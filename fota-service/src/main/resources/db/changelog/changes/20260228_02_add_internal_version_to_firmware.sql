-- ============================================================================
-- 添加 internal_version 字段到 firmware_versions 表
-- ============================================================================
-- 目的：支持通过 version + tag 组合唯一确定固件版本
-- 背景：由于历史设计缺陷，version 号可能重复，需要 tag (internalVersion) 组合来唯一确定
-- ============================================================================

-- 1. 添加 internal_version 字段
ALTER TABLE firmware_versions
    ADD COLUMN IF NOT EXISTS internal_version VARCHAR(255);

-- 2. 添加注释
COMMENT ON COLUMN firmware_versions.internal_version IS
    '内部版本号（build tag），用于与 version 组合唯一确定固件版本（如 ASR_YEMEN_M476_V11_B03_Build02）';

-- 3. 创建索引（用于高效查询）
CREATE INDEX IF NOT EXISTS idx_fv_product_id_version_internal
    ON firmware_versions(product_id, version, internal_version)
    WHERE deleted_at IS NULL;

-- 4. 创建部分唯一索引（version + internal_version 组合在产品下唯一）
--    注意：当 internal_version 为 NULL 时不参与唯一性约束
CREATE UNIQUE INDEX uk_fv_product_version_internal
    ON firmware_versions(product_id, version, internal_version)
    WHERE deleted_at IS NULL AND internal_version IS NOT NULL;

COMMENT ON INDEX uk_fv_product_version_internal IS
    '固件版本组合唯一约束（仅未删除记录且有 internal_version）：产品下相同 version 和 internal_version 组合只能有一个';
