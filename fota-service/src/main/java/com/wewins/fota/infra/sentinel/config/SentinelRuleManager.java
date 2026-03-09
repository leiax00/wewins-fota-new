package com.wewins.fota.infra.sentinel.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sentinel 规则管理器
 * <p>
 * 支持从配置文件和 Redis 加载流控和降级规则
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentinelRuleManager {

    private final SentinelProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 控制行为映射表
     */
    private static final Map<String, Integer> CONTROL_BEHAVIOR_MAP = Map.of(
            "RATE_LIMITER", RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER,
            "WARM_UP", RuleConstant.CONTROL_BEHAVIOR_WARM_UP,
            "WARM_UP_RATE_LIMITER", RuleConstant.CONTROL_BEHAVIOR_WARM_UP_RATE_LIMITER,
            "DEFAULT", RuleConstant.CONTROL_BEHAVIOR_DEFAULT
    );

    /**
     * 降级策略映射表
     */
    private static final Map<String, Integer> DEGRADE_GRADE_MAP = Map.of(
            "RT", RuleConstant.DEGRADE_GRADE_RT,
            "EXCEPTION_RATIO", RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO,
            "EXCEPTION_COUNT", RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT
    );

    /**
     * 应用启动时初始化规则
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initRules() {
        if (!properties.isEnabled()) {
            log.info("Sentinel is disabled, skipping rule initialization");
            return;
        }
        loadRules();
        log.info("Sentinel rules initialized: source={}", properties.getRuleSource());
    }

    /**
     * 定时刷新规则（从 Redis）
     */
    @Scheduled(fixedDelayString = "${app.sentinel.refresh-interval-ms:30000}",
               initialDelayString = "${app.sentinel.refresh-initial-delay-ms:30000}")
    public void refreshRules() {
        if (!properties.isEnabled()) {
            return;
        }
        if (properties.getRuleSource() == SentinelProperties.RuleSource.CONFIG) {
            return;
        }
        loadRules();
    }

    /**
     * 加载流控和降级规则
     */
    public void loadRules() {
        loadFlowRules();
        loadDegradeRules();
    }

    /**
     * 更新流控规则到 Redis
     */
    public void updateFlowRulesToRedis(List<SentinelProperties.FlowRuleConfig> rules) {
        if (rules == null) {
            log.warn("Attempted to update null flow rules, skipping");
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(rules);
            String key = properties.getRedis().getFlowRulesKey();
            redisTemplate.opsForValue().set(key, json);
            loadRules();
            log.info("Flow rules updated to Redis: {} rules", rules.size());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize flow rules", e);
            throw new com.wewins.fota.common.exception.BizException(
                    com.wewins.fota.common.exception.ErrorCode.INTERNAL_ERROR,
                    "流控规则序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新降级规则到 Redis
     */
    public void updateDegradeRulesToRedis(List<SentinelProperties.DegradeRuleConfig> rules) {
        if (rules == null) {
            log.warn("Attempted to update null degrade rules, skipping");
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(rules);
            String key = properties.getRedis().getDegradeRulesKey();
            redisTemplate.opsForValue().set(key, json);
            loadRules();
            log.info("Degrade rules updated to Redis: {} rules", rules.size());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize degrade rules", e);
            throw new com.wewins.fota.common.exception.BizException(
                    com.wewins.fota.common.exception.ErrorCode.INTERNAL_ERROR,
                    "降级规则序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载流控规则
     */
    private void loadFlowRules() {
        List<FlowRule> configRules = buildFlowRulesFromConfig();
        List<FlowRule> redisRules = loadFlowRulesFromRedis();

        List<FlowRule> finalRules;
        if (properties.getRuleSource() == SentinelProperties.RuleSource.REDIS) {
            finalRules = redisRules.isEmpty() ? configRules : redisRules;
        } else {
            finalRules = mergeFlowRules(configRules, redisRules);
        }

        FlowRuleManager.loadRules(finalRules);
        log.info("Loaded {} flow rules, source: {}", finalRules.size(), properties.getRuleSource());
    }

    /**
     * 加载降级规则
     */
    private void loadDegradeRules() {
        List<DegradeRule> configRules = buildDegradeRulesFromConfig();
        List<DegradeRule> redisRules = loadDegradeRulesFromRedis();

        List<DegradeRule> finalRules;
        if (properties.getRuleSource() == SentinelProperties.RuleSource.REDIS) {
            finalRules = redisRules.isEmpty() ? configRules : redisRules;
        } else {
            finalRules = mergeDegradeRules(configRules, redisRules);
        }

        DegradeRuleManager.loadRules(finalRules);
        log.info("Loaded {} degrade rules, source: {}", finalRules.size(), properties.getRuleSource());
    }

    /**
     * 从配置构建流控规则
     */
    private List<FlowRule> buildFlowRulesFromConfig() {
        return properties.getFlowRules().stream()
                .filter(SentinelProperties.FlowRuleConfig::isEnabled)
                .map(this::toFlowRule)
                .collect(Collectors.toList());
    }

    /**
     * 转换配置为流控规则
     */
    private FlowRule toFlowRule(SentinelProperties.FlowRuleConfig config) {
        FlowRule rule = new FlowRule();
        rule.setResource(config.getResource());
        rule.setGrade("QPS".equals(config.getGrade()) ? RuleConstant.FLOW_GRADE_QPS : RuleConstant.FLOW_GRADE_THREAD);
        rule.setCount(config.getCount());
        rule.setControlBehavior(toControlBehavior(config.getControlBehavior()));
        rule.setMaxQueueingTimeMs(config.getMaxQueueingTimeMs());
        return rule;
    }

    /**
     * 转换控制行为字符串为常量
     */
    private int toControlBehavior(String behavior) {
        if (behavior == null) {
            return RuleConstant.CONTROL_BEHAVIOR_DEFAULT;
        }
        return CONTROL_BEHAVIOR_MAP.getOrDefault(behavior.toUpperCase(), RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
    }

    /**
     * 从 Redis 加载流控规则
     */
    private List<FlowRule> loadFlowRulesFromRedis() {
        String key = properties.getRedis().getFlowRulesKey();
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            log.debug("No flow rules in Redis, using config defaults");
            return List.of();
        }
        try {
            List<SentinelProperties.FlowRuleConfig> configs = objectMapper.readValue(json, new TypeReference<>() {});
            return configs.stream().map(this::toFlowRule).collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("Failed to load flow rules from Redis, using config defaults: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 合并流控规则（Redis 规则优先）
     */
    private List<FlowRule> mergeFlowRules(List<FlowRule> configRules, List<FlowRule> redisRules) {
        if (redisRules.isEmpty()) {
            return configRules;
        }
        Map<String, FlowRule> redisRuleMap = redisRules.stream()
                .collect(Collectors.toUnmodifiableMap(FlowRule::getResource, r -> r, (a, b) -> a));

        return configRules.stream()
                .map(configRule -> {
                    FlowRule redisRule = redisRuleMap.get(configRule.getResource());
                    return redisRule != null ? redisRule : configRule;
                })
                .collect(Collectors.toList());
    }

    /**
     * 从配置构建降级规则
     */
    private List<DegradeRule> buildDegradeRulesFromConfig() {
        return properties.getDegradeRules().stream()
                .filter(SentinelProperties.DegradeRuleConfig::isEnabled)
                .map(this::toDegradeRule)
                .collect(Collectors.toList());
    }

    /**
     * 转换配置为降级规则
     */
    private DegradeRule toDegradeRule(SentinelProperties.DegradeRuleConfig config) {
        DegradeRule rule = new DegradeRule();
        rule.setResource(config.getResource());
        rule.setGrade(toDegradeGrade(config.getGrade()));
        rule.setCount(config.getCount());
        rule.setTimeWindow(config.getTimeWindow());
        rule.setMinRequestAmount(config.getMinRequestAmount());
        rule.setSlowRatioThreshold(config.getSlowRatioThreshold());
        return rule;
    }

    /**
     * 转换降级策略字符串为常量
     */
    private int toDegradeGrade(String grade) {
        if (grade == null) {
            return RuleConstant.DEGRADE_GRADE_RT;
        }
        return DEGRADE_GRADE_MAP.getOrDefault(grade.toUpperCase(), RuleConstant.DEGRADE_GRADE_RT);
    }

    /**
     * 从 Redis 加载降级规则
     */
    private List<DegradeRule> loadDegradeRulesFromRedis() {
        String key = properties.getRedis().getDegradeRulesKey();
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            log.debug("No degrade rules in Redis, using config defaults");
            return List.of();
        }
        try {
            List<SentinelProperties.DegradeRuleConfig> configs = objectMapper.readValue(json, new TypeReference<>() {});
            return configs.stream().map(this::toDegradeRule).collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("Failed to load degrade rules from Redis, using config defaults: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 合并降级规则（Redis 规则优先）
     */
    private List<DegradeRule> mergeDegradeRules(List<DegradeRule> configRules, List<DegradeRule> redisRules) {
        if (redisRules.isEmpty()) {
            return configRules;
        }
        Map<String, DegradeRule> redisRuleMap = redisRules.stream()
                .collect(Collectors.toUnmodifiableMap(DegradeRule::getResource, r -> r, (a, b) -> a));

        return configRules.stream()
                .map(configRule -> {
                    DegradeRule redisRule = redisRuleMap.get(configRule.getResource());
                    return redisRule != null ? redisRule : configRule;
                })
                .collect(Collectors.toList());
    }

}
