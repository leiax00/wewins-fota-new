-- V1__init_core.sql
-- FOTA 系统核心表结构初始化脚本
-- 创建日期: 2026-02-05
-- 作者: FOTA 团队
--
-- 表列表:
-- 1. products - 产品表
-- 2. devices - 设备表
-- 3. firmware_versions - 固件版本表
-- 4. upgrade_policies - 升级策略表

-- ============================================================================
-- 1. products (产品表)
-- ============================================================================
-- 存储产品的基本信息，每个产品代表一类设备
CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(255),
    model VARCHAR(255),
    remark TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP
);

COMMENT ON TABLE products IS '产品表：存储设备产品的基本信息';
COMMENT ON COLUMN products.id IS '产品唯一标识';
COMMENT ON COLUMN products.name IS '产品名称';
COMMENT ON COLUMN products.manufacturer IS '制造商';
COMMENT ON COLUMN products.model IS '产品型号';
COMMENT ON COLUMN products.remark IS '产品备注';
COMMENT ON COLUMN products.created_at IS '创建时间';
COMMENT ON COLUMN products.created_by IS '创建人用户ID';
COMMENT ON COLUMN products.updated_at IS '更新时间';
COMMENT ON COLUMN products.updated_by IS '更新人用户ID';
COMMENT ON COLUMN products.deleted_at IS '软删除时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);
CREATE INDEX IF NOT EXISTS idx_products_manufacturer ON products(manufacturer);
CREATE INDEX IF NOT EXISTS idx_products_deleted_at ON products(deleted_at) WHERE deleted_at IS NULL;

-- ============================================================================
-- 2. devices (设备表)
-- ============================================================================
-- 存储所有设备的基本信息和当前状态
CREATE TABLE IF NOT EXISTS devices (
    id BIGSERIAL PRIMARY KEY,
    imei VARCHAR(255) UNIQUE NOT NULL,
    product_id BIGINT,
    current_version_id BIGINT,
    status VARCHAR(50) NOT NULL,
    last_seen_at TIMESTAMP,
    tags JSONB,
    import_batch_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP
);

COMMENT ON TABLE devices IS '设备表：存储所有设备的基本信息和当前状态';
COMMENT ON COLUMN devices.id IS '设备唯一标识';
COMMENT ON COLUMN devices.imei IS '设备 IMEI 号（唯一）';
COMMENT ON COLUMN devices.product_id IS '关联的产品 ID（无外键约束，由应用层保证一致性）';
COMMENT ON COLUMN devices.current_version_id IS '当前固件版本 ID';
COMMENT ON COLUMN devices.status IS '设备状态（ACTIVE, INACTIVE, LOST, etc.）';
COMMENT ON COLUMN devices.last_seen_at IS '最后一次在线时间';
COMMENT ON COLUMN devices.tags IS '设备标签（JSONB 对象，KV 结构，如 {"env":"test", "region":"CN", "network":"5G"}）';
COMMENT ON COLUMN devices.import_batch_id IS '导入批次ID';
COMMENT ON COLUMN devices.created_at IS '创建时间';
COMMENT ON COLUMN devices.created_by IS '创建人用户ID';
COMMENT ON COLUMN devices.updated_at IS '更新时间';
COMMENT ON COLUMN devices.updated_by IS '更新人用户ID';
COMMENT ON COLUMN devices.deleted_at IS '软删除时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_devices_imei ON devices(imei);
CREATE INDEX IF NOT EXISTS idx_devices_product_id ON devices(product_id);
CREATE INDEX IF NOT EXISTS idx_devices_current_version_id ON devices(current_version_id);
CREATE INDEX IF NOT EXISTS idx_devices_status ON devices(status);
CREATE INDEX IF NOT EXISTS idx_devices_last_seen_at ON devices(last_seen_at);
CREATE INDEX IF NOT EXISTS idx_devices_tags_gin ON devices USING GIN (tags);
CREATE INDEX IF NOT EXISTS idx_devices_import_batch_id ON devices(import_batch_id);
CREATE INDEX IF NOT EXISTS idx_devices_deleted_at ON devices(deleted_at) WHERE deleted_at IS NULL;

