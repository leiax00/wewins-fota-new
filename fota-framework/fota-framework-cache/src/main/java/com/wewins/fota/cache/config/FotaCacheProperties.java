package com.wewins.fota.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * FOTA 缓存配置属性
 * <p>
 * 集中管理 Redis 缓存相关配置，包括：
 * <ul>
 *   <li>设备活跃度 Bitmap：TTL、保留天数</li>
 *   <li>策略缓存：TTL</li>
 *   <li>限流：默认窗口大小</li>
 * </ul>
 * </p>
 * <p>
 * 配置前缀：{@code app.cache}
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-17
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cache")
public class FotaCacheProperties {

    /**
     * 设备活跃度 Bitmap 的默认 TTL
     * <p>
     * 用于控制每日活跃度 bitmap 的过期时间。
     * 建议保留较长时间（如 90 天），用于离线分析和统计。
     * </p>
     * <p>
     * 默认值：90 天
     * </p>
     */
    private Duration bitmapTtl = Duration.ofDays(90);

    /**
     * Bitmap 保留天数
     * <p>
     * 用于控制离线统计的时间窗口大小。
     * 例如：bitmapKeepDays=7 表示统计最近 7 天的活跃设备。
     * </p>
     * <p>
     * 默认值：90 天
     * </p>
     */
    private int bitmapKeepDays = 90;

    /**
     * 策略缓存默认 TTL
     * <p>
     * 用于控制升级策略缓存的过期时间。
     * 策略可能动态调整，建议设置较短的 TTL（如 1 小时）。
     * </p>
     * <p>
     * 默认值：1 小时
     * </p>
     */
    private Duration policyTtl = Duration.ofHours(1);

    /**
     * 限流默认窗口大小
     * <p>
     * 用于固定窗口限流算法的时间窗口大小。
     * 例如：defaultRateLimitWindow=60s 表示每 60 秒为一个统计窗口。
     * </p>
     * <p>
     * 默认值：60 秒
     * </p>
     */
    private Duration defaultRateLimitWindow = Duration.ofSeconds(60);
}
