package com.wewins.fota.adapter.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstanceMetricsDTO {

    private String instance;

    private double cpuUsage;

    private double memoryUsage;

    private double checkQps;

    private double reportQps;

    private int activeRequests;

    private double blockRate;

    private String circuitState;
}
