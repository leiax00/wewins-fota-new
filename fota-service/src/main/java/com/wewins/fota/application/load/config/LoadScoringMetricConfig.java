package com.wewins.fota.application.load.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadScoringMetricConfig {

    private String metricKey;
    private String scope;
    private String metricType;
    private String unit;
    private Boolean enabled;
    private Double warning;
    private Double critical;
    private Integer weight;
    private LoadCapacitySourceConfig capacitySource;
    private String description;
    private Integer schemaVersion;
}
