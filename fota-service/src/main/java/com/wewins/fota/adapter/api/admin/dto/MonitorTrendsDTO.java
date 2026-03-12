package com.wewins.fota.adapter.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorTrendsDTO {

    private String range;

    private int stepSeconds;

    private List<TrendPointDTO> checkQps;

    private List<TrendPointDTO> reportQps;

    private List<TrendPointDTO> p50Latency;

    private List<TrendPointDTO> p99Latency;

    private List<TrendPointDTO> blockRate;
}
