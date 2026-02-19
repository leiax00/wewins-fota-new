package com.wewins.fota.cache.ratelimit;

import java.time.Duration;

/**
 * 设备限流器接口
 * <p>
 * 提供基于 Redis 的限流功能，支持固定窗口限流算法
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-17
 */
public interface DeviceRateLimiter {

    /**
     * 检查是否允许请求（固定窗口限流）
     * <p>
     * 基于固定时间窗口的限流算法，在窗口内限制最大请求数
     * </p>
     *
     * @param key        限流键（如设备 IMEI、IP 地址等）
     * @param maxRequests 最大请求数
     * @param window     时间窗口大小
     * @return 限流决策结果
     */
    RateLimitDecision allow(String key, int maxRequests, Duration window);

    /**
     * LastSeen 更新限频
     * <p>
     * 限制 LastSeen 字段的更新频率，避免频繁写入数据库
     * </p>
     *
     * @param imei         设备 IMEI
     * @param minInterval 最小更新间隔
     * @return true-允许更新，false-不允许更新
     */
    boolean allowLastSeenUpdate(String imei, Duration minInterval);
}
