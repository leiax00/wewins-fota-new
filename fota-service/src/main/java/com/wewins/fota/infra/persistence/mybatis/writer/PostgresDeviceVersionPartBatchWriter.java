package com.wewins.fota.infra.persistence.mybatis.writer;

import org.springframework.jdbc.core.JdbcTemplate;

public class PostgresDeviceVersionPartBatchWriter extends AbstractDeviceVersionPartBatchWriter {

    public PostgresDeviceVersionPartBatchWriter(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    protected String currentVersionSql() {
        return """
                INSERT INTO device_version_parts
                (device_id, part_name, version_id, version, internal_version, is_primary, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (device_id, part_name) DO UPDATE
                SET version_id = EXCLUDED.version_id,
                    version = EXCLUDED.version,
                    internal_version = EXCLUDED.internal_version,
                    is_primary = EXCLUDED.is_primary,
                    updated_at = EXCLUDED.updated_at
                """;
    }

    @Override
    protected String initialVersionSql() {
        return """
                INSERT INTO device_initial_version_parts
                (device_id, part_name, version_id, version, internal_version, is_primary, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (device_id, part_name) DO NOTHING
                """;
    }
}
