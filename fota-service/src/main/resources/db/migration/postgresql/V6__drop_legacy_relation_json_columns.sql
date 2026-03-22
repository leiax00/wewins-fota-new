DROP INDEX IF EXISTS idx_devices_tags_gin;
DROP INDEX IF EXISTS idx_devices_version_parts_gin;
DROP INDEX IF EXISTS idx_devices_initial_version_parts_gin;
DROP INDEX IF EXISTS idx_fv_tags_gin;
DROP INDEX IF EXISTS idx_up_source_versions_gin;
DROP INDEX IF EXISTS idx_up_target_device_tags_gin;
DROP INDEX IF EXISTS idx_up_target_device_batch_ids_gin;
DROP INDEX IF EXISTS idx_up_target_imeis_gin;

ALTER TABLE devices
    DROP COLUMN IF EXISTS tags,
    DROP COLUMN IF EXISTS version_parts,
    DROP COLUMN IF EXISTS initial_version_parts;

ALTER TABLE firmware_versions
    DROP COLUMN IF EXISTS tags;

ALTER TABLE upgrade_policies
    DROP COLUMN IF EXISTS source_versions,
    DROP COLUMN IF EXISTS target_imeis,
    DROP COLUMN IF EXISTS target_device_batch_ids,
    DROP COLUMN IF EXISTS target_device_tags;
