package com.wewins.fota.domain.load.model.entity;

import com.wewins.fota.common.util.TimeConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 控制参数实体
 * <p>
 * 用于存储全局或产品级的控制参数，支持动态调整设备行为
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlParameter {

    private static final int DEFAULT_MIN_CHECK_INTERVAL_SECONDS = 30 * TimeConstants.SECONDS_PER_MINUTE;
    private static final int DEFAULT_MAX_CHECK_INTERVAL_SECONDS = 2 * TimeConstants.SECONDS_PER_DAY;

    /**
     * 配置主键。
     */
    private Long id;

    /**
     * 关联产品 ID。
     * <p>
     * 为空表示全局配置；非空表示产品级覆盖配置。
     * </p>
     */
    private Long productId;

    /**
     * 保护态倍率。
     * <p>
     * 当请求进入保护态时，在常规结果基础上继续放大。
     * 例如 2.0 表示保护态周期至少为正常周期的 2 倍。
     * </p>
     */
    private Double protectedIntervalMultiplier;

    /**
     * 下载延迟倍率。
     * <p>
     * 只作用于 download delay 的计算，不直接决定 check interval。
     * </p>
     */
    private Double downloadDelayMultiplier;

    /**
     * 最小检测周期，单位秒。
     * <p>
     * 默认 30 分钟。
     * 最终下发给设备的周期不会低于该值。
     * </p>
     */
    private Integer minCheckIntervalSeconds;

    /**
     * 最大检测周期，单位秒。
     * <p>
     * 默认 2 天。
     * 最终下发给设备的周期不会高于该值。
     * </p>
     */
    private Integer maxCheckIntervalSeconds;

    /**
     * 最近更新时间。
     */
    private Instant updatedAt;

    /**
     * 最近更新人。
     */
    private String updatedBy;

    public static ControlParameter createGlobalDefault() {
        return ControlParameter.builder()
                .protectedIntervalMultiplier(2.0)
                .downloadDelayMultiplier(1.0)
                .minCheckIntervalSeconds(DEFAULT_MIN_CHECK_INTERVAL_SECONDS)
                .maxCheckIntervalSeconds(DEFAULT_MAX_CHECK_INTERVAL_SECONDS)
                .updatedAt(Instant.now())
                .build();
    }

    public static ControlParameter createProductDefault(Long productId) {
        return ControlParameter.builder()
                .productId(productId)
                .protectedIntervalMultiplier(2.0)
                .downloadDelayMultiplier(1.0)
                .minCheckIntervalSeconds(DEFAULT_MIN_CHECK_INTERVAL_SECONDS)
                .maxCheckIntervalSeconds(DEFAULT_MAX_CHECK_INTERVAL_SECONDS)
                .updatedAt(Instant.now())
                .build();
    }
}
