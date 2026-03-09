package com.wewins.fota.application.load;

import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.model.vo.LoadSnapshot;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 系统负载指标服务实现
 * <p>
 * 采集 CPU、内存、QPS、延迟、连接池等指标，计算综合负载评分
 * </p>
 */
@Slf4j
@Service
public class SystemLoadIndicatorImpl implements SystemLoadIndicator {

    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private final MeterRegistry meterRegistry;

    private final AtomicReference<LoadSnapshot> cachedSnapshot = new AtomicReference<>();
    private final AtomicReference<Instant> lastUpdateTime = new AtomicReference<>(Instant.EPOCH);
    private static final Duration CACHE_TTL = Duration.ofSeconds(1);

    public SystemLoadIndicatorImpl(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
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
        double p99Latency = getP99Latency();
        double connectionPoolUsage = getConnectionPoolUsage();

        int totalScore = calculateTotalScore(cpuUsage, memoryUsage, qps, p99Latency, connectionPoolUsage);
        LoadLevel level = LoadLevel.fromScore(totalScore);

        return LoadSnapshot.builder()
                .timestamp(Instant.now())
                .totalScore(totalScore)
                .level(level)
                .cpuUsage(cpuUsage)
                .memoryUsage(memoryUsage)
                .qps(qps)
                .p99Latency(p99Latency)
                .connectionPoolUsage(connectionPoolUsage)
                .build();
    }

    private double getCpuUsage() {
        try {
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
        try {
            var timer = meterRegistry.find("http.server.requests").timer();
            if (timer == null) {
                return 0;
            }
            long count = timer.count();
            return count / 60.0;
        } catch (Exception e) {
            log.debug("Failed to get QPS", e);
            return 0;
        }
    }

    private double getP99Latency() {
        try {
            var timer = meterRegistry.find("http.server.requests").timer();
            if (timer == null) {
                return 0;
            }
            return timer.percentile(0.99, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.debug("Failed to get P99 latency", e);
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

    private int calculateTotalScore(double cpu, double memory, double qps, double p99, double pool) {
        int cpuScore = calculateMetricScore(cpu, 70, 90, 30);
        int memoryScore = calculateMetricScore(memory, 75, 90, 20);
        int qpsScore = calculateMetricScore(qps, 8000, 12000, 20);
        int p99Score = calculateMetricScore(p99, 30, 50, 15);
        int poolScore = calculateMetricScore(pool, 80, 95, 15);

        return cpuScore + memoryScore + qpsScore + p99Score + poolScore;
    }

    private int calculateMetricScore(double value, double warning, double critical, int maxScore) {
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
