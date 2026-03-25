package com.wewins.fota.infra.persistence.mybatis.writer;

import org.springframework.jdbc.core.JdbcTemplate;

public class MySqlDeviceVersionPartBatchWriter extends AbstractDeviceVersionPartBatchWriter {

    public MySqlDeviceVersionPartBatchWriter(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    protected String currentVersionSql() {
        return """
                INSERT INTO device_version_parts
                (device_id, part_name, version_id, version, internal_version, is_primary, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    version_id = VALUES(version_id),
                    version = VALUES(version),
                    internal_version = VALUES(internal_version),
                    is_primary = VALUES(is_primary),
                    updated_at = VALUES(updated_at)
                """;
    }

    @Override
    protected String initialVersionSql() {
        return """
                INSERT INTO device_initial_version_parts
                (device_id, part_name, version_id, version, internal_version, is_primary, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    device_id = device_id
                """;
    }
}
