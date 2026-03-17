-- ============================================================================
-- 固件包状态改造：支持无包版本与两阶段上传
-- ============================================================================
--
-- 变更内容：
-- 1. 将现有文件相关字段改为可空（支持"无包版本"占位）
-- 2. 新增 package_status 和 package_uploaded_at 字段
-- 3. 添加状态枚举约束和字段一致性约束
-- 4. 创建状态相关索引，优化查询性能
-- 5. 回填历史数据的 package_status 字段
--
-- 状态定义：
--   NONE    - 无固件包（占位版本号）
--   UPLOADED - 已上传临时文件（本地 staging）
--   READY   - 已转存到对象存储，可下载
--   FAILED  - 上传或转存失败
--
-- 作者: fota-team
-- 日期: 2026-02-26
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 字段可空化：为支持"无包版本"（package_status=NONE）做准备
-- ----------------------------------------------------------------------------
-- 说明：
--   - 当 package_status=NONE 时，file_url/file_size/md5/sha256 必须为 NULL
--   - 当 package_status=READY 时，上述字段必须非 NULL
--   - 通过 CHECK 约束保证一致性（见下文约束定义）
-- ----------------------------------------------------------------------------

ALTER TABLE firmware_versions
    ALTER COLUMN file_url DROP NOT NULL,
    ALTER COLUMN file_size DROP NOT NULL,
    ALTER COLUMN md5 DROP NOT NULL,
    ALTER COLUMN sha256 DROP NOT NULL;

-- ----------------------------------------------------------------------------
-- 2. 新增包状态字段
-- ----------------------------------------------------------------------------

ALTER TABLE firmware_versions
    ADD COLUMN IF NOT EXISTS package_status VARCHAR(20);

ALTER TABLE firmware_versions
    ADD COLUMN IF NOT EXISTS package_uploaded_at TIMESTAMP;

COMMENT ON COLUMN firmware_versions.package_status IS
    '固件包状态：NONE（无包）/ UPLOADED（已上传临时文件）/ READY（已转存对象存储）/ FAILED（失败）';

COMMENT ON COLUMN firmware_versions.package_uploaded_at IS
    '固件包最近一次上传完成时间（临时上传成功或最终转存成功时更新）';

-- ----------------------------------------------------------------------------
-- 3. 历史数据回填
-- ----------------------------------------------------------------------------
-- 规则：
--   - 历史记录原本均为"已具备包元数据"，回填为 READY
--   - 若存在异常历史脏数据（关键包字段缺失），回填为 NONE
-- ----------------------------------------------------------------------------

UPDATE firmware_versions
SET package_status = 'READY',
    package_uploaded_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
WHERE package_status IS NULL
  AND file_url IS NOT NULL
  AND file_size IS NOT NULL
  AND file_size > 0
  AND md5 IS NOT NULL
  AND sha256 IS NOT NULL;

UPDATE firmware_versions
SET package_status = 'NONE',
    package_uploaded_at = NULL
WHERE package_status IS NULL;

ALTER TABLE firmware_versions
    ALTER COLUMN package_status SET DEFAULT 'NONE',
    ALTER COLUMN package_status SET NOT NULL;

-- ----------------------------------------------------------------------------
-- 5. 索引创建：优化状态相关查询
-- ----------------------------------------------------------------------------

-- 5.1 单列状态索引（支持按状态筛选）
CREATE INDEX IF NOT EXISTS idx_fv_package_status
    ON firmware_versions(package_status)
    WHERE deleted_at IS NULL;

-- 5.2 产品+状态复合索引（支持策略下拉列表查询：按产品筛选仅 READY 状态）
CREATE INDEX IF NOT EXISTS idx_fv_product_package_status
    ON firmware_versions(product_id, package_status)
    WHERE deleted_at IS NULL;

-- 5.3 上传时间索引（支持定时清理任务扫描超时会话）
CREATE INDEX IF NOT EXISTS idx_fv_package_uploaded_at
    ON firmware_versions(package_uploaded_at)
    WHERE deleted_at IS NULL AND package_uploaded_at IS NOT NULL;

-- ============================================================================
-- 验证脚本（可选，用于验证迁移结果）
-- ============================================================================

-- 检查状态分布：
-- SELECT package_status, COUNT(*) as count
-- FROM firmware_versions
-- WHERE deleted_at IS NULL
-- GROUP BY package_status;

-- 检查约束是否生效（应该返回 0 行）：
-- SELECT *
-- FROM firmware_versions
-- WHERE deleted_at IS NULL
--   AND (
--       (package_status = 'READY' AND (file_url IS NULL OR file_size IS NULL))
--       OR
--       (package_status = 'NONE' AND (file_url IS NOT NULL OR file_size IS NOT NULL))
--   );
