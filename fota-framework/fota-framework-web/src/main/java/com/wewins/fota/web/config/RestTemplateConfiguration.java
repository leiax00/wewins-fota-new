package com.wewins.fota.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置类
 * <p>
 * 配置 RestTemplate bean，用于跨区域 HTTP 调用
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-12
 */
@Configuration
public class RestTemplateConfiguration {

    /**
     * 配置 RestTemplate bean
     * <p>
     * 用于 RegionSyncService 调用主区域 API
     * </p>
     *
     * @return RestTemplate bean
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
