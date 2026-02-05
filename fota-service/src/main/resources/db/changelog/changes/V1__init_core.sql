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
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE products IS '产品表：存储设备产品的基本信息';
COMMENT ON COLUMN products.id IS '产品唯一标识';
COMMENT ON COLUMN products.name IS '产品名称';
COMMENT ON COLUMN products.manufacturer IS '制造商';
COMMENT ON COLUMN products.model IS '产品型号';
COMMENT ON COLUMN products.description IS '产品描述';
COMMENT ON COLUMN products.created_at IS '创建时间';
COMMENT ON COLUMN products.updated_at IS '更新时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);
CREATE INDEX IF NOT EXISTS idx_products_manufacturer ON products(manufacturer);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_devices_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL
);

COMMENT ON TABLE devices IS '设备表：存储所有设备的基本信息和当前状态';
COMMENT ON COLUMN devices.id IS '设备唯一标识';
COMMENT ON COLUMN devices.imei IS '设备 IMEI 号（唯一）';
COMMENT ON COLUMN devices.product_id IS '关联的产品 ID';
COMMENT ON COLUMN devices.current_version_id IS '当前固件版本 ID';
COMMENT ON COLUMN devices.status IS '设备状态（ACTIVE, INACTIVE, LOST, etc.）';
COMMENT ON COLUMN devices.last_seen_at IS '最后一次在线时间';
COMMENT ON COLUMN devices.created_at IS '创建时间';
COMMENT ON COLUMN devices.updated_at IS '更新时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_devices_imei ON devices(imei);
CREATE INDEX IF NOT EXISTS idx_devices_product_id ON devices(product_id);
CREATE INDEX IF NOT EXISTS idx_devices_status ON devices(status);
CREATE INDEX IF NOT EXISTS idx_devices_last_seen_at ON devices(last_seen_at);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fv_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT uk_fv_product_version UNIQUE (product_id, version)
);

COMMENT ON TABLE firmware_versions IS '固件版本表：存储所有固件版本的信息和文件元数据';
COMMENT ON COLUMN firmware_versions.id IS '固件版本唯一标识';
COMMENT ON COLUMN firmware_versions.product_id IS '关联的产品 ID';
COMMENT ON COLUMN firmware_versions.version IS '版本号（如 1.0.0）';
COMMENT ON COLUMN firmware_versions.file_url IS '固件文件下载地址';
COMMENT ON COLUMN firmware_versions.file_size IS '固件文件大小（字节）';
COMMENT ON COLUMN firmware_versions.md5 IS 'MD5 校验和';
COMMENT ON COLUMN firmware_versions.sha256 IS 'SHA-256 校验和';
COMMENT ON COLUMN firmware_versions.created_at IS '创建时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_fv_product_id ON firmware_versions(product_id);
CREATE UNIQUE INDEX uk_fv_product_version ON firmware_versions(product_id, version);

-- ============================================================================
-- 4. upgrade_policies (升级策略表)
-- ============================================================================
-- 存储固件升级策略，包括灰度发布、时间窗口等配置
CREATE TABLE IF NOT EXISTS upgrade_policies (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    target_version_id BIGINT NOT NULL,
    priority INTEGER DEFAULT 0,
    gray_rate INTEGER DEFAULT 0,
    time_window JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_up_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_up_target_version FOREIGN KEY (target_version_id) REFERENCES firmware_versions(id) ON DELETE CASCADE
);

COMMENT ON TABLE upgrade_policies IS '升级策略表：存储固件升级策略和配置';
COMMENT ON COLUMN upgrade_policies.id IS '策略唯一标识';
COMMENT ON COLUMN upgrade_policies.product_id IS '关联的产品 ID';
COMMENT ON COLUMN upgrade_policies.name IS '策略名称';
COMMENT ON COLUMN upgrade_policies.description IS '策略描述';
COMMENT ON COLUMN upgrade_policies.target_version_id IS '目标固件版本 ID';
COMMENT ON COLUMN upgrade_policies.priority IS '优先级（数值越大优先级越高）';
COMMENT ON COLUMN upgrade_policies.gray_rate IS '灰度比例（0-100）';
COMMENT ON COLUMN upgrade_policies.time_window IS '时间窗口配置（JSONB 格式）';
COMMENT ON COLUMN upgrade_policies.created_at IS '创建时间';
COMMENT ON COLUMN upgrade_policies.updated_at IS '更新时间';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_up_product_id ON upgrade_policies(product_id);
CREATE INDEX IF NOT EXISTS idx_up_priority ON upgrade_policies(priority);
CREATE INDEX IF NOT EXISTS idx_up_target_version ON upgrade_policies(target_version_id);

-- ============================================================================
-- 初始化完成
-- ============================================================================
-- 记录创建的表和索引数量
DO $$
BEGIN
    RAISE NOTICE 'FOTA 核心表结构初始化完成';
    RAISE NOTICE '已创建 4 个核心表：products, devices, firmware_versions, upgrade_policies';
    RAISE NOTICE '已创建所有必要的索引和外键约束';
END $$;
