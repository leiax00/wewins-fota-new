package com.wewins.fota.adapter.api.admin.dto;

import com.wewins.fota.domain.load.model.enums.ProductPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlParameterDTO {

    /**
     * 产品 ID；为空表示全局配置。
     */
    private Long productId;

    /**
     * Sentinel 保护态下的基础检测周期，单位秒。
     */
    private Integer protectedCheckIntervalSeconds;

    /**
     * 常规检测周期倍率。
     */
    private Double checkIntervalMultiplier;

    /**
     * 保护态倍率。
     */
    private Double protectedIntervalMultiplier;

    /**
     * 下载延迟倍率。
     */
    private Double downloadDelayMultiplier;

    /**
     * 产品级周期偏置。
     */
    private Double intervalBias;

    /**
     * 最小检测周期，单位秒。
     */
    private Integer minCheckIntervalSeconds;

    /**
     * 最大检测周期，单位秒。
     */
    private Integer maxCheckIntervalSeconds;

    /**
     * 产品优先级。
     */
    private ProductPriority priority;

    /**
     * 是否启用热点产品保护。
     */
    private Boolean hotspotProtectionEnabled;

    /**
     * 是否强制进入维护模式。
     */
    private Boolean forceMaintenance;

    /**
     * 维护提示文案。
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
}
