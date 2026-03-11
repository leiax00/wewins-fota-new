package com.wewins.fota.cache.cluster;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

/**
 * Cluster properties used by registry and leader services.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class ClusterProperties {

    private String mode = "main";

    @NestedConfigurationProperty
    private Node node = new Node();

    @NestedConfigurationProperty
    private ServiceRegistry registry = new ServiceRegistry();

    @NestedConfigurationProperty
    private Leader leader = new Leader();

    @Data
    public static class Node {
        private String code;
        private String name;
        private String baseUrl;
        private String timeZone;
    }

    @Data
    public static class ServiceRegistry {
        private boolean enabled = true;
        private long ttlSeconds = 90;
    }

    @Data
    public static class Leader {
        private boolean enabled = true;
        private long ttlSeconds = 30;
    }
}
