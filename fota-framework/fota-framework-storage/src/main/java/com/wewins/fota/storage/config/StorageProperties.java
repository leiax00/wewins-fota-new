package com.wewins.fota.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object storage properties.
 */
@Data
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private long defaultTtlSeconds = 900;

    private final Local local = new Local();

    private final S3 s3 = new S3();

    @Data
    public static class Local {
        private String baseDir = "data/storage";
    }

    @Data
    public static class S3 {
        private boolean enabled = false;
        private String endpoint;
        private String region = "us-east-1";
        private String bucket;
        private String accessKey;
        private String secretKey;
        private boolean pathStyleAccessEnabled = true;
    }
}
