package com.wewins.fota.adapter.api.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 实例详情 DTO
 */
@Data
@Builder
public class InstanceDetailDTO {
    /**
     * 实例摘要
     */
    private InstanceMetricsEnhancedDTO summary;

    /**
     * 主机 CPU 使用率
     */
    private Double hostCpuUsage;

    /**
     * 主机内存使用率
     */
    private Double hostMemoryUsage;

    /**
     * 实例承载的热点产品列表
     */
    private List<HotProductDTO> hotProducts;

    /**
     * 所属区域
     */
    private String region;

    /**
     * 所属主机
     */
    private String host;

    /**
     * 实例标识
     */
    private String instance;

    /**
     * 最近刷新时间
     */
    private String lastRefreshTime;
}
