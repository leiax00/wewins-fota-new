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
        /**
         * URL 访问模式
         * <p>
         * 控制 URL 构造时如何处理 bucket 名称：
         * <ul>
         *   <li>PATH_STYLE: http://endpoint/bucket/key</li>
         *   <li>LIKE_R2: http://r2-domain/key（域名已绑定到桶）</li>
         *   <li>VIRTUAL_HOSTED: http://bucket.endpoint/key</li>
         * </ul>
         * </p>
         */
        private UrlAccessType urlAccessType = UrlAccessType.PATH_STYLE;

        /**
         * 判断是否为 Path Style 访问模式
         * <p>
         * 不是 VIRTUAL_HOSTED 的都是 path style
         * </p>
         */
        public boolean isPathStyle() {
            return urlAccessType == UrlAccessType.PATH_STYLE ||
                    urlAccessType == UrlAccessType.LIKE_R2;
        }
    }

    /**
     * S3 URL 访问模式枚举
     */
    public enum UrlAccessType {
        /**
         * Path Style 访问模式
         * <p>
         * URL 格式：http://endpoint/bucket/key
         * </p>
         * <p>
         * 适用于：MinIO 等兼容 S3 的对象存储服务
         * </p>
         */
        PATH_STYLE,

        /**
         * R2 访问模式
         * <p>
         * URL 格式：http://r2-domain/key
         * </p>
         * <p>
         * 适用于：Cloudflare R2 等支持自定义域名绑定的服务
         * 域名已直接绑定到特定桶，URL 中无需包含桶名
         * </p>
         */
        LIKE_R2,

        /**
         * Virtual-Hosted Style 访问模式
         * <p>
         * URL 格式：http://bucket.endpoint/key
         * </p>
         * <p>
         * 适用于：AWS S3 等标准 S3 服务
         * </p>
         */
        VIRTUAL_HOSTED
    }
}
