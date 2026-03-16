package com.wewins.fota.adapter.api.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 全系统监控指标 DTO
 */
@Data
@Builder
public class GlobalMonitorMetricsDTO {
    /**
     * 全局聚合指标
     */
    private GlobalSummaryDTO global;

    /**
     * 区域指标列表
     */
    private List<RegionMetricsDTO> regions;

    /**
     * 主机指标列表
     */
    private List<HostMetricsEnhancedDTO> hosts;

    /**
     * 实例指标列表
     */
    private List<InstanceMetricsEnhancedDTO> instances;

    /**
     * 热点产品列表
     */
    private List<HotProductMetricsEnhancedDTO> hotProducts;

    /**
     * 数据时间戳
     */
    private Instant timestamp;
}
