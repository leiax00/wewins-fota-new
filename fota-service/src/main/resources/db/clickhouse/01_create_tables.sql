-- ================================================================================
-- ClickHouse 表结构 DDL
-- ================================================================================
-- 用途：存储设备升级相关的日志数据，支持实时查询和统计分析
-- 执行方式：手动连接 ClickHouse 执行此脚本
-- ================================================================================

-- 设置数据库（可选，根据实际环境调整）
-- CREATE DATABASE IF NOT EXISTS fota;
-- USE fota;

-- ================================================================================
-- 设备检查日志表
-- ================================================================================
-- 用途：记录设备调用 /v1/upgrade/check 接口的日志
CREATE TABLE IF NOT EXISTS device_check_logs
(
    -- 时间和分区字段
    event_time DateTime64(3, 'UTC'),
    date Date MATERIALIZED toDate(event_time),
    month MATERIALIZED toYYYYMM(event_time),

    -- 设备信息
    device_id UInt64,
    imei String,
    product_id UInt64,

    -- 检查参数
    version String,
    internal_version String,
    check_mode LowCardinality(String),
    language LowCardinality(String),
    ext_tags String,
    is_dev UInt8,

    -- 检查结果
    check_rst Nullable(String),
    target_version Nullable(String),
    target_version_id Nullable(UInt64),
    policy_id Nullable(UInt64),
    gray_bucket Nullable(UInt8),
    is_gray_hit Nullable(UInt8),

    -- 下发控制参数
    response_check_interval Nullable(UInt32),
    download_delay Nullable(UInt32),

    -- 检查请求唯一标识（NOT NULL，用于幂等写入和关联 upgrade events）
    request_id String,

    -- 请求元数据
    client_ip Nullable(IPv4),
    user_agent Nullable(String),

    -- 区域标识（支持跨区域统计）
    region LowCardinality(String),

    -- 错误信息
    error_code Nullable(String),
    error_message Nullable(String)
)
ENGINE = MergeTree()
PARTITION BY month
ORDER BY (product_id, device_id, event_time)
TTL event_time + toIntervalDay(30)
SETTINGS index_granularity = 8192;

-- ================================================================================
-- 设备升级事件表
-- ================================================================================
-- 用途：记录设备上报的升级进度和结果事件（从 /v1/upgrade/report 接口）
-- 设计原则：
--   1. event_time 使用服务端时间（权威），设备上报时间放在 details JSON 中
--   2. device_id, product_id, firmware_version 从 Redis 缓存补全（可为空）
CREATE TABLE IF NOT EXISTS device_upgrade_events
(
    -- 时间字段（服务端时间，权威）
    event_time DateTime64(3, 'UTC'),
    date Date MATERIALIZED toDate(event_time),
    month MATERIALIZED toYYYYMM(event_time),

    -- 事件唯一标识（用于幂等写入）
    event_id UUID,

    -- 设备标识（从上报获取）
    imei String,

    -- 关联字段（从 URL 解析，String 类型支持无中划线 UUID）
    request_id Nullable(String),
    policy_id Nullable(UInt64),

    -- 事件信息（从上报获取）
    event_type Enum8('DL_START' = 1, 'DL_OK' = 2, 'DL_FAIL' = 3, 'UP_OK' = 4, 'UP_FAIL' = 5),
    download_url String,

    -- 原始上报详情（JSON，含设备上报时间、进度、错误信息等）
    details String,

    -- 冗余字段（从 Redis 缓存补全，可为空）
    device_id Nullable(UInt64),
    product_id Nullable(UInt64),
    firmware_version Nullable(String),

    -- 请求元数据
    client_ip Nullable(IPv4),

    -- 区域标识（支持跨区域统计）
    region LowCardinality(String)
)
ENGINE = MergeTree()
PARTITION BY month
ORDER BY (event_time)
TTL event_time + toIntervalDay(30)
SETTINGS index_granularity = 8192;

-- ================================================================================
-- 验证表结构
-- ================================================================================
-- 查看表结构
-- DESCRIBE device_check_logs;
-- DESCRIBE device_upgrade_events;

-- 查看分区信息
-- SELECT partition, name, rows, bytes_on_disk
-- FROM system.parts
-- WHERE table IN ('device_check_logs', 'device_upgrade_events')
--   AND active;
