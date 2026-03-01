package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.wewins.fota.adapter.api.device.dto.UpgradeDecision;
import com.wewins.fota.application.firmware.download.SignedUrlService;
import com.wewins.fota.application.upgrade.dto.CheckResult;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 升级检查响应构建服务
 * <p>
 * 负责将策略和固件信息组装成 Application 层的 CheckResult
 * </p>
 * <p>
 * 主要职责：
 * </p>
 * <ul>
 *   <li>加载固件元数据（版本号、文件大小、发布说明等）</li>
 *   <li>生成签名下载 URL（集成 SignedUrlService）</li>
 *   <li>计算控制参数（检查间隔、下载延迟）</li>
 *   <li>多语言发布说明选择</li>
 *   <li>文件大小格式化</li>
 * </ul>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpgradeResponseBuilder {

    private final SignedUrlService signedUrlService;
    private final FirmwareVersionRepository firmwareVersionRepository;

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** 默认检查间隔：1 小时（秒） */
    private static final int DEFAULT_CHECK_INTERVAL = 3600;

    /** 默认下载延迟：5 分钟（秒） */
    private static final int DEFAULT_DOWNLOAD_DELAY = 300;

    /**
     * 构建完整的升级检查响应
     * <p>
     * 此方法综合策略、固件、设备信息生成 CheckResult
     * </p>
     *
     * @param device   设备信息
     * @param policy   匹配的升级策略
     * @param lang     语言代码（如 "en", "zh"），可选
     * @param autoMode 是否自动检查模式
     * @return 检查结果
     */
    public CheckResult buildResponse(Device device, UpgradePolicy policy, String lang, Boolean autoMode) {
        // 1. 加载目标固件版本信息
        FirmwareVersion targetFirmware = loadTargetFirmware(policy.getTargetVersionId());
        if (targetFirmware == null) {
            log.warn("目标固件版本不存在: targetVersionId={}", policy.getTargetVersionId());
            return CheckResult.error("目标固件版本不存在");
        }

        // 2. 检查固件包状态
        if (!isFirmwareReady(targetFirmware)) {
            log.warn("固件包未准备好: targetVersionId={}, packageStatus={}",
                    targetFirmware.getId(), targetFirmware.getPackageStatus());
            return CheckResult.error("固件包未准备好");
        }

        // 3. 计算控制参数
        int checkInterval = calculateCheckInterval(autoMode);
        int downloadDelay = calculateDownloadDelay();

        // 4. 生成签名下载 URL
        String downloadUrl = generateDownloadUrl(targetFirmware, policy, device);

        // 5. 获取校验和
        String checksum = selectChecksum(targetFirmware);
        String checksumType = selectChecksumType(targetFirmware);

        // 6. 构建 CheckResult（扁平结构，不使用 ext Map）
        return CheckResult.builder()
                .hasUpdate(true)
                .decision(UpgradeDecision.UPDATE)
                .targetVersionId(targetFirmware.getId())
                .targetVersion(targetFirmware.getVersion())
                .policyId(policy.getId())
                .checkInterval(checkInterval)
                .downloadDelay(downloadDelay)
                .releaseStartDate(formatReleaseDate(targetFirmware))
                .releaseNote(extractReleaseNote(targetFirmware, lang))
                .newFirmware(targetFirmware.getVersion())
                .downloadUrl(downloadUrl)
                .fileSize(targetFirmware.getFileSize())
                .fileSizeText(formatFileSize(targetFirmware.getFileSize()))
                .checksum(checksum)
                .checksumType(checksumType)
                .build();
    }

    private FirmwareVersion loadTargetFirmware(Long targetVersionId) {
        return firmwareVersionRepository.findById(targetVersionId).orElse(null);
    }

    private boolean isFirmwareReady(FirmwareVersion firmware) {
        return firmware != null
                && "READY".equals(firmware.getPackageStatus())
                && firmware.getFileUrl() != null;
    }

    private String extractReleaseNote(FirmwareVersion firmware, String lang) {
        if (firmware.getMeta() == null) {
            return getSimpleReleaseNote(firmware);
        }

        JsonNode meta = firmware.getMeta();
        if (!meta.has("i18n")) {
            return getSimpleReleaseNote(firmware);
        }

        JsonNode i18n = meta.get("i18n");
        String effectiveLang = determineEffectiveLanguage(lang, i18n);

        JsonNode langNode = i18n.get(effectiveLang);
        if (langNode != null) {
            if (langNode.has("changelog") && langNode.get("changelog").isTextual()) {
                return langNode.get("changelog").asText();
            }
            if (langNode.has("description") && langNode.get("description").isTextual()) {
                return langNode.get("description").asText();
            }
        }

        return getSimpleReleaseNote(firmware);
    }

    private String determineEffectiveLanguage(String lang, JsonNode i18n) {
        if (lang == null || lang.isBlank()) {
            lang = "en";
        } else {
            lang = lang.toLowerCase();
        }

        if (i18n.has(lang)) {
            return lang;
        }

        if (lang.contains("-")) {
            String baseLang = lang.split("-")[0];
            if (i18n.has(baseLang)) {
                return baseLang;
            }
        }

        if (i18n.has("en")) {
            return "en";
        }

        if (i18n.size() > 0) {
            return i18n.fieldNames().next();
        }

        return "en";
    }

    private String getSimpleReleaseNote(FirmwareVersion firmware) {
        JsonNode tags = firmware.getTags();
        if (tags != null && tags.has("releaseNote")) {
            return tags.get("releaseNote").asText();
        }
        if (tags != null && tags.has("description")) {
            return tags.get("description").asText();
        }
        return "";
    }

    private String generateDownloadUrl(FirmwareVersion firmware, UpgradePolicy policy, Device device) {
        String fileUrl = firmware.getFileUrl();
        if (fileUrl == null || fileUrl.isBlank()) {
            log.warn("固件文件 URL 为空: firmwareId={}", firmware.getId());
            return null;
        }

        try {
            return signedUrlService.generateSignedUrl(fileUrl, policy.getId(), device.getId());
        } catch (Exception e) {
            log.error("生成签名下载 URL 失败: firmwareId={}, policyId={}, deviceId={}",
                    firmware.getId(), policy.getId(), device.getId(), e);
            return null;
        }
    }

    private int calculateCheckInterval(Boolean autoMode) {
        boolean isAuto = Boolean.TRUE.equals(autoMode);
        return isAuto ? 86400 : DEFAULT_CHECK_INTERVAL;
    }

    private int calculateDownloadDelay() {
        return DEFAULT_DOWNLOAD_DELAY;
    }

    private String formatReleaseDate(FirmwareVersion firmware) {
        if (firmware.getPackageUploadedAt() == null) {
            if (firmware.getCreatedAt() != null) {
                return firmware.getCreatedAt().format(ISO_FORMATTER);
            }
            return "";
        }
        return firmware.getPackageUploadedAt().format(ISO_FORMATTER);
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null || bytes == 0) {
            return "0B";
        }

        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes.doubleValue();

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        if (unitIndex >= 2) {
            return String.format(Locale.US, "%.1f%s", size, units[unitIndex]);
        }
        return String.format(Locale.US, "%.0f%s", size, units[unitIndex]);
    }

    private String selectChecksum(FirmwareVersion firmware) {
        if (firmware.getSha256() != null && !firmware.getSha256().isBlank()) {
            return firmware.getSha256();
        }
        return firmware.getMd5();
    }

    private String selectChecksumType(FirmwareVersion firmware) {
        if (firmware.getSha256() != null && !firmware.getSha256().isBlank()) {
            return "sha256";
        }
        if (firmware.getMd5() != null && !firmware.getMd5().isBlank()) {
            return "md5";
        }
        return null;
    }
}
