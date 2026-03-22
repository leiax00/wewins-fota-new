ALTER TABLE devices
    DROP COLUMN tags,
    DROP COLUMN version_parts,
    DROP COLUMN initial_version_parts;

ALTER TABLE firmware_versions
    DROP COLUMN tags;

ALTER TABLE upgrade_policies
    DROP COLUMN source_versions,
    DROP COLUMN target_imeis,
    DROP COLUMN target_device_batch_ids,
    DROP COLUMN target_device_tags;
