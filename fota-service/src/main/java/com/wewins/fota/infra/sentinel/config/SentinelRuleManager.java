package com.wewins.fota.infra.sentinel.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.wewins.fota.application.load.config.LoadControlRuntimeConfigService;
import com.wewins.fota.application.sentinel.dto.SentinelDegradeRuleDTO;
import com.wewins.fota.application.sentinel.dto.SentinelFlowRuleDTO;
import com.wewins.fota.application.sentinel.dto.SentinelRulesDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Sentinel 规则管理器。
 * <p>
 * Sentinel 规则统一从已发布的负载控制总快照中读取。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentinelRuleManager {

    private static final Map<String, Integer> CONTROL_BEHAVIOR_MAP = Map.of(
            "RATE_LIMITER", RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER,
            "WARM_UP", RuleConstant.CONTROL_BEHAVIOR_WARM_UP,
            "WARM_UP_RATE_LIMITER", RuleConstant.CONTROL_BEHAVIOR_WARM_UP_RATE_LIMITER,
            "DEFAULT", RuleConstant.CONTROL_BEHAVIOR_DEFAULT
    );

    private static final Map<String, Integer> DEGRADE_GRADE_MAP = Map.of(
            "RT", RuleConstant.DEGRADE_GRADE_RT,
            "EXCEPTION_RATIO", RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO,
            "EXCEPTION_COUNT", RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT
    );

    private final LoadControlRuntimeConfigService runtimeConfigService;

    private final AtomicReference<Long> lastLoadedVersion = new AtomicReference<>();

    @EventListener(ApplicationReadyEvent.class)
    public void initRules() {
        loadRules();
        markLoadedVersion(currentPublishedVersion());
        log.info("Sentinel rules initialized from load-control snapshot");
    }

    @Scheduled(fixedDelayString = "${app.sentinel.refresh-interval-ms:30000}",
               initialDelayString = "${app.sentinel.refresh-initial-delay-ms:30000}")
    public void refreshRules() {
        Long currentVersion = currentPublishedVersion();
        if (Objects.equals(lastLoadedVersion.get(), currentVersion)) {
            return;
        }
        loadRules();
        markLoadedVersion(currentVersion);
    }

    public void loadRules() {
        loadFlowRules();
        loadDegradeRules();
    }

    public void markLoadedVersion(Long version) {
        lastLoadedVersion.set(version);
    }

    public Optional<FlowRule> getFlowRule(String resource) {
        return FlowRuleManager.getRules().stream()
                .filter(rule -> resource.equals(rule.getResource()))
                .findFirst();
    }

    public double getFlowThreshold(String resource, double fallback) {
        return getFlowRule(resource)
                .map(FlowRule::getCount)
                .orElse(fallback);
    }

    public List<SentinelFlowRuleDTO> getStoredFlowRules() {
        return currentSentinelRules().getFlowRules();
    }

    public List<SentinelDegradeRuleDTO> getStoredDegradeRules() {
        return currentSentinelRules().getDegradeRules();
    }

    private void loadFlowRules() {
        List<FlowRule> rules = currentSentinelRules().getFlowRules().stream()
                .filter(rule -> rule.getResource() != null && !rule.getResource().isBlank())
                .filter(rule -> rule.getGrade() != null)
                .map(this::toFlowRule)
                .collect(Collectors.toList());
        FlowRuleManager.loadRules(rules);
    }

    private void loadDegradeRules() {
        List<DegradeRule> rules = currentSentinelRules().getDegradeRules().stream()
                .filter(rule -> rule.getResource() != null && !rule.getResource().isBlank())
                .filter(rule -> rule.getGrade() != null)
                .filter(rule -> rule.getTimeWindow() > 0)
                .map(this::toDegradeRule)
                .collect(Collectors.toList());
        DegradeRuleManager.loadRules(rules);
    }

    private FlowRule toFlowRule(SentinelFlowRuleDTO config) {
        FlowRule rule = new FlowRule();
        rule.setResource(config.getResource());
        rule.setGrade("QPS".equals(config.getGrade()) ? RuleConstant.FLOW_GRADE_QPS : RuleConstant.FLOW_GRADE_THREAD);
        rule.setCount(config.getCount());
        rule.setControlBehavior(toControlBehavior(config.getControlBehavior()));
        rule.setMaxQueueingTimeMs(config.getMaxQueueingTimeMs());
        return rule;
    }

    private DegradeRule toDegradeRule(SentinelDegradeRuleDTO config) {
        DegradeRule rule = new DegradeRule();
        rule.setResource(config.getResource());
        rule.setGrade(toDegradeGrade(config.getGrade()));
        rule.setCount(config.getCount());
        rule.setTimeWindow(config.getTimeWindow());
        rule.setMinRequestAmount(config.getMinRequestAmount());
        rule.setSlowRatioThreshold(config.getSlowRatioThreshold());
        return rule;
    }

    private int toControlBehavior(String behavior) {
        if (behavior == null) {
            return RuleConstant.CONTROL_BEHAVIOR_DEFAULT;
        }
        return CONTROL_BEHAVIOR_MAP.getOrDefault(behavior.toUpperCase(), RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
    }

    private int toDegradeGrade(String grade) {
        if (grade == null) {
            return RuleConstant.DEGRADE_GRADE_RT;
        }
        return DEGRADE_GRADE_MAP.getOrDefault(grade.toUpperCase(), RuleConstant.DEGRADE_GRADE_RT);
    }

    private Long currentPublishedVersion() {
        return runtimeConfigService.getPublishedConfig()
                .map(config -> config.getVersion())
                .orElse(null);
    }

    private SentinelRulesDTO currentSentinelRules() {
        return runtimeConfigService.getPublishedConfig()
                .map(config -> config.getSentinel())
                .map(rules -> SentinelRulesDTO.builder()
                        .flowRules(rules.getFlowRules() == null ? List.of() : rules.getFlowRules())
                        .degradeRules(rules.getDegradeRules() == null ? List.of() : rules.getDegradeRules())
                        .build())
                .orElseGet(() -> SentinelRulesDTO.builder()
                        .flowRules(List.of())
                        .degradeRules(List.of())
                        .build());
    }
}
