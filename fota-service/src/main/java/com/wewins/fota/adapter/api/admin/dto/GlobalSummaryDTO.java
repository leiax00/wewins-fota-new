package com.wewins.fota.adapter.api.admin.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 全局聚合指标 DTO
 */
@Data
@Builder
public class GlobalSummaryDTO {
    /**
     * 总体负载评分
     */
    private Integer loadScore;

    /**
     * 总体负载等级
     */
    private String loadLevel;

    /**
     * 总 Check QPS
     */
    private Double checkQps;

    /**
     * 总 Report QPS
     */
    private Double reportQps;

    /**
     * 总 Check P50 延迟
     */
    private Double checkP50Latency;

    /**
     * 总 Check P99 延迟
     */
    private Double checkP99Latency;

    /**
     * 今日活跃设备总数
     */
    private Long todayActiveDevices;

    /**
     * 总限流率
     */
    private Double blockRate;

    /**
     * 区域数量
     */
    private Integer regionCount;

    /**
     * 总实例数
     */
    private Integer totalInstances;
}
