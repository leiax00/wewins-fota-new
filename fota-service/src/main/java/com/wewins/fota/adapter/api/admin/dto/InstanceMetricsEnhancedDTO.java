package com.wewins.fota.adapter.api.admin.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 增强的实例指标 DTO（包含区域和主机信息）
 */
@Data
@Builder
public class InstanceMetricsEnhancedDTO {
    /**
     * 实例标识
     */
    private String instance;

    /**
     * 所属区域
     */
    private String region;

    /**
     * 所属主机
     */
    private String host;

    /**
     * 负载评分
     */
    private Integer loadScore;

    /**
     * 负载等级
     */
    private String loadLevel;

    /**
     * CPU 使用率
     */
    private Double cpuUsage;

    /**
     * 内存使用率
     */
    private Double memoryUsage;

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
     * 活跃请求数
     */
    private Integer activeRequests;

    /**
     * 限流率
     */
    private Double blockRate;

    /**
     * 熔断状态
     */
    private String circuitState;
}
