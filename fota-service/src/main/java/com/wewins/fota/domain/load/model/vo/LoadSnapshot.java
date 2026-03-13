package com.wewins.fota.domain.load.model.vo;

import com.wewins.fota.domain.load.model.enums.LoadLevel;

import java.time.Instant;

public record LoadSnapshot(
        Instant timestamp,
        int totalScore,
        LoadLevel level,
        double cpuUsage,
        double memoryUsage,
        double connectionPoolUsage,
        double hostCpuUsage,
        double hostMemoryUsage,
        double networkInBytes,
        double networkOutBytes,
        double checkQps,
        double reportQps,
        double checkP50Latency,
        double checkP99Latency,
        double reportP50Latency,
        double reportP99Latency
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
        private double connectionPoolUsage;
        private double hostCpuUsage;
        private double hostMemoryUsage;
        private double networkInBytes;
        private double networkOutBytes;
        private double checkQps;
        private double reportQps;
        private double checkP50Latency;
        private double checkP99Latency;
        private double reportP50Latency;
        private double reportP99Latency;

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

        public LoadSnapshotBuilder connectionPoolUsage(double connectionPoolUsage) {
            this.connectionPoolUsage = connectionPoolUsage;
            return this;
        }

        public LoadSnapshotBuilder hostCpuUsage(double hostCpuUsage) {
            this.hostCpuUsage = hostCpuUsage;
            return this;
        }

        public LoadSnapshotBuilder hostMemoryUsage(double hostMemoryUsage) {
            this.hostMemoryUsage = hostMemoryUsage;
            return this;
        }

        public LoadSnapshotBuilder networkInBytes(double networkInBytes) {
            this.networkInBytes = networkInBytes;
            return this;
        }

        public LoadSnapshotBuilder networkOutBytes(double networkOutBytes) {
            this.networkOutBytes = networkOutBytes;
            return this;
        }

        public LoadSnapshotBuilder checkQps(double checkQps) {
            this.checkQps = checkQps;
            return this;
        }

        public LoadSnapshotBuilder reportQps(double reportQps) {
            this.reportQps = reportQps;
            return this;
        }

        public LoadSnapshotBuilder checkP50Latency(double checkP50Latency) {
            this.checkP50Latency = checkP50Latency;
            return this;
        }

        public LoadSnapshotBuilder checkP99Latency(double checkP99Latency) {
            this.checkP99Latency = checkP99Latency;
            return this;
        }

        public LoadSnapshotBuilder reportP50Latency(double reportP50Latency) {
            this.reportP50Latency = reportP50Latency;
            return this;
        }

        public LoadSnapshotBuilder reportP99Latency(double reportP99Latency) {
            this.reportP99Latency = reportP99Latency;
            return this;
        }

        public LoadSnapshot build() {
            if (level == null) {
                level = LoadLevel.fromScore(totalScore);
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
            return new LoadSnapshot(
                    timestamp,
                    totalScore,
                    level,
                    cpuUsage,
                    memoryUsage,
                    connectionPoolUsage,
                    hostCpuUsage,
                    hostMemoryUsage,
                    networkInBytes,
                    networkOutBytes,
                    checkQps,
                    reportQps,
                    checkP50Latency,
                    checkP99Latency,
                    reportP50Latency,
                    reportP99Latency
            );
        }
    }
}
