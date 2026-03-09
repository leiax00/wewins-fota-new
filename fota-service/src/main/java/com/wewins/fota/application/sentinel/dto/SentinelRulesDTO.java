package com.wewins.fota.application.sentinel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Sentinel 规则集合 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentinelRulesDTO {

    /**
     * 流控规则列表
     */
    private List<SentinelFlowRuleDTO> flowRules;

    /**
     * 降级规则列表
     */
    private List<SentinelDegradeRuleDTO> degradeRules;
}
