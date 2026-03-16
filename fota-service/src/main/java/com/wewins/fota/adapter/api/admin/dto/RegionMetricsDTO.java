package com.wewins.fota.adapter.api.admin.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 区域指标 DTO
 */
@Data
@Builder
public class RegionMetricsDTO {
    /**
     * 区域代码
     */
    private String region;

    /**
     * 负载评分
     */
    private Integer loadScore;

    /**
     * 负载等级
     */
    private String loadLevel;

    /**
     * Check QPS
     */
    private Double checkQps;

    /**
     * Report QPS
     */
    private Double reportQps;

    /**
     * Check P50 延迟
     */
    private Double checkP50Latency;

    /**
     * Check P99 延迟
     */
    private Double checkP99Latency;

    /**
     * Report P50 延迟
     */
    private Double reportP50Latency;

    /**
     * Report P99 延迟
     */
    private Double reportP99Latency;

    /**
     * 今日活跃设备数
     */
    private Long todayActiveDevices;

    /**
     * 限流率
     */
    private Double blockRate;

    /**
     * 实例数量
     */
    private Integer instanceCount;

    /**
     * 热点产品数量
     */
    private Integer hotProductCount;
}
