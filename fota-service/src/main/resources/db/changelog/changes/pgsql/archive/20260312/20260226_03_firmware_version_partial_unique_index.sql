-- ============================================================================
-- 修复软删除 + 唯一约束冲突
-- ============================================================================
-- 问题：
--   软删除(deletedAt) + 全表唯一约束 uk_fv_product_version 导致：
--   - 删除版本 1.0.0 后，无法重新创建版本 1.0.0
--   - 应用层检查 deletedAt IS NULL，数据库检查全表
-- 解决方案：
--   使用 PostgreSQL 部分唯一索引，只对未删除记录(deletedAt IS NULL)生效
--   允许已删除记录有重复的 (product_id, version)
-- ============================================================================

-- 1. 删除旧的唯一约束（而不是索引）
--    PostgreSQL 中 UNIQUE CONSTRAINT 会自动创建索引
--    必须先删除约束，索引会自动删除
ALTER TABLE firmware_versions
    DROP CONSTRAINT IF EXISTS uk_fv_product_version;

-- 2. 创建部分唯一索引（推荐方式）
--    创建部分唯一索引，PostgreSQL 会自动 enforce uniqueness
CREATE UNIQUE INDEX uk_fv_product_version_active
    ON firmware_versions(product_id, version)
    WHERE deleted_at IS NULL;

-- 3. 添加注释
COMMENT ON INDEX uk_fv_product_version_active IS
    '固件版本唯一约束（仅未删除记录）：产品下同一版本号只能有一个活跃版本';
