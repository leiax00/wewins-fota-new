package com.wewins.fota.application.firmware.download;

import lombok.Data;
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
@Data
@ConfigurationProperties(prefix = "app.firmware.signed-url")
public class SignedUrlProperties {

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
}
