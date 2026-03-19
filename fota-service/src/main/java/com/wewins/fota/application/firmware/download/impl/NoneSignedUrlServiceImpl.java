package com.wewins.fota.application.firmware.download.impl;

import com.wewins.fota.application.firmware.download.FirmwareDownloadProperties;
import com.wewins.fota.application.firmware.download.SignedUrlService;
import com.wewins.fota.storage.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * 无签名下载 URL 服务实现。
 * <p>
 * 直接返回固定下载地址，不进行任何签名处理。
 * 适用于无需签名验证的场景（如内部 CDN 或已通过其他方式保护的环境）。
 * 当使用 S3 path_style 时，会自动添加 bucket 到 URL 路径中。
 * </p>
 * <p>
 * 此类也可作为其他签名 URL 服务的基类，子类可复用 {@link #buildBaseUrl(String)} 方法。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-02
 */
@Slf4j
public class NoneSignedUrlServiceImpl implements SignedUrlService {

    protected final FirmwareDownloadProperties properties;
    protected final StorageProperties storageProperties;

    public NoneSignedUrlServiceImpl(FirmwareDownloadProperties properties, StorageProperties storageProperties) {
        this.properties = properties;
        this.storageProperties = storageProperties;
    }

    @Override
    public String generateSignedUrl(String firmwarePath) {
        return generateSignedUrl(firmwarePath, 0);
    }

    @Override
    public String generateSignedUrl(String firmwarePath, int expireSeconds) {
        validateFirmwarePath(firmwarePath);
        return buildBaseUrl(firmwarePath);
    }

    /**
     * 构建基础 URL（不包含签名参数）。
     * <p>
     * 根据 {@link StorageProperties.UrlAccessType} 决定 URL 格式：
     * <ul>
     *   <li>PATH_STYLE: {baseUrl}/{bucket}/{firmwarePath}</li>
     *   <li>LIKE_R2: {baseUrl}/{firmwarePath}（域名已绑定到桶）</li>
     *   <li>VIRTUAL_HOSTED: {baseUrl}/{firmwarePath}（bucket 在域名中）</li>
     * </ul>
     * </p>
     * <p>
     * 子类可复用此方法构建 URL 基础部分，再添加签名参数。
     * </p>
     *
     * @param firmwarePath 固件路径
     * @return 基础 URL
     */
    protected String buildBaseUrl(String firmwarePath) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("baseUrl 未配置，直接返回 firmwarePath");
            return firmwarePath;
        }

        StringBuilder urlBuilder = new StringBuilder();
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        urlBuilder.append(cleanBaseUrl);

        // 只有 PATH_STYLE 模式需要在 URL 中添加 bucket
        if (isS3PathStyle()) {
            String bucket = storageProperties.getS3().getBucket();
            if (bucket != null && !bucket.isBlank()) {
                urlBuilder.append("/").append(bucket);
            }
        }

        String cleanPath = firmwarePath.startsWith("/") ? firmwarePath.substring(1) : firmwarePath;
        urlBuilder.append("/").append(cleanPath);

        return urlBuilder.toString();
    }

    /**
     * 检查是否使用 S3 Path Style 访问模式
     * <p>
     * 只有 PATH_STYLE 模式需要在 URL 中包含 bucket 名称
     * R2模式上传是加到path中, 获取不加PATH
     * </p>
     *
     * @return 是否为 Path Style 模式
     */
    protected boolean isS3PathStyle() {
        return storageProperties.getS3().getUrlAccessType() == StorageProperties.UrlAccessType.PATH_STYLE;
    }

    /**
     * 校验固件路径参数
     */
    protected void validateFirmwarePath(String firmwarePath) {
        if (firmwarePath == null || firmwarePath.isBlank()) {
            throw new IllegalArgumentException("firmwarePath 不能为空");
        }
    }

    @Override
    public boolean verifySignature(String firmwarePath, long expireTime, String signature) {
        // 无签名模式跳过签名验证，但仍然校验基本参数
        if (firmwarePath == null || firmwarePath.isBlank()) {
            log.warn("签名验证失败：firmwarePath 为空");
            return false;
        }
        log.debug("无签名模式，跳过签名验证: firmwarePath={}", firmwarePath);
        return true;
    }
}
