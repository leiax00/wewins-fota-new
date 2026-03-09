package com.wewins.fota.infra.sentinel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.sentinel")
public class SentinelProperties {

    private boolean enabled = true;

    private RuleSource ruleSource = RuleSource.HYBRID;

    private List<FlowRuleConfig> flowRules = new ArrayList<>();

    private List<DegradeRuleConfig> degradeRules = new ArrayList<>();

    private RedisConfig redis = new RedisConfig();

    public enum RuleSource {
        CONFIG,
        REDIS,
        HYBRID
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

    @Data
    public static class RedisConfig {
        private String flowRulesKey = "fota:sentinel:flow:rules";
        private String degradeRulesKey = "fota:sentinel:degrade:rules";
    }
}
