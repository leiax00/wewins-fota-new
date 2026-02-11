package com.wewins.fota.infra.config;

import lombok.Data;

/**
 * 区域部署配置属性
 * <p>
 * 当 app.mode=region 时使用的配置
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Data
public class RegionProperties {

    /**
     * 区域唯一标识符
     * <p>
     * 格式：小写字母 + 连字符，如 us-east、eu-west
     * </p>
     */
    private String code;

    /**
     * 区域时区
     * <p>
     * 示例：America/New_York、Europe/Paris
     * </p>
     */
    private String timeZone;

    /**
     * 默认配额
     */
    private Long defaultQuota = 100000L;

    /**
     * 区域对外 API 地址
     * <p>
     * 用于生成设备 API 端点
     * </p>
     */
    private String apiBaseUrl;

    /**
     * 配置同步端点
     * <p>
     * 从主区域拉取配置的地址
     * </p>
     */
    private String syncEndpoint;
}
