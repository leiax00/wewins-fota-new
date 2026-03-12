package com.wewins.fota.adapter.api.admin.dto;

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
     * 保护态倍率。
     */
    private Double protectedIntervalMultiplier;

    /**
     * 下载延迟倍率。
     */
    private Double downloadDelayMultiplier;

    /**
     * 最小检测周期，单位秒。
     */
    private Integer minCheckIntervalSeconds;

    /**
     * 最大检测周期，单位秒。
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
}
