package com.wewins.fota.cache.ratelimit;

import lombok.Builder;
import lombok.Data;

/**
 * 限流决策结果
 * <p>
 * 封装限流检查的结果，包括是否允许请求、剩余配额、重置时间等信息
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-17
 */
@Data
@Builder
public class RateLimitDecision {

    /**
     * 是否允许请求
     * <p>
     * true-允许通过，false-被限流拒绝
     * </p>
     */
    private boolean allowed;

    /**
     * 剩余配额
     * <p>
     * 当前时间窗口内剩余的请求次数
     * </p>
     */
    private long remaining;

    /**
     * 重置时间戳（秒）
     * <p>
     * 当前时间窗口重置的 Unix 时间戳（秒）
     * </p>
     */
    private long resetAtEpochSecond;

    /**
     * 限流原因
     * <p>
     * 当 allowed=false 时，包含限流原因说明
     * </p>
     */
    private String reason;

    /**
     * 创建允许通过的决策
     *
     * @param remaining 剩余配额
     * @param resetAt   重置时间戳
     * @return RateLimitDecision
     */
    public static RateLimitDecision allowed(long remaining, long resetAt) {
        return RateLimitDecision.builder()
                .allowed(true)
                .remaining(remaining)
                .resetAtEpochSecond(resetAt)
                .build();
    }

    /**
     * 创建拒绝通过的决策
     *
     * @param reason 拒绝原因
     * @return RateLimitDecision
     */
    public static RateLimitDecision denied(String reason) {
        return RateLimitDecision.builder()
                .allowed(false)
                .remaining(0)
                .resetAtEpochSecond(0)
                .reason(reason)
                .build();
    }
}
