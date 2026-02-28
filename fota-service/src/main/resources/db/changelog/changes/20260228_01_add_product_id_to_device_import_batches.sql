-- 为设备导入批次表添加 product_id 字段
-- 用途：追溯批次属于哪个产品，支持按产品筛选批次
-- 创建日期: 2026-02-28

-- 1. 添加 product_id 字段
ALTER TABLE device_import_batches
ADD COLUMN IF NOT EXISTS product_id BIGINT;

-- 2. 添加字段注释
COMMENT ON COLUMN device_import_batches.product_id IS '关联的产品ID（导入时指定的产品）';

-- 3. 创建索引（支持按产品筛选批次）
CREATE INDEX IF NOT EXISTS idx_device_import_batches_product_id
ON device_import_batches(product_id);

-- 4. 数据迁移: 从 devices 表反推 productId
-- 由于批次是导入快照，我们从属于该批次的设备中获取产品ID
-- 注意：如果同一批次包含多个产品（理论上不应该发生），取第一个
-- 限制：如果某个批次下所有设备都被删除了，该批次的 product_id 将保持为 NULL
UPDATE device_import_batches dib
SET product_id = (
    SELECT d.product_id
    FROM devices d
    WHERE d.import_batch_id = dib.id
      AND d.deleted_at IS NULL
    LIMIT 1
)
WHERE dib.product_id IS NULL;
