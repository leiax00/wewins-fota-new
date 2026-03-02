package com.wewins.fota.application.firmware.download.impl;

import com.wewins.fota.application.firmware.download.FirmwareDownloadProperties;
import com.wewins.fota.application.firmware.download.SignedUrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 无签名下载 URL 服务实现。
 * <p>
 * 直接返回固定下载地址，不进行任何签名处理。
 * 适用于无需签名验证的场景（如内部 CDN 或已通过其他方式保护的环境）。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-02
 */
@Slf4j
@RequiredArgsConstructor
public class NoneSignedUrlServiceImpl implements SignedUrlService {

    private final FirmwareDownloadProperties properties;

    @Override
    public String generateSignedUrl(String firmwarePath) {
        return generateSignedUrl(firmwarePath, 0);
    }

    @Override
    public String generateSignedUrl(String firmwarePath, int expireSeconds) {
        // 参数校验
        if (firmwarePath == null || firmwarePath.isBlank()) {
            throw new IllegalArgumentException("firmwarePath 不能为空");
        }

        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("baseUrl 未配置，直接返回 firmwarePath");
            return firmwarePath;
        }

        // 拼接 baseUrl 和 firmwarePath
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String cleanPath = firmwarePath.startsWith("/") ? firmwarePath : "/" + firmwarePath;

        return cleanBaseUrl + cleanPath;
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
