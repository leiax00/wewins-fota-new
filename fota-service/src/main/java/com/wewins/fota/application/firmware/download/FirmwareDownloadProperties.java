package com.wewins.fota.application.firmware.download;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 固件下载配置属性。
 * <p>
 * 支持两种签名模式：
 * <ul>
 *   <li>self-signed：使用 HMAC-SHA256 自签名 URL</li>
 *   <li>s3-presigned：使用 S3 SDK 生成预签名 URL</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-01
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "app.firmware.download")
public class FirmwareDownloadProperties {

    /**
     * 签名密钥最小长度（字节）
     */
    private static final int MIN_SECRET_KEY_LENGTH = 32;

    /**
     * 签名模式
     */
    private SignMode signMode = SignMode.SELF_SIGNED;

    /**
     * RustFS/CDN 基础域名
     * <p>
     * 用于拼接完整下载 URL，例如：https://rustfs.example.com
     * </p>
     */
    private String baseUrl;

    private final SelfSigned selfSigned = new SelfSigned();
    private final S3Presigned s3Presigned = new S3Presigned();

    /**
     * 签名模式枚举
     */
    public enum SignMode {
        /**
         * 自签名模式：使用 HMAC-SHA256 生成签名 URL
         */
        SELF_SIGNED,

        /**
         * S3 预签名模式：使用 S3 SDK 的 presignGetObject 生成预签名 URL
         */
        S3_PRESIGNED
    }

    /**
     * 自签名配置
     */
    @Data
    public static class SelfSigned {

        /**
         * 签名密钥
         * <p>
         * 用于 HMAC-SHA256 签名的密钥，长度至少 32 字节
         * </p>
         */
        private String secretKey;

        /**
         * 默认过期时间（秒）
         * <p>
         * 默认 24 小时 = 86400 秒
         * </p>
         */
        private int defaultExpireSeconds = 86400;
    }

    /**
     * S3 预签名配置
     */
    @Data
    public static class S3Presigned {

        /**
         * 默认过期时间（秒）
         * <p>
         * 默认 1 小时 = 3600 秒
         * </p>
         */
        private int defaultExpireSeconds = 3600;
    }

    /**
     * 启动时验证配置
     */
    @PostConstruct
    public void validate() {
        if (signMode == SignMode.SELF_SIGNED) {
            String secretKey = selfSigned.getSecretKey();
            if (secretKey == null || secretKey.isBlank()) {
                throw new IllegalStateException(
                        "自签名密钥未配置，请设置 app.firmware.download.self-signed.secret-key");
            }

            if (secretKey.length() < MIN_SECRET_KEY_LENGTH) {
                throw new IllegalStateException(
                        "签名密钥长度不足，当前 " + secretKey.length() + " 字节，"
                                + "要求至少 " + MIN_SECRET_KEY_LENGTH + " 字节");
            }
        }

        log.info("固件下载配置验证通过: signMode={}, baseUrl={}", signMode, baseUrl);
    }
}
