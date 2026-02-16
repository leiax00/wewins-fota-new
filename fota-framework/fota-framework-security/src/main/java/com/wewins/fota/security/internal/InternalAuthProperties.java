package com.wewins.fota.security.internal;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Internal API authentication properties.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.internal-auth")
public class InternalAuthProperties {

    private boolean enabled = true;

    private long skewSeconds = 300;

    private long nonceTtlSeconds = 300;
}
