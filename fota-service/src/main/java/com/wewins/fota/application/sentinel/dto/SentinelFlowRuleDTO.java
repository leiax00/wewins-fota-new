package com.wewins.fota.application.sentinel.dto;

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
 * Sentinel 流控规则 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentinelFlowRuleDTO {

    /**
     * 资源名称，对应 Sentinel 的资源标识
     */
    @NotBlank(message = "资源名称不能为空")
    @Size(max = 200, message = "资源名称不能超过 200 字符")
    private String resource;

    /**
     * 限流阈值类型：QPS 或 THREAD
     */
    @Pattern(regexp = "QPS|THREAD", message = "限流类型必须是 QPS 或 THREAD")
    @Builder.Default
    private String grade = "QPS";

    /**
     * 限流阈值
     */
    @Min(value = 1, message = "限流阈值必须大于 0")
    @Max(value = 100000, message = "限流阈值不能超过 100000")
    @Builder.Default
    private int count = 2500;

    /**
     * 流控效果：RATE_LIMITER、WARM_UP、WARM_UP_RATE_LIMITER
     */
    @Pattern(regexp = "RATE_LIMITER|WARM_UP|WARM_UP_RATE_LIMITER|DEFAULT", message = "流控效果无效")
    @Builder.Default
    private String controlBehavior = "RATE_LIMITER";

    /**
     * 最大排队时间（毫秒）
     */
    @Min(value = 0, message = "最大排队时间不能小于 0")
    @Max(value = 10000, message = "最大排队时间不能超过 10000 毫秒")
    @Builder.Default
    private int maxQueueingTimeMs = 50;
}
