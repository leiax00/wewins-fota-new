package com.wewins.fota.domain.load.model.vo;

import com.wewins.fota.domain.load.model.enums.LoadLevel;

import java.time.Instant;

/**
 * 系统负载快照
 * <p>
 * 记录某一时刻的系统负载状态，用于负载评估和趋势分析
 * </p>
 *
 * @param timestamp          快照时间戳
 * @param totalScore         综合评分 (0-100)
 * @param level              负载级别
 * @param cpuUsage           CPU 使用率 %
 * @param memoryUsage        JVM 堆内存使用率 %
 * @param qps                当前 QPS
 * @param p99Latency         P99 延迟 ms
 * @param connectionPoolUsage 连接池使用率 %
 */
public record LoadSnapshot(
        Instant timestamp,
        int totalScore,
        LoadLevel level,
        double cpuUsage,
        double memoryUsage,
        double qps,
        double p99Latency,
        double connectionPoolUsage
) {
    public LoadSnapshot {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (totalScore < 0 || totalScore > 100) {
            throw new IllegalArgumentException("totalScore must be between 0 and 100");
        }
        if (level == null) {
            level = LoadLevel.fromScore(totalScore);
        }
    }

    public static LoadSnapshotBuilder builder() {
        return new LoadSnapshotBuilder();
    }

    public boolean isOverloaded() {
        return level.isOverloaded();
    }

    public boolean isCritical() {
        return level.isCritical();
    }

    public static class LoadSnapshotBuilder {
        private Instant timestamp;
        private int totalScore;
        private LoadLevel level;
        private double cpuUsage;
        private double memoryUsage;
        private double qps;
        private double p99Latency;
        private double connectionPoolUsage;

        public LoadSnapshotBuilder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public LoadSnapshotBuilder totalScore(int totalScore) {
            this.totalScore = totalScore;
            return this;
        }

        public LoadSnapshotBuilder level(LoadLevel level) {
            this.level = level;
            return this;
        }

        public LoadSnapshotBuilder cpuUsage(double cpuUsage) {
            this.cpuUsage = cpuUsage;
            return this;
        }

        public LoadSnapshotBuilder memoryUsage(double memoryUsage) {
            this.memoryUsage = memoryUsage;
            return this;
        }

        public LoadSnapshotBuilder qps(double qps) {
            this.qps = qps;
            return this;
        }

        public LoadSnapshotBuilder p99Latency(double p99Latency) {
            this.p99Latency = p99Latency;
            return this;
        }

        public LoadSnapshotBuilder connectionPoolUsage(double connectionPoolUsage) {
            this.connectionPoolUsage = connectionPoolUsage;
            return this;
        }

        public LoadSnapshot build() {
            if (level == null) {
                level = LoadLevel.fromScore(totalScore);
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
            return new LoadSnapshot(timestamp, totalScore, level, cpuUsage, memoryUsage, qps, p99Latency, connectionPoolUsage);
        }
    }
}
