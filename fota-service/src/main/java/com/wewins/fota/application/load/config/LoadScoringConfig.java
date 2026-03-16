package com.wewins.fota.application.load.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadScoringConfig {

    @Builder.Default
    private List<LoadScoringMetricConfig> instanceMetrics = new ArrayList<>();

    @Builder.Default
    private List<LoadScoringMetricConfig> hostMetrics = new ArrayList<>();

    @Builder.Default
    private List<LoadScoringMetricConfig> regionMetrics = new ArrayList<>();
}