-- ============================================================================
-- 3. firmware_versions (固件版本表)
-- ============================================================================
-- 存储所有固件版本的信息和文件元数据
CREATE TABLE IF NOT EXISTS firmware_versions (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    version VARCHAR(50) NOT NULL,
    file_url VARCHAR(1024) NOT NULL,
    file_size BIGINT NOT NULL,
    md5 VARCHAR(32) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    tags JSONB,
    meta JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP,
    CONSTRAINT uk_fv_product_version UNIQUE (product_id, version)
);

COMMENT ON TABLE firmware_versions IS '固件版本表：存储所有固件版本的信息和文件元数据';
COMMENT ON COLUMN firmware_versions.id IS '固件版本唯一标识';
COMMENT ON COLUMN firmware_versions.product_id IS '关联的产品 ID（无外键约束，由应用层保证一致性）';
COMMENT ON COLUMN firmware_versions.version IS '版本号（如 1.0.0）';
COMMENT ON COLUMN firmware_versions.file_url IS '固件文件下载地址';
COMMENT ON COLUMN firmware_versions.file_size IS '固件文件大小（字节）';
COMMENT ON COLUMN firmware_versions.md5 IS 'MD5 校验和';
COMMENT ON COLUMN firmware_versions.sha256 IS 'SHA-256 校验和';
COMMENT ON COLUMN firmware_versions.tags IS '版本标签（JSONB 对象，KV 结构，如 {"tag":"build01"}）';
COMMENT ON COLUMN firmware_versions.meta IS '扩展元数据（多语言描述、changelog、扩展字段）';
COMMENT ON COLUMN firmware_versions.created_at IS '创建时间';
COMMENT ON COLUMN firmware_versions.created_by IS '创建人用户ID';
COMMENT ON COLUMN firmware_versions.updated_at IS '更新时间';
COMMENT ON COLUMN firmware_versions.updated_by IS '更新人用户ID';
COMMENT ON COLUMN firmware_versions.deleted_at IS '软删除时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_fv_product_id ON firmware_versions(product_id);
-- uk_fv_product_version 索引由 UNIQUE 约束自动创建，无需显式创建
CREATE INDEX IF NOT EXISTS idx_fv_tags_gin ON firmware_versions USING GIN (tags);
CREATE INDEX IF NOT EXISTS idx_fv_meta_gin ON firmware_versions USING GIN (meta);
CREATE INDEX IF NOT EXISTS idx_fv_deleted_at ON firmware_versions(deleted_at) WHERE deleted_at IS NULL;

-- ============================================================================
-- 4. upgrade_policies (升级策略表)
-- ============================================================================
-- 存储固件升级策略，包括灰度发布、时间窗口、设备筛选等配置
CREATE TABLE IF NOT EXISTS upgrade_policies (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    remark TEXT,
    target_version_id BIGINT NOT NULL,
    source_versions JSONB,
    priority INTEGER DEFAULT 0,
    gray_rate INTEGER DEFAULT 0,
    trigger_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    target_device_ids JSONB,
    target_device_tags JSONB,
    time_window JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP
);

