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

    private List<TrendPointDTO> checkP50Latency;

    private List<TrendPointDTO> checkP99Latency;

    private List<TrendPointDTO> reportP50Latency;

    private List<TrendPointDTO> reportP99Latency;

    private List<TrendPointDTO> blockRate;

    private List<TrendPointDTO> activeDevicesTotal;

    private List<TrendPointDTO> activeDevicesIncrement;
}
