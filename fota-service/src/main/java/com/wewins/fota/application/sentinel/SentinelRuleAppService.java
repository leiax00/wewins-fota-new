package com.wewins.fota.application.sentinel;

import com.wewins.fota.application.sentinel.dto.SentinelConfigDTO;
import com.wewins.fota.application.sentinel.dto.SentinelDegradeRuleDTO;
import com.wewins.fota.application.sentinel.dto.SentinelFlowRuleDTO;
import com.wewins.fota.application.sentinel.dto.SentinelRulesDTO;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.infra.sentinel.config.SentinelRuleManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SentinelRuleAppService {

    private final SentinelRuleManager ruleManager;

    public SentinelConfigDTO getConfig() {
        return SentinelConfigDTO.builder()
                .enabled(true)
                .ruleSource("LOAD_CONTROL_SNAPSHOT")
                .runtime(SentinelConfigDTO.RuntimeConfig.builder()
                        .activeConfigKey(RedisKeyConstants.LOAD_CONTROL_ACTIVE_CONFIG_KEY)
                        .build())
                .build();
    }

    public SentinelRulesDTO getRules() {
        List<SentinelFlowRuleDTO> flowRules = ruleManager.getStoredFlowRules().stream()
                .map(rule -> SentinelFlowRuleDTO.builder()
                        .resource(rule.getResource())
                        .grade(rule.getGrade())
                        .count(rule.getCount())
                        .controlBehavior(rule.getControlBehavior())
                        .maxQueueingTimeMs(rule.getMaxQueueingTimeMs())
                        .build())
                .toList();

        List<SentinelDegradeRuleDTO> degradeRules = ruleManager.getStoredDegradeRules().stream()
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
}
