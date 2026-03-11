ALTER TABLE products
    ADD COLUMN IF NOT EXISTS check_period_seconds INTEGER NOT NULL DEFAULT 21600;

COMMENT ON COLUMN products.check_period_seconds IS '产品默认检测周期（秒），默认 21600 秒（6 小时）';
