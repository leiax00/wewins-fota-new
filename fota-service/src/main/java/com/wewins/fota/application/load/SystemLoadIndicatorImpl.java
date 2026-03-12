package com.wewins.fota.application.load;

import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.model.vo.LoadSnapshot;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import com.wewins.fota.infra.metrics.NodeIdentity;
import com.wewins.fota.infra.metrics.PrometheusClient;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class SystemLoadIndicatorImpl implements SystemLoadIndicator {

    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private final MeterRegistry meterRegistry;
    private final PrometheusClient prometheusClient;
    private final String hostLabel;
    private final String region;

    private final AtomicReference<LoadSnapshot> cachedSnapshot = new AtomicReference<>();
    private final AtomicReference<Instant> lastUpdateTime = new AtomicReference<>(Instant.EPOCH);
    private static final Duration CACHE_TTL = Duration.ofSeconds(1);

    public SystemLoadIndicatorImpl(
            MeterRegistry meterRegistry,
            PrometheusClient prometheusClient,
            NodeIdentity nodeIdentity) {
        this.meterRegistry = meterRegistry;
        this.prometheusClient = prometheusClient;
        this.hostLabel = nodeIdentity.hostCode();
        this.region = nodeIdentity.regionCode();
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
        double qps = getCurrentQps();
        double p50Latency = getP50Latency();
        double p99Latency = getP99Latency();
        double connectionPoolUsage = getConnectionPoolUsage();

        double hostCpuUsage = getHostCpuUsage();
        double hostMemoryUsage = getHostMemoryUsage();
        double networkInBytes = getNetworkInBytes();
        double networkOutBytes = getNetworkOutBytes();
        double checkQps = getCheckQps();
        double reportQps = getReportQps();

        int totalScore = calculateTotalScore(
                cpuUsage, memoryUsage, qps, p99Latency, connectionPoolUsage,
                hostCpuUsage, hostMemoryUsage, checkQps, reportQps
        );
        LoadLevel level = LoadLevel.fromScore(totalScore);

        return LoadSnapshot.builder()
                .timestamp(Instant.now())
                .totalScore(totalScore)
                .level(level)
                .cpuUsage(cpuUsage)
                .memoryUsage(memoryUsage)
                .qps(qps)
                .p50Latency(p50Latency)
                .p99Latency(p99Latency)
                .connectionPoolUsage(connectionPoolUsage)
                .hostCpuUsage(hostCpuUsage)
                .hostMemoryUsage(hostMemoryUsage)
                .networkInBytes(networkInBytes)
                .networkOutBytes(networkOutBytes)
                .checkQps(checkQps)
                .reportQps(reportQps)
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

    private double getCurrentQps() {
        double checkQps = getCheckQps();
        double reportQps = getReportQps();
        if (checkQps >= 0 && reportQps >= 0) {
            return checkQps + reportQps;
        }
        return 0;
    }

    private double getP50Latency() {
        double regionP50 = prometheusClient.getDeviceApiP50Latency(region);
        if (regionP50 >= 0) {
            return regionP50;
        }
        return 0;
    }

    private double getP99Latency() {
        double regionP99 = prometheusClient.getDeviceApiP99Latency(region);
        if (regionP99 >= 0) {
            return regionP99;
        }
        return 0;
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

    private double getCheckQps() {
        return prometheusClient.getRegionCheckQps(region);
    }

    private double getReportQps() {
        return prometheusClient.getRegionReportQps(region);
    }

    private int calculateTotalScore(
            double cpu, double memory, double qps, double p99, double pool,
            double hostCpu, double hostMemory, double checkQps, double reportQps) {
        
        int cpuScore = calculateMetricScore(cpu, 70, 90, 20);
        int memoryScore = calculateMetricScore(memory, 75, 90, 15);
        int qpsScore = calculateMetricScore(qps, 8000, 12000, 15);
        int p99Score = calculateMetricScore(p99, 30, 50, 10);
        int poolScore = calculateMetricScore(pool, 80, 95, 10);

        int hostCpuScore = hostCpu >= 0 ? calculateMetricScore(hostCpu, 70, 90, 15) : 0;
        int hostMemoryScore = hostMemory >= 0 ? calculateMetricScore(hostMemory, 75, 90, 10) : 0;
        int checkQpsScore = checkQps >= 0 ? calculateMetricScore(checkQps, 2000, 3000, 3) : 0;
        int reportQpsScore = reportQps >= 0 ? calculateMetricScore(reportQps, 4000, 6000, 2) : 0;

        return cpuScore + memoryScore + qpsScore + p99Score + poolScore
                + hostCpuScore + hostMemoryScore + checkQpsScore + reportQpsScore;
    }

    private int calculateMetricScore(double value, double warning, double critical, int maxScore) {
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
}
