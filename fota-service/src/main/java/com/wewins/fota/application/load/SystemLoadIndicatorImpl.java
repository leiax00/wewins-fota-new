package com.wewins.fota.application.load;

import com.wewins.fota.application.load.config.LoadControlDefaults;
import com.wewins.fota.application.load.config.LoadControlRuntimeConfigService;
import com.wewins.fota.application.load.config.LoadScoringMetricConfig;
import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.model.vo.LoadSnapshot;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import com.wewins.fota.infra.metrics.NodeIdentity;
import com.wewins.fota.infra.metrics.PrometheusClient;
import com.wewins.fota.infra.sentinel.config.SentinelRuleManager;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class SystemLoadIndicatorImpl implements SystemLoadIndicator {

    private static final String CHECK_RESOURCE = "upgrade:check";
    private static final String REPORT_RESOURCE = "upgrade:report";
    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private final MeterRegistry meterRegistry;
    private final PrometheusClient prometheusClient;
    private final SentinelRuleManager sentinelRuleManager;
    private final LoadControlRuntimeConfigService runtimeConfigService;
    private final String hostLabel;
    private final String region;
    private final String instance;

    private final AtomicReference<LoadSnapshot> cachedSnapshot = new AtomicReference<>();
    private final AtomicReference<Instant> lastUpdateTime = new AtomicReference<>(Instant.EPOCH);

    public SystemLoadIndicatorImpl(
            MeterRegistry meterRegistry,
            PrometheusClient prometheusClient,
            SentinelRuleManager sentinelRuleManager,
            LoadControlRuntimeConfigService runtimeConfigService,
            NodeIdentity nodeIdentity) {
        this.meterRegistry = meterRegistry;
        this.prometheusClient = prometheusClient;
        this.sentinelRuleManager = sentinelRuleManager;
        this.runtimeConfigService = runtimeConfigService;
        this.hostLabel = nodeIdentity.hostCode();
        this.region = nodeIdentity.regionCode();
        this.instance = nodeIdentity.monitoringInstanceLabel();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }

    @Override
    public LoadSnapshot getSnapshot() {
        Instant now = Instant.now();
        Instant lastUpdate = lastUpdateTime.get();

        if (Duration.between(lastUpdate, now).compareTo(CACHE_TTL) < 0) {
            LoadSnapshot cached = cachedSnapshot.get();
            if (cached != null) {
                return cached;
            }
        }

        LoadSnapshot snapshot = collectSnapshot();
        cachedSnapshot.set(snapshot);
        lastUpdateTime.set(now);
        return snapshot;
    }

    @Override
    public LoadLevel getLoadLevel() {
        return getSnapshot().level();
    }

    @Override
    public boolean isOverloaded() {
        return getSnapshot().isOverloaded();
    }

    private LoadSnapshot collectSnapshot() {
        double cpuUsage = getCpuUsage();
        double memoryUsage = getMemoryUsage();
        double connectionPoolUsage = getConnectionPoolUsage();

        double hostCpuUsage = getHostCpuUsage();
        double hostMemoryUsage = getHostMemoryUsage();
        double networkInBytes = getNetworkInBytes();
        double networkOutBytes = getNetworkOutBytes();

        double instanceCheckQps = prometheusClient.getInstanceCheckQps(region, instance);
        double instanceReportQps = prometheusClient.getInstanceReportQps(region, instance);
        double instanceCheckP50Latency = getInstanceCheckP50Latency();
        double instanceCheckP99Latency = getInstanceCheckP99Latency();
        double instanceReportP50Latency = getInstanceReportP50Latency();
        double instanceReportP99Latency = getInstanceReportP99Latency();

        double regionCheckQps = prometheusClient.getRegionCheckQps(region);
        double regionReportQps = prometheusClient.getRegionReportQps(region);
        double regionCheckP50Latency = getRegionCheckP50Latency();
        double regionCheckP99Latency = getRegionCheckP99Latency();
        double regionReportP50Latency = getRegionReportP50Latency();
        double regionReportP99Latency = getRegionReportP99Latency();
        int regionInstanceCount = Math.max(prometheusClient.getRegionInstanceCount(region), 1);

        int totalScore = calculateTotalScore(
                cpuUsage,
                memoryUsage,
                connectionPoolUsage,
                hostCpuUsage,
                hostMemoryUsage,
                instanceCheckQps,
                instanceReportQps,
                instanceCheckP50Latency,
                instanceCheckP99Latency,
                instanceReportP50Latency,
                instanceReportP99Latency,
                regionCheckQps,
                regionReportQps,
                regionCheckP50Latency,
                regionCheckP99Latency,
                regionReportP50Latency,
                regionReportP99Latency,
                regionInstanceCount
        );
        LoadLevel level = LoadLevel.fromScore(totalScore);

        return LoadSnapshot.builder()
                .timestamp(Instant.now())
                .totalScore(totalScore)
                .level(level)
                .cpuUsage(cpuUsage)
                .memoryUsage(memoryUsage)
                .connectionPoolUsage(connectionPoolUsage)
                .hostCpuUsage(hostCpuUsage)
                .hostMemoryUsage(hostMemoryUsage)
                .networkInBytes(networkInBytes)
                .networkOutBytes(networkOutBytes)
                .checkQps(nonNegative(instanceCheckQps))
                .reportQps(nonNegative(instanceReportQps))
                .checkP50Latency(nonNegative(instanceCheckP50Latency))
                .checkP99Latency(nonNegative(instanceCheckP99Latency))
                .reportP50Latency(nonNegative(instanceReportP50Latency))
                .reportP99Latency(nonNegative(instanceReportP99Latency))
                .build();
    }

    private double getCpuUsage() {
        try {
            var gauge = meterRegistry.find("system.cpu.usage").gauge();
            if (gauge != null) {
                double usage = gauge.value();
                if (usage >= 0 && usage <= 1) {
                    return usage * 100;
                }
            }
            double load = osBean.getSystemLoadAverage();
            int processors = osBean.getAvailableProcessors();
            if (load < 0 || processors <= 0) {
                return 0;
            }
            return Math.min(100, (load / processors) * 100);
        } catch (Exception e) {
            log.debug("Failed to get CPU usage", e);
            return 0;
        }
    }

    private double getMemoryUsage() {
        try {
            var heap = memoryBean.getHeapMemoryUsage();
            long used = heap.getUsed();
            long max = heap.getMax();
            if (max <= 0) {
                return 0;
            }
            return (used * 100.0) / max;
        } catch (Exception e) {
            log.debug("Failed to get memory usage", e);
            return 0;
        }
    }

    private double getConnectionPoolUsage() {
        try {
            var gauge = meterRegistry.find("hikaricp.connections.active").gauge();
            var maxGauge = meterRegistry.find("hikaricp.connections.max").gauge();
            if (gauge == null || maxGauge == null) {
                return 0;
            }
            double active = gauge.value();
            double max = maxGauge.value();
            if (max <= 0) {
                return 0;
            }
            return (active / max) * 100;
        } catch (Exception e) {
            log.debug("Failed to get connection pool usage", e);
            return 0;
        }
    }

    private double getHostCpuUsage() {
        if (hostLabel == null || hostLabel.isEmpty()) {
            return -1;
        }
        return prometheusClient.getHostCpuUsage(hostLabel);
    }

    private double getHostMemoryUsage() {
        if (hostLabel == null || hostLabel.isEmpty()) {
            return -1;
        }
        return prometheusClient.getHostMemoryUsage(hostLabel);
    }

    private double getNetworkInBytes() {
        if (hostLabel == null || hostLabel.isEmpty()) {
            return -1;
        }
        return prometheusClient.getHostNetworkInBytes(hostLabel);
    }

    private double getNetworkOutBytes() {
        if (hostLabel == null || hostLabel.isEmpty()) {
            return -1;
        }
        return prometheusClient.getHostNetworkOutBytes(hostLabel);
    }

    private double getInstanceCheckP50Latency() {
        return prometheusClient.getInstanceCheckP50Latency(region, instance);
    }

    private double getInstanceCheckP99Latency() {
        return prometheusClient.getInstanceCheckP99Latency(region, instance);
    }

    private double getInstanceReportP50Latency() {
        return prometheusClient.getInstanceReportP50Latency(region, instance);
    }

    private double getInstanceReportP99Latency() {
        return prometheusClient.getInstanceReportP99Latency(region, instance);
    }

    private double getRegionCheckP50Latency() {
        return prometheusClient.getRegionCheckP50Latency(region);
    }

    private double getRegionCheckP99Latency() {
        return prometheusClient.getRegionCheckP99Latency(region);
    }

    private double getRegionReportP50Latency() {
        return prometheusClient.getRegionReportP50Latency(region);
    }

    private double getRegionReportP99Latency() {
        return prometheusClient.getRegionReportP99Latency(region);
    }

    private int calculateTotalScore(
            double cpu,
            double memory,
            double pool,
            double hostCpu,
            double hostMemory,
            double instanceCheckQps,
            double instanceReportQps,
            double instanceCheckP50,
            double instanceCheckP99,
            double instanceReportP50,
            double instanceReportP99,
            double regionCheckQps,
            double regionReportQps,
            double regionCheckP50,
            double regionCheckP99,
            double regionReportP50,
            double regionReportP99,
            int regionInstanceCount) {
        Map<String, LoadScoringMetricConfig> metrics = runtimeConfigService.getEffectiveScoringMetricMap();

        int cpuScore = calculateMetricScore(metrics.get(LoadControlDefaults.INSTANCE_JVM_CPU), cpu);
        int memoryScore = calculateMetricScore(metrics.get(LoadControlDefaults.INSTANCE_JVM_HEAP), memory);
        int poolScore = calculateMetricScore(metrics.get(LoadControlDefaults.INSTANCE_DB_POOL_USAGE), pool);

        int instanceCheckQpsScore = calculateMetricScore(
                metrics.get(LoadControlDefaults.INSTANCE_CHECK_QPS_UTILIZATION),
                toUtilizationPercent(instanceCheckQps, getCheckCapacity()));
        int instanceReportQpsScore = calculateMetricScore(
                metrics.get(LoadControlDefaults.INSTANCE_REPORT_QPS_UTILIZATION),
                toUtilizationPercent(instanceReportQps, getReportCapacity()));
        int instanceCheckP50Score = calculateMetricScore(metrics.get(LoadControlDefaults.INSTANCE_CHECK_P50), instanceCheckP50);
        int instanceCheckP99Score = calculateMetricScore(metrics.get(LoadControlDefaults.INSTANCE_CHECK_P99), instanceCheckP99);
        int instanceReportP50Score = calculateMetricScore(metrics.get(LoadControlDefaults.INSTANCE_REPORT_P50), instanceReportP50);
        int instanceReportP99Score = calculateMetricScore(metrics.get(LoadControlDefaults.INSTANCE_REPORT_P99), instanceReportP99);

        int hostCpuScore = calculateMetricScore(metrics.get(LoadControlDefaults.HOST_CPU), hostCpu);
        int hostMemoryScore = calculateMetricScore(metrics.get(LoadControlDefaults.HOST_MEMORY), hostMemory);

        int regionCheckQpsScore = calculateMetricScore(
                metrics.get(LoadControlDefaults.REGION_CHECK_QPS_UTILIZATION),
                toUtilizationPercent(regionCheckQps, getRegionCheckCapacity(regionInstanceCount)));
        int regionReportQpsScore = calculateMetricScore(
                metrics.get(LoadControlDefaults.REGION_REPORT_QPS_UTILIZATION),
                toUtilizationPercent(regionReportQps, getRegionReportCapacity(regionInstanceCount)));
        int regionCheckP50Score = calculateMetricScore(metrics.get(LoadControlDefaults.REGION_CHECK_P50), regionCheckP50);
        int regionCheckP99Score = calculateMetricScore(metrics.get(LoadControlDefaults.REGION_CHECK_P99), regionCheckP99);
        int regionReportP50Score = calculateMetricScore(metrics.get(LoadControlDefaults.REGION_REPORT_P50), regionReportP50);
        int regionReportP99Score = calculateMetricScore(metrics.get(LoadControlDefaults.REGION_REPORT_P99), regionReportP99);

        int total = cpuScore + memoryScore + poolScore
                + instanceCheckQpsScore + instanceReportQpsScore
                + instanceCheckP50Score + instanceCheckP99Score
                + instanceReportP50Score + instanceReportP99Score
                + hostCpuScore + hostMemoryScore
                + regionCheckQpsScore + regionReportQpsScore
                + regionCheckP50Score + regionCheckP99Score
                + regionReportP50Score + regionReportP99Score;
        return Math.max(0, Math.min(100, total));
    }

    private int calculateMetricScore(LoadScoringMetricConfig config, double value) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return 0;
        }
        Integer maxScore = config.getWeight();
        Double warning = config.getWarning();
        Double critical = config.getCritical();
        if (maxScore == null || maxScore <= 0 || warning == null || critical == null || warning <= 0 || critical <= warning) {
            return 0;
        }
        if (value < 0) {
            return 0;
        }
        if (value >= critical) {
            return maxScore;
        }
        if (value >= warning) {
            int partial = (int) (maxScore * 0.3);
            int range = (int) (maxScore * 0.7);
            return partial + (int) (range * (value - warning) / (critical - warning));
        }
        return (int) (maxScore * 0.3 * value / warning);
    }

    private double getCheckCapacity() {
        return sentinelRuleManager.getFlowThreshold(CHECK_RESOURCE, 1000);
    }

    private double getReportCapacity() {
        return sentinelRuleManager.getFlowThreshold(REPORT_RESOURCE, 2500);
    }

    private double getRegionCheckCapacity(int instanceCount) {
        return getCheckCapacity() * instanceCount;
    }

    private double getRegionReportCapacity(int instanceCount) {
        return getReportCapacity() * instanceCount;
    }

    private double toUtilizationPercent(double currentQps, double capacity) {
        if (currentQps < 0 || capacity <= 0) {
            return -1;
        }
        return currentQps * 100.0 / capacity;
    }

    private double nonNegative(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return 0;
        }
        return value;
    }
}
