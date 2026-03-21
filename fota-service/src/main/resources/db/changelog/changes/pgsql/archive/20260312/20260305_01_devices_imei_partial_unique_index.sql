DROP INDEX IF EXISTS idx_devices_imei;

CREATE UNIQUE INDEX IF NOT EXISTS uk_devices_imei_active
    ON devices (imei)
    WHERE deleted_at IS NULL;

COMMENT ON INDEX uk_devices_imei_active IS
    'devices 活跃记录 IMEI 唯一索引（deleted_at IS NULL）';
