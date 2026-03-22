ALTER TABLE device_tags
    ADD INDEX idx_device_tags_kv_device (tag_key, tag_value, device_id),
    ADD INDEX idx_device_tags_device_id (device_id);

ALTER TABLE device_version_parts
    ADD INDEX idx_dvp_version_lookup (version_id),
    ADD INDEX idx_dvp_part_version_lookup (part_name, version_id),
    ADD INDEX idx_dvp_device_primary (device_id, is_primary);

CREATE TABLE device_initial_version_parts
(
    id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id        BIGINT      NOT NULL,
    part_name        VARCHAR(64) NOT NULL,
    version_id       BIGINT,
    version          VARCHAR(50) NOT NULL,
    internal_version VARCHAR(255),
    is_primary       TINYINT(1)  NOT NULL DEFAULT 0,
    recorded_at      DATETIME             DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_divp_device_part (device_id, part_name),
    INDEX idx_divp_device_id (device_id),
    INDEX idx_divp_version_id (version_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='设备首次版本分片关联表';

CREATE TABLE firmware_version_tags
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    firmware_version_id BIGINT       NOT NULL,
    tag_key             VARCHAR(64)  NOT NULL,
    tag_value           VARCHAR(255) NOT NULL,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fvt_version_key (firmware_version_id, tag_key),
    INDEX idx_fvt_kv_version (tag_key, tag_value, firmware_version_id),
    INDEX idx_fvt_version_id (firmware_version_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='固件标签关联表';

CREATE TABLE upgrade_policy_source_versions
(
    policy_id          BIGINT   NOT NULL,
    source_version_id  BIGINT   NOT NULL,
    created_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (policy_id, source_version_id),
    INDEX idx_upsv_source_version_policy (source_version_id, policy_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='策略源版本关联表';

CREATE TABLE upgrade_policy_target_devices
(
    policy_id   BIGINT      NOT NULL,
    imei        VARCHAR(64) NOT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (policy_id, imei),
    INDEX idx_uptd_imei_policy (imei, policy_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='策略目标设备关联表';

CREATE TABLE upgrade_policy_target_batches
(
    policy_id   BIGINT   NOT NULL,
    batch_id    BIGINT   NOT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (policy_id, batch_id),
    INDEX idx_uptb_batch_policy (batch_id, policy_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='策略目标批次关联表';

CREATE TABLE upgrade_policy_target_tags
(
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    policy_id  BIGINT       NOT NULL,
    tag_key    VARCHAR(64)  NOT NULL,
    tag_value  VARCHAR(255) NOT NULL,
    operator   VARCHAR(16)  NOT NULL DEFAULT 'EQ',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_uptt_policy_key (policy_id, tag_key),
    INDEX idx_uptt_kv_policy (tag_key, tag_value, policy_id),
    INDEX idx_uptt_policy_id (policy_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='策略目标标签关联表';
