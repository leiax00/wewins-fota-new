package com.wewins.fota.application.firmware.download;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 签名下载 URL 配置属性
 * <p>
 * 配置固件下载 URL 的签名相关参数
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "app.firmware.signed-url")
public class SignedUrlProperties {

    /**
     * 签名密钥最小长度（字节）
     * <p>
     * HMAC-SHA256 输出为 256 位（32 字节），密钥长度应与之匹配以保证安全性
     * </p>
     */
    private static final int MIN_SECRET_KEY_LENGTH = 32;

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

    /**
     * CDN 基础 URL
     * <p>
     * 用于生成完整的 HTTPS 下载地址
     * 例如: https://cdn.example.com
     * </p>
     */
    private String cdnBaseUrl;

    /**
     * 是否启用签名验证
     * <p>
     * true: 生成的 URL 包含签名参数
     * false: 直接返回原始下载 URL（仅用于测试环境）
     * </p>
     */
    private boolean enabled = true;

    /**
     * 启动时验证配置
     * <p>
     * 确保签名密钥满足安全要求
     * </p>
     *
     * @throws IllegalStateException 如果配置无效
     */
    @PostConstruct
    public void validate() {
        if (!enabled) {
            log.warn("签名 URL 功能已禁用，仅适用于测试环境");
            return;
        }

        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "签名密钥未配置，请设置 app.firmware.signed-url.secret-key");
        }

        if (secretKey.length() < MIN_SECRET_KEY_LENGTH) {
            throw new IllegalStateException(
                    "签名密钥长度不足，当前 " + secretKey.length() + " 字节，"
                            + "要求至少 " + MIN_SECRET_KEY_LENGTH + " 字节");
        }

        log.info("签名 URL 配置验证通过: expireSeconds={}, cdnBaseUrl={}",
                defaultExpireSeconds, cdnBaseUrl);
    }
}
