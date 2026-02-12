package com.wewins.fota.infra.registry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 注册中心配置
 *
 * @author FOTA Team
 * @since 2026-02-12
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.registry.enabled", havingValue = "true", matchIfMissing = true)
public class NodeRegistryConfiguration {
}
