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
     * 规则来源
     */
    private String ruleSource;

    /**
     * 运行态配置
     */
    private RuntimeConfig runtime;

    /**
     * 运行态配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuntimeConfig {
        /**
         * 当前生效的负载控制总快照 Key
         */
        private String activeConfigKey;
    }
}
