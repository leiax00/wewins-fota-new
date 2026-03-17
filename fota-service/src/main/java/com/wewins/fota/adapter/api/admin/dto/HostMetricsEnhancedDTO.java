package com.wewins.fota.adapter.api.admin.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 增强的主机指标 DTO（包含区域信息）
 */
@Data
@Builder
public class HostMetricsEnhancedDTO {
    /**
     * 主机标识
     */
    private String host;

    /**
     * 所属区域
     */
    private String region;

    /**
     * CPU 使用率
     */
    private Double cpuUsage;

    /**
     * 内存使用率
     */
    private Double memoryUsage;

    /**
     * 网络入站流量
     */
    private Double networkInBytes;

    /**
     * 网络出站流量
     */
    private Double networkOutBytes;

    /**
     * 承载的实例数量
     */
    private Integer instanceCount;
}
