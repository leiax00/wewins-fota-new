-- 修改升级策略表：将 target_device_ids 改为 target_imeis
-- 原因：外部系统无法获取设备ID，但可以通过IMEI来匹配设备
-- 创建日期: 2026-02-27

-- 1. 添加新列 target_imeis
ALTER TABLE upgrade_policies
ADD COLUMN IF NOT EXISTS target_imeis JSONB;

-- 2. 数据迁移：清空使用 DEVICE_IDS 模式的策略数据
-- 由于原字段存储的是设备ID（数字），无法直接转换为IMEI
-- 将这些策略重置为 ALL 模式，需要用户重新配置
UPDATE upgrade_policies
SET target_imeis = '[]'::jsonb,
    target_mode = 'ALL'
WHERE target_mode = 'DEVICE_IDS' AND target_device_ids IS NOT NULL;

-- 3. 更新 target_mode 枚举值注释
-- 新的枚举值含义：DEVICE_IDS 实际存储 IMEI 列表
-- 但为了兼容性，我们保留枚举值名，只改变段名和含义

-- 4. 删除旧列 target_device_ids
ALTER TABLE upgrade_policies
DROP COLUMN IF EXISTS target_device_ids;

-- 5. 删除旧索引
DROP INDEX IF EXISTS idx_up_target_device_ids_gin;

-- 6. 创建新索引
CREATE INDEX IF NOT EXISTS idx_up_target_imeis_gin ON upgrade_policies USING GIN (target_imeis);

-- 7. 更新列注释
COMMENT ON COLUMN upgrade_policies.target_imeis IS '指定设备IMEI列表（JSONB 数组），当 target_mode = DEVICE_IDS 时使用';
COMMENT ON COLUMN upgrade_policies.target_mode IS '目标设备模式（ALL/DEVICE_IDS(实际为IMEI列表)/DEVICE_BATCHES/DEVICE_TAGS）';
