package com.wewins.fota.application.sentinel.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sentinel 降级规则 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentinelDegradeRuleDTO {

    /**
     * 资源名称，对应 Sentinel 的资源标识
     */
    @NotBlank(message = "资源名称不能为空")
    @Size(max = 200, message = "资源名称不能超过 200 字符")
    private String resource;

    /**
     * 降级策略：RT、EXCEPTION_RATIO、EXCEPTION_COUNT
     */
    @Pattern(regexp = "RT|EXCEPTION_RATIO|EXCEPTION_COUNT", message = "降级策略必须是 RT、EXCEPTION_RATIO 或 EXCEPTION_COUNT")
    @Builder.Default
    private String grade = "RT";

    /**
     * 降级阈值
     */
    @Min(value = 1, message = "降级阈值必须大于 0")
    @Max(value = 10000, message = "降级阈值不能超过 10000")
    @Builder.Default
    private int count = 50;

    /**
     * 降级时间窗口（秒）
     */
    @Min(value = 1, message = "降级时间窗口至少为 1 秒")
    @Max(value = 3600, message = "降级时间窗口不能超过 3600 秒")
    @Builder.Default
    private int timeWindow = 30;

    /**
     * 最小请求数
     */
    @Min(value = 1, message = "最小请求数必须大于 0")
    @Max(value = 10000, message = "最小请求数不能超过 10000")
    @Builder.Default
    private int minRequestAmount = 100;

    /**
     * 慢调用比例阈值（仅当 grade=EXCEPTION_RATIO 时有效）
     */
    @DecimalMin(value = "0.0", message = "慢调用比例不能小于 0")
    @DecimalMax(value = "1.0", message = "慢调用比例不能大于 1")
    @Builder.Default
    private double slowRatioThreshold = 0.5;
}
