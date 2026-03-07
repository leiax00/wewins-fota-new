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

    -- 检查请求唯一标识（NOT NULL，用于幂等写入和关联 upgrade events）
    request_id String,

    -- 设备信息
    device_id UInt64,
    imei String,
    product_id UInt64,

    -- 检查参数
    version String,
    internal_version String,
    check_mode Enum8('MANUAL' = 0, 'AUTO' = 1),
    language LowCardinality(String),
    ext_tags String,
    is_dev UInt8,

    -- 检查结果
    check_rst Nullable(Enum8('UPDATE' = 0, 'NO_UPDATE' = 1, 'RATE_LIMITED' = 2, 'DEVICE_NOT_FOUND' = 3, 'ERROR' = 4)),
    target_version_id Nullable(UInt64),
    target_version Nullable(String),
    target_internal_version Nullable(String),
    policy_id Nullable(UInt64),
    download_url Nullable(String),
    gray_bucket Nullable(UInt8),
    is_gray_hit Nullable(UInt8),

    -- 下发控制参数
    response_check_interval Nullable(UInt32),
    download_delay Nullable(UInt32),

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
--   2. 通过 request_id（链路追踪 ID）关联 device_check_logs 获取设备和策略信息
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

    -- 链路追踪 ID（设备从 Check 响应获取并上报）
    request_id Nullable(String),

    -- 事件信息（从上报获取）
    event_type Enum8('DL_START' = 0, 'DL_OK' = 1, 'DL_FAIL' = 2, 'UP_OK' = 3, 'UP_FAIL' = 4),

    -- 原始上报详情（JSON，含设备上报时间、进度、错误信息等）
    details String,

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
