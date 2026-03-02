package com.wewins.fota.application.firmware.download.impl;

import com.wewins.fota.application.firmware.download.FirmwareDownloadProperties;
import com.wewins.fota.application.firmware.download.SignedUrlService;
import com.wewins.fota.storage.core.S3StorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * S3 预签名下载 URL 服务实现。
 * <p>
 * 使用 S3 SDK 的 presignGetObject 生成预签名 URL。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-01
 */
@Slf4j
@RequiredArgsConstructor
public class S3PresignedUrlServiceImpl implements SignedUrlService {

    private final S3StorageClient s3StorageClient;
    private final FirmwareDownloadProperties properties;

    @Override
    public String generateSignedUrl(String firmwarePath) {
        return generateSignedUrl(firmwarePath, properties.getS3Presigned().getDefaultExpireSeconds());
    }

    @Override
    public String generateSignedUrl(String firmwarePath, int expireSeconds) {
        validateParams(firmwarePath, expireSeconds);

        // 使用 S3 SDK 生成预签名 URL（不再添加自定义参数）
        Duration ttl = Duration.ofSeconds(expireSeconds);
        return s3StorageClient.getDownloadUrl(firmwarePath, ttl);
    }

    @Override
    public boolean verifySignature(String firmwarePath, long expireTime, String signature) {
        // S3 预签名 URL 的验证由 S3 服务端完成
        // 这里只检查是否过期
        long currentTime = System.currentTimeMillis() / 1000;
        if (expireTime < currentTime) {
            log.debug("预签名 URL 已过期: expireTime={}, currentTime={}", expireTime, currentTime);
            return false;
        }

        // S3 预签名 URL 的签名验证由 S3 服务端处理
        return true;
    }

    /**
     * 参数校验
     * <p>
     * 注意：policyId 和 requestId 参数保留用于接口兼容性，但不再参与 URL 生成。
     * </p>
     */
    private void validateParams(String firmwarePath, int expireSeconds) {
        if (firmwarePath == null || firmwarePath.isBlank()) {
            throw new IllegalArgumentException("firmwarePath 不能为空");
        }
        if (expireSeconds <= 0 || expireSeconds > Duration.ofDays(7).toSeconds()) {
            throw new IllegalArgumentException("expireSeconds 必须在 1-604800 秒之间（最多 7 天）");
        }
    }
}
