package com.wewins.fota.domain.reporting.model.aggregate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备检查日志实体
 * <p>
 * 对应 ClickHouse 表：device_check_logs
 * 记录设备调用 /v1/upgrade/check 接口的日志
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCheckLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 请求唯一标识（UUID 字符串）
     * <p>
     * 用于关联设备检查和后续的升级事件
     * 使用 String 类型存储 UUID，避免 MyBatis 类型处理器问题
     * </p>
     */
    private String requestId;

    /**
     * 设备 ID
     */
    private Long deviceId;

    /**
     * 设备 IMEI
     * <p>
     * 使用 String 类型存储，防止前导 0 丢失
     * </p>
     */
    private String imei;

    /**
     * 产品 ID
     */
    private Long productId;

    /**
     * 策略 ID
     * <p>
     * 如果匹配到升级策略，则记录策略 ID
     * </p>
     */
    private Long policyId;

    /**
     * 当前固件版本
     */
    private String version;

    /**
     * 设备内部版本号（build tag）
     */
    private String internalVersion;

    /**
     * 目标固件版本
     * <p>
     * 如果有更新，记录目标版本号
     * </p>
     */
    private String targetVersion;

    /**
     * 目标版本 ID
     */
    private Long targetVersionId;

    /**
     * 检查结果
     * <p>
     * 结果值：UPDATE（有更新）、NO_UPDATE（无更新）、RATE_LIMITED（限流）、DEVICE_NOT_FOUND（设备不存在）、ERROR（错误）
     * </p>
     */
    private String checkRst;

    /**
     * 检查模式
     * <p>
     * auto（自动检查）、manual（手动检查）
     * </p>
     */
    private String checkMode;

    /**
     * 语言设置
     */
    private String language;

    /**
     * 扩展标签（JSON）
     * <p>
     * 存储扩展的标签信息
     * </p>
     */
    private String extTags;

    /**
     * 是否为开发设备
     * <p>
     * 0：普通设备，1：开发设备
     * </p>
     */
    private Integer isDev;

    /**
     * 灰度桶号
     * <p>
     * 范围：0-99
     * </p>
     */
    private Integer grayBucket;

    /**
     * 是否命中灰度
     * <p>
     * 0：未命中，1：命中
     * </p>
     */
    private Integer isGrayHit;

    /**
     * 下发检查间隔（秒）
     * <p>
     * 建议设备下次检查的时间间隔
     * </p>
     */
    private Integer responseCheckInterval;

    /**
     * 下载延迟（秒）
     * <p>
     * 建议设备延迟下载的时间
     * </p>
     */
    private Integer downloadDelay;

    /**
     * 事件时间（设备上报时间或服务器时间）
     * <p>
     * DateTime64(3, 'UTC') 毫秒精度
     * </p>
     */
    private LocalDateTime eventTime;

    /**
     * 客户端 IP 地址
     * <p>
     * IPv4 类型
     * </p>
     */
    private String clientIp;

    /**
     * 用户代理
     * <p>
     * 设备上报的 User-Agent 信息
     * </p>
     */
    private String userAgent;

    /**
     * 区域标识
     * <p>
     * LowCardinality(String) 类型
     * 支持跨区域统计和过滤
     * </p>
     */
    private String region;

    /**
     * 错误码
     * <p>
     * 检查过程中出现的错误码
     * </p>
    */
    private String errorCode;

    /**
     * 错误信息
     * <p>
     * 检查过程中出现的错误描述
     * </p>
     */
    private String errorMessage;
}
