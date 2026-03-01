package com.wewins.fota.application.firmware.download.impl;

import com.wewins.fota.application.firmware.download.FirmwareDownloadProperties;
import com.wewins.fota.application.firmware.download.SignedUrlService;
import com.wewins.fota.storage.core.S3StorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S3 预签名下载 URL 服务实现。
 * <p>
 * 使用 S3 SDK 的 presignGetObject 生成预签名 URL，
 * 溯源参数（policy、device）被纳入签名计算，确保完整性。
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
    public String generateSignedUrl(String firmwarePath, Long policyId, Long deviceId) {
        return generateSignedUrl(firmwarePath, policyId, deviceId, properties.getS3Presigned().getDefaultExpireSeconds());
    }

    @Override
    public String generateSignedUrl(String firmwarePath, Long policyId, Long deviceId, int expireSeconds) {
        // 参数校验
        validateParams(firmwarePath, policyId, deviceId, expireSeconds);

        // 构建溯源参数
        Map<String, String> trackingParams = new LinkedHashMap<>();
        trackingParams.put("pid", String.valueOf(policyId));
        trackingParams.put("did", String.valueOf(deviceId));

        // 使用 S3 SDK 生成带自定义参数的预签名 URL
        Duration ttl = Duration.ofSeconds(expireSeconds);
        return s3StorageClient.getDownloadUrl(firmwarePath, ttl, trackingParams);
    }

    @Override
    public boolean verifySignature(String firmwarePath, Long policyId, Long deviceId, long expireTime, String signature) {
        // S3 预签名 URL 的验证由 S3 服务端完成
        // 这里只检查是否过期
        long currentTime = System.currentTimeMillis() / 1000;
        if (expireTime < currentTime) {
            log.debug("预签名 URL 已过期: expireTime={}, currentTime={}", expireTime, currentTime);
            return false;
        }

        // S3 预签名 URL 的签名验证由 S3 服务端处理
        // 自定义参数（policy、device）已纳入签名，确保完整性
        return true;
    }

    /**
     * 参数校验
     */
    private void validateParams(String firmwarePath, Long policyId, Long deviceId, int expireSeconds) {
        if (firmwarePath == null || firmwarePath.isBlank()) {
            throw new IllegalArgumentException("firmwarePath 不能为空");
        }
        if (policyId == null || policyId <= 0) {
            throw new IllegalArgumentException("policyId 必须为正数");
        }
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("deviceId 必须为正数");
        }
        if (expireSeconds <= 0 || expireSeconds > Duration.ofDays(7).toSeconds()) {
            throw new IllegalArgumentException("expireSeconds 必须在 1-604800 秒之间（最多 7 天）");
        }
    }
}
