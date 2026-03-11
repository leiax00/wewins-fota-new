package com.wewins.fota.infra.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一注入从 app.node.code 派生出的通用监控标签。
 */
@Configuration
public class MetricsCommonTagsConfiguration {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> meterRegistryCustomizer(NodeIdentity nodeIdentity) {
        return registry -> registry.config()
                .commonTags(
                        "region", nodeIdentity.regionCode(),
                        "host", nodeIdentity.hostCode(),
                        "instance", nodeIdentity.monitoringInstanceLabel(),
                        "instance_id", nodeIdentity.nodeId()
                );
    }
}
