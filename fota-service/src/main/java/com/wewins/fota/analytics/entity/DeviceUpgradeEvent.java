package com.wewins.fota.analytics.entity;

import com.wewins.fota.analytics.enums.DeviceUpgradeEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 设备升级事件实体
 * <p>
 * 对应 ClickHouse 表：device_upgrade_events
 * 记录设备上报的升级进度和结果事件（从 /v1/upgrade/report 接口）
 * </p>
 * <p>
 * 设计原则：
 * <ul>
 *   <li>event_time 使用服务端时间（权威），设备上报时间放在 details JSON 中</li>
 *   <li>device_id, product_id, firmware_version 从 Redis 缓存补全（可为空）</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceUpgradeEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件唯一标识（UUID）
     * <p>
     * 用于幂等写入，防止重复插入
     * </p>
     */
    private UUID eventId;

    /**
     * 设备 IMEI
     * <p>
     * 从上报中获取，使用 String 类型存储，防止前导 0 丢失
     * </p>
     */
    private String imei;

    /**
     * 请求唯一标识（UUID）
     * <p>
     * 从下载 URL 中解析，关联 device_check_logs.request_id
     * 可能为空（如果 URL 中没有 rid 参数）
     * </p>
     */
    private UUID requestId;

    /**
     * 策略 ID
     * <p>
     * 从下载 URL 中解析（pid 参数）
     * </p>
     */
    private Long policyId;

    /**
     * 事件类型
     * <p>
     * Enum8 类型：DL_START, DL_OK, DL_FAIL, UP_OK, UP_FAIL
     * </p>
     */
    private DeviceUpgradeEventType eventType;

    /**
     * 下载 URL
     * <p>
     * 设备上报的下载 URL（包含 pid 和 rid 参数）
     * </p>
     */
    private String downloadUrl;

    /**
     * 原始上报详情（JSON）
     * <p>
     * 包含设备上报时间、进度、错误信息等原始数据
     * 示例：{"device_time": "2026-02-11T10:00:00Z", "progress": 50, "error_code": "E001"}
     * </p>
     */
    private String details;

    /**
     * 事件时间（服务端时间）
     * <p>
     * DateTime64(3, 'UTC') 毫秒精度
     * 使用服务端接收时间，作为权威时间线用于排序和 SLA 统计
     * </p>
     */
    private LocalDateTime eventTime;

    // ========== 以下字段从 Redis 缓存补全 ==========

    /**
     * 设备 ID
     * <p>
     * 从 Redis 缓存补全，键为 "fota:device:{imei}"
     * 如果缓存未命中则为 null
     * </p>
     */
    private Long deviceId;

    /**
     * 产品 ID
     * <p>
     * 从 Redis 缓存补全
     * </p>
     */
    private Long productId;

    /**
     * 固件版本
     * <p>
     * 从 Redis 缓存补全，记录事件发生时的固件版本快照
     * </p>
     */
    private String firmwareVersion;

    // ========== 元数据 ==========

    /**
     * 客户端 IP 地址
     * <p>
     * IPv4 类型，从请求中提取
     * </p>
     */
    private String clientIp;

    /**
     * 区域标识
     * <p>
     * LowCardinality(String) 类型
     * 支持跨区域统计和过滤
     * </p>
     */
    private String region;
}
