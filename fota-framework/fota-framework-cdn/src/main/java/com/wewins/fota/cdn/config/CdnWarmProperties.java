package com.wewins.fota.cdn.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * CDN warm-up configuration properties.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.cdn.warm")
    public class CdnWarmProperties {

    /**
     * Whether CDN warm-up is enabled.
     */
    private boolean enabled = true;

    /**
     * Request timeout in seconds.
     */
    @Min(value = 1, message = "timeoutSeconds must be at least 1")
    @Max(value = 3600, message = "timeoutSeconds must not exceed 3600")
    private int timeoutSeconds = 300;

    /**
     * Chunk size in MB for large files (only used when file size > 50MB).
     */
    @Min(value = 1, message = "chunkSizeMb must be at least 1")
    @Max(value = 100, message = "chunkSizeMb must not exceed 100")
    private int chunkSizeMb = 20;

    /**
     * Concurrency level for chunk requests.
     */
    @Min(value = 1, message = "chunkConcurrency must be at least 1")
    @Max(value = 10, message = "chunkConcurrency must not exceed 10")
    private int chunkConcurrency = 3;

    /**
     * Number of retry attempts on failure.
     */
    @Min(value = 0, message = "retryCount must be at least 0")
    @Max(value = 5, message = "retryCount must not exceed 5")
    private int retryCount = 2;
}
