package com.wewins.fota.application.sentinel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sentinel 配置信息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentinelConfigDTO {

    /**
     * Sentinel 是否启用
     */
    private boolean enabled;

    /**
     * 规则来源：CONFIG、REDIS、HYBRID
     */
    private String ruleSource;

    /**
     * Redis 配置
     */
    private RedisConfig redis;

    /**
     * Redis 配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedisConfig {
        /**
         * 流控规则 Redis Key
         */
        private String flowRulesKey;

        /**
         * 降级规则 Redis Key
         */
        private String degradeRulesKey;
    }
}
