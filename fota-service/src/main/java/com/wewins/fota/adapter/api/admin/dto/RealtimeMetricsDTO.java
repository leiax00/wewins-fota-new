package com.wewins.fota.adapter.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeMetricsDTO {

    private int loadScore;

    private String loadLevel;

    private double cpuUsage;

    private double memoryUsage;

    private double currentQps;

    private double p99Latency;

    private int activeRequests;

    private double blockRate;

    private String circuitState;

    private Instant timestamp;
}
