-- 移除设备软删除字段
-- 设备不需要软删除，直接硬删除即可

-- 删除软删除索引
DROP INDEX IF EXISTS idx_devices_deleted_at;

-- 删除软删除字段
ALTER TABLE devices DROP COLUMN IF EXISTS deleted_at;