COMMENT ON TABLE upgrade_policies IS '升级策略表：存储固件升级策略和配置';
COMMENT ON COLUMN upgrade_policies.id IS '策略唯一标识';
COMMENT ON COLUMN upgrade_policies.product_id IS '关联的产品 ID（无外键约束，由应用层保证一致性）';
COMMENT ON COLUMN upgrade_policies.name IS '策略名称';
COMMENT ON COLUMN upgrade_policies.remark IS '策略备注';
COMMENT ON COLUMN upgrade_policies.target_version_id IS '目标固件版本 ID（无外键约束，由应用层保证一致性）';
COMMENT ON COLUMN upgrade_policies.source_versions IS '允许升级的源版本列表（JSONB 数组，如 ["1.0.0", "1.1.0"]）';
COMMENT ON COLUMN upgrade_policies.priority IS '优先级（数值越大优先级越高）';
COMMENT ON COLUMN upgrade_policies.gray_rate IS '灰度比例（0-100）';
COMMENT ON COLUMN upgrade_policies.trigger_mode IS '触发模式（AUTO/MANUAL）';
COMMENT ON COLUMN upgrade_policies.target_device_ids IS '指定设备ID列表（JSONB 数组）';
COMMENT ON COLUMN upgrade_policies.target_device_tags IS '设备标签过滤条件（JSONB）';
COMMENT ON COLUMN upgrade_policies.time_window IS '时间窗口配置（JSONB）';
COMMENT ON COLUMN upgrade_policies.created_at IS '创建时间';
COMMENT ON COLUMN upgrade_policies.created_by IS '创建人用户ID';
COMMENT ON COLUMN upgrade_policies.updated_at IS '更新时间';
COMMENT ON COLUMN upgrade_policies.updated_by IS '更新人用户ID';
COMMENT ON COLUMN upgrade_policies.deleted_at IS '软删除时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_up_product_id ON upgrade_policies(product_id);
CREATE INDEX IF NOT EXISTS idx_up_target_version ON upgrade_policies(target_version_id);
CREATE INDEX IF NOT EXISTS idx_up_priority ON upgrade_policies(priority);
CREATE INDEX IF NOT EXISTS idx_up_trigger_mode ON upgrade_policies(trigger_mode);
CREATE INDEX IF NOT EXISTS idx_up_deleted_at ON upgrade_policies(deleted_at) WHERE deleted_at IS NULL;

-- JSONB 过滤索引（用于高效查询）
CREATE INDEX IF NOT EXISTS idx_up_source_versions_gin ON upgrade_policies USING GIN (source_versions);
CREATE INDEX IF NOT EXISTS idx_up_target_device_ids_gin ON upgrade_policies USING GIN (target_device_ids);
CREATE INDEX IF NOT EXISTS idx_up_target_device_tags_gin ON upgrade_policies USING GIN (target_device_tags);

-- ============================================================================
-- 5. device_import_batches (设备导入批次表)
-- ============================================================================
-- 存储设备导入批次信息，支持批次状态管理和统计
CREATE TABLE IF NOT EXISTS device_import_batches (
    id BIGSERIAL PRIMARY KEY,
    batch_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'IMPORTING',
    source_file VARCHAR(1024),
    total_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    failed_count INTEGER DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP
);

COMMENT ON TABLE device_import_batches IS '设备导入批次表：支持批次状态管理和导入统计';
COMMENT ON COLUMN device_import_batches.id IS '批次唯一标识';
COMMENT ON COLUMN device_import_batches.batch_name IS '批次名称（用户自定义或自动生成）';
COMMENT ON COLUMN device_import_batches.status IS '批次状态（IMPORTING/SUCCESS/FAILED/PARTIAL）';
COMMENT ON COLUMN device_import_batches.source_file IS '导入文件路径或标识';
COMMENT ON COLUMN device_import_batches.total_count IS '导入总数量';
COMMENT ON COLUMN device_import_batches.success_count IS '成功数量';
COMMENT ON COLUMN device_import_batches.failed_count IS '失败数量';
COMMENT ON COLUMN device_import_batches.error_message IS '失败原因';
COMMENT ON COLUMN device_import_batches.started_at IS '开始导入时间';
COMMENT ON COLUMN device_import_batches.finished_at IS '结束导入时间';
COMMENT ON COLUMN device_import_batches.created_at IS '创建时间';
COMMENT ON COLUMN device_import_batches.created_by IS '创建人用户ID';
COMMENT ON COLUMN device_import_batches.updated_at IS '更新时间';
COMMENT ON COLUMN device_import_batches.updated_by IS '更新人用户ID';
COMMENT ON COLUMN device_import_batches.deleted_at IS '软删除时间';

-- 创建索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_device_import_batches_name ON device_import_batches(batch_name) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_device_import_batches_status ON device_import_batches(status);
CREATE INDEX IF NOT EXISTS idx_device_import_batches_deleted_at ON device_import_batches(deleted_at) WHERE deleted_at IS NULL;

-- ============================================================================
-- 初始化完成
-- ============================================================================
-- 记录创建的表和索引数量
DO $$
BEGIN
    RAISE NOTICE 'FOTA 核心表结构初始化完成';
    RAISE NOTICE '已创建 5 个核心表：products, devices, firmware_versions, upgrade_policies, device_import_batches';
    RAISE NOTICE '已创建所有必要的索引和外键约束';
END $$;
