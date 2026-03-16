package com.wewins.fota.adapter.api.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 区域详情 DTO
 */
@Data
@Builder
public class RegionDetailDTO {
    /**
     * 区域摘要
     */
    private RegionMetricsDTO summary;

    /**
     * 区域内主机列表
     */
    private List<HostMetricsDTO> hosts;

    /**
     * 区域内实例列表
     */
    private List<InstanceMetricsDTO> instances;

    /**
     * 区域热点产品列表
     */
    private List<HotProductDTO> hotProducts;
}
