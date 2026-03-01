package com.wewins.fota.application.firmware.download.config;

import com.wewins.fota.application.firmware.download.FirmwareDownloadProperties;
import com.wewins.fota.application.firmware.download.SignedUrlService;
import com.wewins.fota.application.firmware.download.impl.S3PresignedUrlServiceImpl;
import com.wewins.fota.application.firmware.download.impl.SelfSignedUrlServiceImpl;
import com.wewins.fota.storage.core.S3StorageClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 签名 URL 服务配置类。
 * <p>
 * 根据 app.firmware.download.sign-mode 配置选择使用哪种签名实现：
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
@Configuration
@EnableConfigurationProperties(FirmwareDownloadProperties.class)
public class SignedUrlServiceConfiguration {

    /**
     * 自签名 URL 服务
     * <p>
     * 当 sign-mode=self-signed 或未配置时使用
     * </p>
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.firmware.download",
            name = "sign-mode",
            havingValue = "self-signed",
            matchIfMissing = true
    )
    @ConditionalOnMissingBean(SignedUrlService.class)
    public SignedUrlService selfSignedUrlService(FirmwareDownloadProperties properties) {
        log.info("初始化自签名 URL 服务");
        return new SelfSignedUrlServiceImpl(properties);
    }

    /**
     * S3 预签名 URL 服务
     * <p>
     * 当 sign-mode=s3-presigned 且 S3 已启用时使用
     * </p>
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.firmware.download",
            name = "sign-mode",
            havingValue = "s3-presigned"
    )
    @ConditionalOnMissingBean(SignedUrlService.class)
    public SignedUrlService s3PresignedUrlService(
            S3StorageClient s3StorageClient,
            FirmwareDownloadProperties properties) {
        log.info("初始化 S3 预签名 URL 服务");
        return new S3PresignedUrlServiceImpl(s3StorageClient, properties);
    }
}
