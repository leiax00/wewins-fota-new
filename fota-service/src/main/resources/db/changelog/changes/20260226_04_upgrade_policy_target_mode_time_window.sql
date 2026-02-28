-- ============================================================================
-- 升级策略字段增强：新增 target_mode 和 target_device_batch_ids
-- ============================================================================
--
-- 字段历史：
-- - 20260209_01_init_core.sql: 创建基础表，包含 priority、trigger_mode、source_versions、
--   target_device_ids、target_device_tags、time_window
-- - 本迁移：新增 target_mode、target_device_batch_ids
--
-- ============================================================================

-- 1) 新增字段（幂等）
ALTER TABLE upgrade_policies
    ADD COLUMN IF NOT EXISTS target_mode VARCHAR(32) NOT NULL DEFAULT 'ALL';

ALTER TABLE upgrade_policies
    ADD COLUMN IF NOT EXISTS target_device_batch_ids JSONB;

-- 2) 存量数据迁移：根据现有的 target_device_ids 和 target_device_tags 推断 target_mode
UPDATE upgrade_policies
SET target_mode = CASE
    WHEN target_device_ids IS NOT NULL
        AND jsonb_typeof(target_device_ids) = 'array'
        AND jsonb_array_length(target_device_ids) > 0 THEN 'DEVICE_IDS'
    WHEN target_device_tags IS NOT NULL
        AND jsonb_typeof(target_device_tags) = 'object'
        AND target_device_tags <> '{}'::jsonb THEN 'DEVICE_TAGS'
    ELSE 'ALL'
END
WHERE target_mode = 'ALL';  -- 仅处理默认值，避免覆盖已设置的数据

-- 3) 更新 trigger_mode 默认值为 BOTH（不限制触发方式）
-- 注：PostgreSQL 的 SET DEFAULT 操作本身是幂等的，重复执行不会报错
ALTER TABLE upgrade_policies
    ALTER COLUMN trigger_mode SET DEFAULT 'BOTH';

-- 4) 添加/更新字段注释
COMMENT ON COLUMN upgrade_policies.trigger_mode IS '触发模式（AUTO:仅自动 / MANUAL:仅手动 / BOTH:不限制）';
COMMENT ON COLUMN upgrade_policies.target_mode IS '目标设备模式（ALL/DEVICE_IDS/DEVICE_BATCHES/DEVICE_TAGS）';
COMMENT ON COLUMN upgrade_policies.target_device_batch_ids IS '目标设备批次ID列表（JSONB数组）';

-- 5) 创建新索引
CREATE INDEX IF NOT EXISTS idx_up_target_mode ON upgrade_policies(target_mode);
CREATE INDEX IF NOT EXISTS idx_up_target_device_batch_ids_gin ON upgrade_policies USING GIN (target_device_batch_ids);
CREATE INDEX IF NOT EXISTS idx_up_time_window_gin ON upgrade_policies USING GIN (time_window);