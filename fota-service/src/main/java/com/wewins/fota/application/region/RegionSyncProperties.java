package com.wewins.fota.application.region;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

/**
 * Properties used by region config sync flow.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class RegionSyncProperties {

    private String mode = "main";

    @NestedConfigurationProperty
    private Node node = new Node();

    @NestedConfigurationProperty
    private Main main = new Main();

    @Data
    public static class Node {
        private String code;
        private String name;
        private String baseUrl;
        private String timeZone;
    }

    @Data
    public static class Main {
        private String baseUrl;
        private String bootstrapSecret;
    }
}
