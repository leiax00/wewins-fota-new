package com.wewins.fota.infra.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Region sync properties for main endpoint and bootstrap secret.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.main")
public class RegionMainProperties {

    private String baseUrl;

    private String bootstrapSecret;
}
