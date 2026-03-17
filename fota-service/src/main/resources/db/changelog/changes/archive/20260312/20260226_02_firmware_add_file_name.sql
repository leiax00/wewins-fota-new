-- ============================================================================
-- firmware_versions 新增 file_name 字段（原始文件名）
-- ============================================================================
-- 目标：
-- 1) 新增 file_name VARCHAR(255) 可空字段
-- 2) 添加字段注释
-- 3) 增加一致性约束：package_status='READY' 时 file_name 必须有值
-- 4) 历史数据回填（无法还原原始文件名时，回填 objectKey 的末段作为兜底）
-- ============================================================================

-- 1. 新增字段（可空）
ALTER TABLE firmware_versions
    ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);

-- 2. 字段注释
COMMENT ON COLUMN firmware_versions.file_name IS
    '固件原始文件名（上传时文件名，package_status=READY 时应有值）';
