package com.wewins.fota.application.sentinel;

import com.wewins.fota.application.sentinel.dto.SentinelConfigDTO;
import com.wewins.fota.application.sentinel.dto.SentinelDegradeRuleDTO;
import com.wewins.fota.application.sentinel.dto.SentinelFlowRuleDTO;
import com.wewins.fota.application.sentinel.dto.SentinelRulesDTO;
import com.wewins.fota.infra.sentinel.config.SentinelProperties;
import com.wewins.fota.infra.sentinel.config.SentinelRuleManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SentinelRuleAppService {

    private final SentinelProperties properties;
    private final SentinelRuleManager ruleManager;

    public SentinelConfigDTO getConfig() {
        return SentinelConfigDTO.builder()
                .enabled(properties.isEnabled())
                .ruleSource(properties.getRuleSource().name())
                .redis(SentinelConfigDTO.RedisConfig.builder()
                        .flowRulesKey(properties.getRedis().getFlowRulesKey())
                        .degradeRulesKey(properties.getRedis().getDegradeRulesKey())
                        .build())
                .build();
    }

    public SentinelRulesDTO getRules() {
        List<SentinelFlowRuleDTO> flowRules = properties.getFlowRules().stream()
                .filter(SentinelProperties.FlowRuleConfig::isEnabled)
                .map(rule -> SentinelFlowRuleDTO.builder()
                        .resource(rule.getResource())
                        .grade(rule.getGrade())
                        .count(rule.getCount())
                        .controlBehavior(rule.getControlBehavior())
                        .maxQueueingTimeMs(rule.getMaxQueueingTimeMs())
                        .build())
                .toList();

        List<SentinelDegradeRuleDTO> degradeRules = properties.getDegradeRules().stream()
                .filter(SentinelProperties.DegradeRuleConfig::isEnabled)
                .map(rule -> SentinelDegradeRuleDTO.builder()
                        .resource(rule.getResource())
                        .grade(rule.getGrade())
                        .count(rule.getCount())
                        .timeWindow(rule.getTimeWindow())
                        .minRequestAmount(rule.getMinRequestAmount())
                        .slowRatioThreshold(rule.getSlowRatioThreshold())
                        .build())
                .toList();

        return SentinelRulesDTO.builder()
                .flowRules(flowRules)
                .degradeRules(degradeRules)
                .build();
    }

    public void updateFlowRules(List<SentinelFlowRuleDTO> rules) {
        List<SentinelProperties.FlowRuleConfig> propertyRules = rules.stream()
                .map(rule -> {
                    SentinelProperties.FlowRuleConfig config = new SentinelProperties.FlowRuleConfig();
                    config.setResource(rule.getResource());
                    config.setGrade(rule.getGrade());
                    config.setCount(rule.getCount());
                    config.setControlBehavior(rule.getControlBehavior());
                    config.setMaxQueueingTimeMs(rule.getMaxQueueingTimeMs());
                    return config;
                })
                .toList();
        ruleManager.updateFlowRulesToRedis(propertyRules);
    }

    public void updateDegradeRules(List<SentinelDegradeRuleDTO> rules) {
        List<SentinelProperties.DegradeRuleConfig> propertyRules = rules.stream()
                .map(rule -> {
                    SentinelProperties.DegradeRuleConfig config = new SentinelProperties.DegradeRuleConfig();
                    config.setResource(rule.getResource());
                    config.setGrade(rule.getGrade());
                    config.setCount(rule.getCount());
                    config.setTimeWindow(rule.getTimeWindow());
                    config.setMinRequestAmount(rule.getMinRequestAmount());
                    config.setSlowRatioThreshold(rule.getSlowRatioThreshold());
                    return config;
                })
                .toList();
        ruleManager.updateDegradeRulesToRedis(propertyRules);
    }

    public void refreshRules() {
        ruleManager.loadRules();
    }
}
