-- ============================================================================
-- 添加 internal_version 字段到 firmware_versions 表
-- ============================================================================
-- 目的：支持通过 version + internal_version 组合唯一确定固件版本
-- 背景：由于历史设计缺陷，version 号可能重复，需要 internal_version 组合来唯一确定
-- ============================================================================

-- 1. 添加 internal_version 字段
ALTER TABLE firmware_versions
    ADD COLUMN IF NOT EXISTS internal_version VARCHAR(255);

-- 2. 添加注释
COMMENT ON COLUMN firmware_versions.internal_version IS
    '内部版本号（build tag），用于与 version 组合唯一确定固件版本（如 ASR_YEMEN_M476_V11_B03_Build02）';

-- 3. 数据迁移：将 tags->>'inner_version' 复制到 internal_version 列
UPDATE firmware_versions
SET internal_version = tags->>'inner_version'
WHERE jsonb_exists(tags, 'inner_version')
  AND internal_version IS NULL;

-- 4. 删除旧的 product + version 唯一索引
DROP INDEX IF EXISTS uk_fv_product_version_active;

-- 5. 创建普通索引用于高效查询（product_id + internal_version 组合查询）
CREATE INDEX IF NOT EXISTS idx_fv_product_id_internal_version
    ON firmware_versions(product_id, internal_version)
    WHERE deleted_at IS NULL;

COMMENT ON INDEX idx_fv_product_id_internal_version IS
    '固件版本查询索引（仅未删除记录）：支持按产品+内部版本号查询';

-- 6. 创建部分唯一索引（product_id + version + internal_version 组合在产品下唯一）
--    注意：当 internal_version 为 NULL 时不参与唯一性约束
CREATE UNIQUE INDEX uk_fv_product_version_internal
    ON firmware_versions(product_id, version, internal_version)
    WHERE deleted_at IS NULL;

COMMENT ON INDEX uk_fv_product_version_internal IS
    '固件版本组合唯一约束（仅未删除记录且有 internal_version）：产品下相同 version 和 internal_version 组合只能有一个';
