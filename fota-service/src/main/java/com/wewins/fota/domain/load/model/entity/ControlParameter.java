package com.wewins.fota.domain.load.model.entity;

import com.wewins.fota.common.util.TimeConstants;
import com.wewins.fota.domain.load.model.enums.ProductPriority;
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

    private static final int DEFAULT_PROTECTED_CHECK_INTERVAL_SECONDS = 12 * TimeConstants.SECONDS_PER_HOUR;
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
     * 保护态检测周期，单位秒。
     * <p>
     * 默认 12 小时。
     * 当请求被 Sentinel 限流、熔断或系统保护拦截时，
     * 返回周期至少提升到该值。
     * </p>
     */
    private Integer protectedCheckIntervalSeconds;

    /**
     * 常规检测周期倍率。
     * <p>
     * 作用在基础检测周期之上，1.0 表示不额外放大或缩小。
     * </p>
     */
    private Double checkIntervalMultiplier;

    /**
     * 保护态倍率。
     * <p>
     * 当请求进入保护态时，在常规计算结果基础上继续放大。
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
     * 产品级周期偏置。
     * <p>
     * 用于让某个产品在相同负载下更激进或更保守。
     * </p>
     */
    private Double intervalBias;

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
     * 产品优先级。
     * <p>
     * 优先级越高，高负载下检测周期通常越不容易被拉长。
     * </p>
     */
    private ProductPriority priority;

    /**
     * 是否启用热点产品保护。
     * <p>
     * 启用后，热点产品在高负载下会得到更温和的周期拉长。
     * </p>
     */
    private Boolean hotspotProtectionEnabled;

    /**
     * 是否强制进入维护模式。
     */
    private Boolean forceMaintenance;

    /**
     * 维护模式提示文案。
     */
    private String maintenanceMessage;

    /**
     * 最近更新时间。
     */
    private Instant updatedAt;

    /**
     * 最近更新人。
     */
    private String updatedBy;

    public boolean isGlobal() {
        return productId == null;
    }

    public boolean isProductLevel() {
        return productId != null;
    }

    public static ControlParameter createGlobalDefault() {
        return ControlParameter.builder()
                .protectedCheckIntervalSeconds(DEFAULT_PROTECTED_CHECK_INTERVAL_SECONDS)
                .checkIntervalMultiplier(1.0)
                .protectedIntervalMultiplier(2.0)
                .downloadDelayMultiplier(1.0)
                .intervalBias(1.0)
                .minCheckIntervalSeconds(DEFAULT_MIN_CHECK_INTERVAL_SECONDS)
                .maxCheckIntervalSeconds(DEFAULT_MAX_CHECK_INTERVAL_SECONDS)
                .priority(ProductPriority.NORMAL)
                .hotspotProtectionEnabled(false)
                .forceMaintenance(false)
                .maintenanceMessage("")
                .updatedAt(Instant.now())
                .build();
    }

    public static ControlParameter createProductDefault(Long productId) {
        return ControlParameter.builder()
                .productId(productId)
                .protectedCheckIntervalSeconds(DEFAULT_PROTECTED_CHECK_INTERVAL_SECONDS)
                .checkIntervalMultiplier(1.0)
                .protectedIntervalMultiplier(2.0)
                .downloadDelayMultiplier(1.0)
                .intervalBias(1.0)
                .minCheckIntervalSeconds(DEFAULT_MIN_CHECK_INTERVAL_SECONDS)
                .maxCheckIntervalSeconds(DEFAULT_MAX_CHECK_INTERVAL_SECONDS)
                .priority(ProductPriority.NORMAL)
                .hotspotProtectionEnabled(false)
                .forceMaintenance(false)
                .maintenanceMessage("")
                .updatedAt(Instant.now())
                .build();
    }
}
