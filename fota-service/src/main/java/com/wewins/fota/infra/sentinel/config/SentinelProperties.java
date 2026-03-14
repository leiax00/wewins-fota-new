package com.wewins.fota.infra.sentinel.config;

import lombok.Data;

public final class SentinelProperties {

    private SentinelProperties() {
    }

    @Data
    public static class FlowRuleConfig {
        private String resource;
        private String grade = "QPS";
        private int count = 2500;
        private String controlBehavior = "RATE_LIMITER";
        private int maxQueueingTimeMs = 50;
        private boolean enabled = true;
    }

    @Data
    public static class DegradeRuleConfig {
        private String resource;
        private String grade = "RT";
        private int count = 50;
        private int timeWindow = 30;
        private int minRequestAmount = 100;
        private double slowRatioThreshold = 0.5;
        private boolean enabled = true;
    }

}
