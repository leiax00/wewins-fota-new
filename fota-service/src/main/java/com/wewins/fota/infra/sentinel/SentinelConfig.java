package com.wewins.fota.infra.sentinel;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 配置类
 */
@Slf4j
@Configuration
public class SentinelConfig {

    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void initRules() {
        initFlowRules();
        initDegradeRules();
        log.info("Sentinel rules initialized");
    }

    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule checkApiRule = new FlowRule();
        checkApiRule.setResource("upgrade:check");
        checkApiRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        checkApiRule.setCount(2500);
        checkApiRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER);
        checkApiRule.setMaxQueueingTimeMs(50);
        rules.add(checkApiRule);

        FlowRule reportApiRule = new FlowRule();
        reportApiRule.setResource("upgrade:report");
        reportApiRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        reportApiRule.setCount(5000);
        rules.add(reportApiRule);

        FlowRuleManager.loadRules(rules);
    }

    private void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        DegradeRule degradeRule = new DegradeRule("upgrade:check");
        degradeRule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        degradeRule.setCount(50);
        degradeRule.setTimeWindow(30);
        degradeRule.setMinRequestAmount(100);
        degradeRule.setSlowRatioThreshold(0.5);
        rules.add(degradeRule);

        DegradeRuleManager.loadRules(rules);
    }
}
