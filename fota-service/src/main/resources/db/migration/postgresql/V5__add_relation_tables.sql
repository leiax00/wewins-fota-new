CREATE TABLE device_tags
(
    id         BIGSERIAL PRIMARY KEY,
    device_id  BIGINT       NOT NULL,
    tag_key    VARCHAR(64)  NOT NULL,
    tag_value  VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_device_tags_device_key UNIQUE (device_id, tag_key)
);

CREATE INDEX idx_device_tags_kv_device ON device_tags (tag_key, tag_value, device_id);
CREATE INDEX idx_device_tags_device_id ON device_tags (device_id);

CREATE TABLE device_version_parts
(
    id               BIGSERIAL PRIMARY KEY,
    device_id        BIGINT      NOT NULL,
    part_name        VARCHAR(64) NOT NULL,
    version_id       BIGINT,
    version          VARCHAR(50) NOT NULL,
    internal_version VARCHAR(255),
    is_primary       SMALLINT  NOT NULL DEFAULT 0,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_device_version_parts_device_part UNIQUE (device_id, part_name)
);

CREATE INDEX idx_dvp_version_lookup ON device_version_parts (version_id);
CREATE INDEX idx_dvp_part_version_lookup ON device_version_parts (part_name, version_id);
CREATE INDEX idx_dvp_device_primary ON device_version_parts (device_id, is_primary);

CREATE TABLE device_initial_version_parts
(
    id               BIGSERIAL PRIMARY KEY,
    device_id        BIGINT      NOT NULL,
    part_name        VARCHAR(64) NOT NULL,
    version_id       BIGINT,
    version          VARCHAR(50) NOT NULL,
    internal_version VARCHAR(255),
    is_primary       SMALLINT  NOT NULL DEFAULT 0,
    recorded_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_device_initial_version_parts_device_part UNIQUE (device_id, part_name)
);

CREATE INDEX idx_divp_device_id ON device_initial_version_parts (device_id);
CREATE INDEX idx_divp_version_id ON device_initial_version_parts (version_id);

CREATE TABLE firmware_version_tags
(
    id                  BIGSERIAL PRIMARY KEY,
    firmware_version_id BIGINT       NOT NULL,
    tag_key             VARCHAR(64)  NOT NULL,
    tag_value           VARCHAR(255) NOT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_firmware_version_tags_version_key UNIQUE (firmware_version_id, tag_key)
);

CREATE INDEX idx_fvt_kv_version ON firmware_version_tags (tag_key, tag_value, firmware_version_id);
CREATE INDEX idx_fvt_version_id ON firmware_version_tags (firmware_version_id);

CREATE TABLE upgrade_policy_source_versions
(
    policy_id         BIGINT NOT NULL,
    source_version_id BIGINT NOT NULL,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_upgrade_policy_source_versions PRIMARY KEY (policy_id, source_version_id)
);

CREATE INDEX idx_upsv_source_version_policy ON upgrade_policy_source_versions (source_version_id, policy_id);

CREATE TABLE upgrade_policy_target_devices
(
    policy_id   BIGINT      NOT NULL,
    imei        VARCHAR(64) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_upgrade_policy_target_devices PRIMARY KEY (policy_id, imei)
);

CREATE INDEX idx_uptd_imei_policy ON upgrade_policy_target_devices (imei, policy_id);

CREATE TABLE upgrade_policy_target_batches
(
    policy_id   BIGINT NOT NULL,
    batch_id    BIGINT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_upgrade_policy_target_batches PRIMARY KEY (policy_id, batch_id)
);

CREATE INDEX idx_uptb_batch_policy ON upgrade_policy_target_batches (batch_id, policy_id);

CREATE TABLE upgrade_policy_target_tags
(
    id         BIGSERIAL PRIMARY KEY,
    policy_id  BIGINT       NOT NULL,
    tag_key    VARCHAR(64)  NOT NULL,
    tag_value  VARCHAR(255) NOT NULL,
    operator   VARCHAR(16)  NOT NULL DEFAULT 'EQ',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_upgrade_policy_target_tags_policy_key UNIQUE (policy_id, tag_key)
);

CREATE INDEX idx_uptt_kv_policy ON upgrade_policy_target_tags (tag_key, tag_value, policy_id);
CREATE INDEX idx_uptt_policy_id ON upgrade_policy_target_tags (policy_id);
