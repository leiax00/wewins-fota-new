package com.wewins.fota.infra.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.monitoring")
public class MonitoringProperties {

    private Prometheus prometheus = new Prometheus();

    @Data
    public static class Prometheus {

        private boolean enabled = true;

        private String url = "http://localhost:19090";
    }
}
