package com.wewins.fota.mq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Common MQ properties for framework-level capabilities.
 */
@Data
@ConfigurationProperties(prefix = "app.mq")
public class MqProperties {

    private boolean enabled = false;

    private final Publish publish = new Publish();

    @Data
    public static class Publish {
        private boolean mandatory = false;
    }
}
