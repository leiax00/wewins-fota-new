package com.wewins.fota.infra.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.registry")
public class RegistryProperties {

    private boolean enabled = true;

    private int ttlSeconds = 90;

    private long heartbeatIntervalMs = 20000;
}
