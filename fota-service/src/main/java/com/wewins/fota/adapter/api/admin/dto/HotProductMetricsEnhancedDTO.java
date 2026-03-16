package com.wewins.fota.adapter.api.admin.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 增强的热点产品指标 DTO
 */
@Data
@Builder
public class HotProductMetricsEnhancedDTO {
    /**
     * 产品型号
     */
    private String product;

    /**
     * Check QPS
     */
    private Double checkQps;

    /**
     * Report QPS
     */
    private Double reportQps;

    /**
     * 流量占比
     */
    private Double trafficShare;

    /**
     * 活跃区域数量
     */
    private Integer activeRegionCount;
}
