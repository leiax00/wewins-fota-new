package com.wewins.fota.infra.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.leader")
public class LeaderProperties {

    private boolean enabled = true;

    private int ttlSeconds = 30;

    private long renewIntervalMs = 10000;
}
