package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.wewins.fota.application.firmware.download.SignedUrlService;
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
 * 负责将策略和固件信息组装成设备可理解的响应格式
 * </p>
 * <p>
 * 主要职责：
 * </p>
 * <ul>
 *   <li>加载固件元数据（版本号、文件大小、发布说明等）</li>
 *   <li>生成签名下载 URL（集成任务 #18 的 SignedUrlService）</li>
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

    /**
     * 签名下载 URL 服务（任务 #18 已完成）
     */
    private final SignedUrlService signedUrlService;

    /**
     * 固件版本仓储
     */
    private final FirmwareVersionRepository firmwareVersionRepository;

    /**
     * ISO 8601 日期时间格式化器
     */
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * 默认检查间隔（秒）
     */
    private static final int DEFAULT_CHECK_INTERVAL = 3600;  // 1 小时

    /**
     * 默认下载延迟（秒）
     */
    private static final int DEFAULT_DOWNLOAD_DELAY = 300;    // 5 分钟

    /**
     * 无更新时的检查间隔（秒）
     */
    private static final int NO_UPDATE_CHECK_INTERVAL = 86400; // 24 小时

    /**
     * 构建完整的升级检查响应
     * <p>
     * 此方法综合策略、固件、设备信息生成响应
     * </p>
     *
     * @param device 设备信息
     * @param policy 匹配的升级策略
     * @param lang   语言代码（如 "en", "zh"），可选
     * @param autoMode 是否自动检查模式
     * @return 检查结果
     */
    public UpgradeCheckService.CheckResult buildResponse(Device device, UpgradePolicy policy, String lang, Boolean autoMode) {
        // 1. 加载目标固件版本信息
        FirmwareVersion targetFirmware = loadTargetFirmware(policy.getTargetVersionId());
        if (targetFirmware == null) {
            log.warn("目标固件版本不存在: targetVersionId={}", policy.getTargetVersionId());
            return UpgradeCheckService.CheckResult.error("目标固件版本不存在");
        }

        // 2. 检查固件包状态
        if (!isFirmwareReady(targetFirmware)) {
            log.warn("固件包未准备好: targetVersionId={}, packageStatus={}",
                    targetFirmware.getId(), targetFirmware.getPackageStatus());
            return UpgradeCheckService.CheckResult.error("固件包未准备好");
        }

        // 3. 计算控制参数
        int checkInterval = calculateCheckInterval(policy, autoMode);
        int downloadDelay = calculateDownloadDelay(policy);

        // 4. 构建扩展数据
        java.util.Map<String, Object> extData = new java.util.HashMap<>();
        extData.put("releaseNote", extractReleaseNote(targetFirmware, lang));
        extData.put("releaseStartDate", formatReleaseDate(targetFirmware));

        // 5. 设置文件大小和校验和
        if (targetFirmware.getFileSize() != null) {
            extData.put("fileSize", targetFirmware.getFileSize());
            extData.put("fileSizeText", formatFileSize(targetFirmware.getFileSize()));
        }

        // 6. 生成签名下载 URL（使用任务 #18 的 SignedUrlService）
        String downloadUrl = generateDownloadUrl(targetFirmware, policy, device);
        if (downloadUrl != null) {
            extData.put("downloadUrl", downloadUrl);
        }

        // 7. 设置校验和
        String checksum = selectChecksum(targetFirmware);
        if (checksum != null) {
            extData.put("checksum", checksum);
            extData.put("checksumType", selectChecksumType(targetFirmware));
        }

        // 8. 设置控制参数
        extData.put("control", java.util.Map.of(
                "checkInterval", checkInterval,
                "downloadDelay", downloadDelay
        ));

        // 9. 构建响应
        return UpgradeCheckService.CheckResult.builder()
                .hasUpdate(true)
                .decision("UPDATE")
                .targetVersionId(targetFirmware.getId())
                .targetVersion(targetFirmware.getVersion())
                .policyId(policy.getId())
                .responseCheckInterval(checkInterval)
                .downloadDelay(downloadDelay)
                .ext(extData)
                .build();
    }

    /**
     * 加载目标固件版本信息
     *
     * @param targetVersionId 目标版本 ID
     * @return 固件版本信息
     */
    private FirmwareVersion loadTargetFirmware(Long targetVersionId) {
        return firmwareVersionRepository.findById(targetVersionId).orElse(null);
    }

    /**
     * 检查固件包是否已准备好
     *
     * @param firmware 固件版本
     * @return true 如果固件包状态为 READY
     */
    private boolean isFirmwareReady(FirmwareVersion firmware) {
        return firmware != null
                && "READY".equals(firmware.getPackageStatus())
                && firmware.getFileUrl() != null;
    }

    /**
     * 提取发布说明（支持多语言）
     * <p>
     * 优先级顺序：
     * </p>
     * <ol>
     *   <li>请求的语言（lang 参数）</li>
     *   <li>固件默认语言</li>
     *   <li>降级到英文</li>
     * </ol>
     *
     * @param firmware 固件版本
     * @param lang     语言代码（如 "en", "zh"）
     * @return 发布说明文本
     */
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

        // 按优先级查找：changelog > description
        JsonNode langNode = i18n.get(effectiveLang);
        if (langNode != null) {
            if (langNode.has("changelog") && langNode.get("changelog").isTextual()) {
                return langNode.get("changelog").asText();
            }
            if (langNode.has("description") && langNode.get("description").isTextual()) {
                return langNode.get("description").asText();
            }
        }

        // 降级到简单版本
        return getSimpleReleaseNote(firmware);
    }

    /**
     * 确定有效语言
     */
    private String determineEffectiveLanguage(String lang, JsonNode i18n) {
        if (lang == null || lang.isBlank()) {
            lang = "en";
        } else {
            lang = lang.toLowerCase();
        }

        // 检查请求语言是否可用
        if (i18n.has(lang)) {
            return lang;
        }

        // 尝试精确匹配后的降级（如 zh-CN -> zh）
        if (lang.contains("-")) {
            String baseLang = lang.split("-")[0];
            if (i18n.has(baseLang)) {
                return baseLang;
            }
        }

        // 降级到英文
        if (i18n.has("en")) {
            return "en";
        }

        // 取第一个可用语言
        if (i18n.size() > 0) {
            return i18n.fieldNames().next();
        }

        return "en";
    }

    /**
     * 获取简单的 release_note（从 tags 中）
     */
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

    /**
     * 生成签名下载 URL
     * <p>
     * 使用任务 #18 的 SignedUrlService
     * </p>
     *
     * @param firmware 固件版本
     * @param policy   升级策略
     * @param device   设备
     * @return 签名下载 URL，如果固件包不存在则返回 null
     */
    private String generateDownloadUrl(FirmwareVersion firmware, UpgradePolicy policy, Device device) {
        String fileUrl = firmware.getFileUrl();
        if (fileUrl == null || fileUrl.isBlank()) {
            log.warn("固件文件 URL 为空: firmwareId={}", firmware.getId());
            return null;
        }

        try {
            return signedUrlService.generateSignedUrl(
                    fileUrl,
                    policy.getId(),
                    device.getId()
            );
        } catch (Exception e) {
            log.error("生成签名下载 URL 失败: firmwareId={}, policyId={}, deviceId={}",
                    firmware.getId(), policy.getId(), device.getId(), e);
            return null;
        }
    }

    /**
     * 计算检查间隔
     *
     * @param policy   升级策略
     * @param autoMode 是否自动检查模式
     * @return 检查间隔（秒）
     */
    private int calculateCheckInterval(UpgradePolicy policy, Boolean autoMode) {
        // 自动模式使用更长间隔，手动模式使用更短间隔
        boolean isAuto = Boolean.TRUE.equals(autoMode);
        return isAuto ? 86400 : DEFAULT_CHECK_INTERVAL;  // 自动 24h，手动 1h
    }

    /**
     * 计算下载延迟
     *
     * @param policy 升级策略
     * @return 下载延迟（秒）
     */
    private int calculateDownloadDelay(UpgradePolicy policy) {
        return DEFAULT_DOWNLOAD_DELAY;
    }

    /**
     * 格式化发布日期
     *
     * @param firmware 固件版本
     * @return ISO 8601 格式的日期时间字符串
     */
    private String formatReleaseDate(FirmwareVersion firmware) {
        if (firmware.getPackageUploadedAt() == null) {
            if (firmware.getCreatedAt() != null) {
                return firmware.getCreatedAt().format(ISO_FORMATTER);
            }
            return "";
        }
        return firmware.getPackageUploadedAt().format(ISO_FORMATTER);
    }

    /**
     * 格式化文件大小为人类可读格式
     *
     * @param bytes 文件大小（字节）
     * @return 格式化后的文本（如 "19MB", "1.5GB"）
     */
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

        // 对于 MB 和 GB，显示一位小数
        if (unitIndex >= 2) {
            return String.format(Locale.US, "%.1f%s", size, units[unitIndex]);
        }
        return String.format(Locale.US, "%.0f%s", size, units[unitIndex]);
    }

    /**
     * 选择校验和（优先 SHA-256）
     *
     * @param firmware 固件版本
     * @return 校验和值
     */
    private String selectChecksum(FirmwareVersion firmware) {
        if (firmware.getSha256() != null && !firmware.getSha256().isBlank()) {
            return firmware.getSha256();
        }
        return firmware.getMd5();
    }

    /**
     * 选择校验和类型
     *
     * @param firmware 固件版本
     * @return "sha256" 或 "md5"
     */
    private String selectChecksumType(FirmwareVersion firmware) {
        if (firmware.getSha256() != null && !firmware.getSha256().isBlank()) {
            return "sha256";
        }
        if (firmware.getMd5() != null && !firmware.getMd5().isBlank()) {
            return "md5";
        }
        return null;
    }

    // ========== 静态工厂方法 ==========

    /**
     * 创建"无更新"响应
     */
    public UpgradeCheckService.CheckResult buildNoUpdateResponse() {
        return UpgradeCheckService.CheckResult.builder()
                .hasUpdate(false)
                .decision("NO_UPDATE")
                .responseCheckInterval(NO_UPDATE_CHECK_INTERVAL)
                .build();
    }

    /**
     * 创建"设备不存在"响应
     */
    public UpgradeCheckService.CheckResult buildNotFoundResponse(String message) {
        return UpgradeCheckService.CheckResult.builder()
                .hasUpdate(false)
                .decision("DEVICE_NOT_FOUND")
                .errorMessage(message)
                .build();
    }

    /**
     * 创建"限流"响应
     */
    public UpgradeCheckService.CheckResult buildRateLimitedResponse(String message, int retryAfterSeconds) {
        return UpgradeCheckService.CheckResult.builder()
                .hasUpdate(false)
                .decision("RATE_LIMITED")
                .errorMessage(message)
                .responseCheckInterval(retryAfterSeconds)
                .downloadDelay(retryAfterSeconds)
                .build();
    }

    /**
     * 创建"错误"响应
     */
    public UpgradeCheckService.CheckResult buildErrorResponse(String errorCode, String message) {
        java.util.Map<String, Object> extData = new java.util.HashMap<>();
        extData.put("errorCode", errorCode);

        return UpgradeCheckService.CheckResult.builder()
                .hasUpdate(false)
                .decision("ERROR")
                .errorMessage(message)
                .ext(extData)
                .build();
    }
}
