package com.wewins.fota.application.load.config;

import com.wewins.fota.domain.load.model.entity.ControlParameter;

import java.util.List;

public final class LoadControlDefaults {

    public static final String INSTANCE_JVM_CPU = "INSTANCE_JVM_CPU";
    public static final String INSTANCE_JVM_HEAP = "INSTANCE_JVM_HEAP";
    public static final String INSTANCE_DB_POOL_USAGE = "INSTANCE_DB_POOL_USAGE";
    public static final String INSTANCE_CHECK_QPS_UTILIZATION = "INSTANCE_CHECK_QPS_UTILIZATION";
    public static final String INSTANCE_REPORT_QPS_UTILIZATION = "INSTANCE_REPORT_QPS_UTILIZATION";
    public static final String INSTANCE_CHECK_P50 = "INSTANCE_CHECK_P50";
    public static final String INSTANCE_CHECK_P99 = "INSTANCE_CHECK_P99";
    public static final String INSTANCE_REPORT_P50 = "INSTANCE_REPORT_P50";
    public static final String INSTANCE_REPORT_P99 = "INSTANCE_REPORT_P99";
    public static final String HOST_CPU = "HOST_CPU";
    public static final String HOST_MEMORY = "HOST_MEMORY";
    public static final String REGION_CHECK_QPS_UTILIZATION = "REGION_CHECK_QPS_UTILIZATION";
    public static final String REGION_REPORT_QPS_UTILIZATION = "REGION_REPORT_QPS_UTILIZATION";
    public static final String REGION_CHECK_P50 = "REGION_CHECK_P50";
    public static final String REGION_CHECK_P99 = "REGION_CHECK_P99";
    public static final String REGION_REPORT_P50 = "REGION_REPORT_P50";
    public static final String REGION_REPORT_P99 = "REGION_REPORT_P99";

    private LoadControlDefaults() {
    }

    public static LoadControlRuntimeConfig createDefaultRuntimeConfig() {
        return LoadControlRuntimeConfig.builder()
                .control(ControlParameter.createGlobalDefault())
                .scoring(defaultScoringConfig())
                .build();
    }

    public static LoadScoringConfig defaultScoringConfig() {
        return LoadScoringConfig.builder()
                .instanceMetrics(List.of(
                        metric(INSTANCE_JVM_CPU, "INSTANCE", "RESOURCE", "percent", 70, 90, 16),
                        metric(INSTANCE_JVM_HEAP, "INSTANCE", "RESOURCE", "percent", 75, 90, 10),
                        metric(INSTANCE_DB_POOL_USAGE, "INSTANCE", "RESOURCE", "percent", 80, 95, 8),
                        qpsMetric(INSTANCE_CHECK_QPS_UTILIZATION, "INSTANCE", 60, 80, 11, "upgrade:check", "instance_direct"),
                        qpsMetric(INSTANCE_REPORT_QPS_UTILIZATION, "INSTANCE", 60, 80, 4, "upgrade:report", "instance_direct"),
                        metric(INSTANCE_CHECK_P50, "INSTANCE", "LATENCY", "ms", 30, 60, 5),
                        metric(INSTANCE_CHECK_P99, "INSTANCE", "LATENCY", "ms", 50, 100, 11),
                        metric(INSTANCE_REPORT_P50, "INSTANCE", "LATENCY", "ms", 20, 40, 2),
                        metric(INSTANCE_REPORT_P99, "INSTANCE", "LATENCY", "ms", 40, 80, 6)
                ))
                .hostMetrics(List.of(
                        metric(HOST_CPU, "HOST", "RESOURCE", "percent", 70, 90, 7),
                        metric(HOST_MEMORY, "HOST", "RESOURCE", "percent", 75, 90, 4)
                ))
                .regionMetrics(List.of(
                        qpsMetric(REGION_CHECK_QPS_UTILIZATION, "REGION", 60, 80, 5, "upgrade:check", "region_instance_count_multiply"),
                        qpsMetric(REGION_REPORT_QPS_UTILIZATION, "REGION", 60, 80, 2, "upgrade:report", "region_instance_count_multiply"),
                        metric(REGION_CHECK_P50, "REGION", "LATENCY", "ms", 35, 70, 2),
                        metric(REGION_CHECK_P99, "REGION", "LATENCY", "ms", 60, 120, 4),
                        metric(REGION_REPORT_P50, "REGION", "LATENCY", "ms", 25, 50, 1),
                        metric(REGION_REPORT_P99, "REGION", "LATENCY", "ms", 50, 100, 2)
                ))
                .build();
    }

    private static LoadScoringMetricConfig metric(
            String metricKey,
            String scope,
            String metricType,
            String unit,
            double warning,
            double critical,
            int weight) {
        return LoadScoringMetricConfig.builder()
                .metricKey(metricKey)
                .scope(scope)
                .metricType(metricType)
                .unit(unit)
                .enabled(true)
                .warning(warning)
                .critical(critical)
                .weight(weight)
                .schemaVersion(1)
                .build();
    }

    private static LoadScoringMetricConfig qpsMetric(
            String metricKey,
            String scope,
            double warning,
            double critical,
            int weight,
            String resource,
            String aggregation) {
        return LoadScoringMetricConfig.builder()
                .metricKey(metricKey)
                .scope(scope)
                .metricType("QPS_UTILIZATION")
                .unit("percent")
                .enabled(true)
                .warning(warning)
                .critical(critical)
                .weight(weight)
                .capacitySource(LoadCapacitySourceConfig.builder()
                        .type("sentinel_flow")
                        .resource(resource)
                        .aggregation(aggregation)
                        .build())
                .schemaVersion(1)
                .build();
    }
}
