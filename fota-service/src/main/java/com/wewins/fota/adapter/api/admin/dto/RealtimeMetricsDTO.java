package com.wewins.fota.adapter.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeMetricsDTO {

    private int loadScore;

    private String loadLevel;

    private double cpuUsage;

    private double memoryUsage;

    private double checkQps;

    private double reportQps;

    private double checkP50Latency;

    private double checkP99Latency;

    private double reportP50Latency;

    private double reportP99Latency;

    private int activeRequests;

    private long todayActiveDevices;

    private double blockRate;

    private String circuitState;

    private String region;

    private String host;

    private double hostCpuUsage;

    private double hostMemoryUsage;

    private double networkInBytes;

    private double networkOutBytes;

    private HostMetricsDTO hostSummary;

    private InstanceMetricsDTO instanceSummary;

    private List<HostMetricsDTO> hosts;

    private List<InstanceMetricsDTO> instances;

    private ControlStateDTO controlState;

    private List<HotProductDTO> hotProducts;

    private Instant timestamp;
}
