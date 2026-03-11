package com.wewins.fota.adapter.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostMetricsDTO {

    private String host;

    private double cpuUsage;

    private double memoryUsage;

    private double networkInBytes;

    private double networkOutBytes;
}
