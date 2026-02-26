package com.wewins.fota.storage.naming;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 文件扩展名解析器。
 * <p>
 * 从文件名中提取扩展名，支持常见的双扩展名压缩格式（如 .tar.gz）。
 * </p>
 * <p>
 * 示例：
 * <ul>
 *   <li>"archive.tar.gz" → "tar.gz"</li>
 *   <li>"firmware.bin" → "bin"</li>
 *   <li>"README" → "bin"</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
public class FileExtensionResolver {

    private static final String DEFAULT_EXTENSION = "bin";

    /**
     * 扩展名字符白名单：仅允许小写字母、数字和点号。
     */
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("^[a-z0-9.]+$");

    /**
     * 常见的双扩展名白名单（压缩文件格式）。
     */
    private static final String[] DOUBLE_EXTENSIONS = {
            ".tar.gz", ".tar.bz2", ".tar.xz", ".tar.Z",
            ".zip.001", ".zip.002", ".zip.003",
            ".rar.001", ".rar.002", ".rar.003",
            ".7z.001", ".7z.002", ".7z.003"
    };

    /**
     * 从文件名中提取扩展名（不包含点号）。
     *
     * @param filename 文件名
     * @return 扩展名（不包含点号），如果没有扩展名则返回默认的 "bin"
     */
    public static String resolve(String filename) {
        return resolve(filename, DEFAULT_EXTENSION);
    }

    /**
     * 从文件名中提取扩展名（不包含点号）。
     *
     * @param filename         文件名
     * @param defaultExtension 默认扩展名（当文件名没有扩展名时使用）
     * @return 扩展名（不包含点号）
     */
    public static String resolve(String filename, String defaultExtension) {
        String safeDefault = sanitizeDefaultExtension(defaultExtension);
        if (filename == null || filename.isBlank()) {
            return safeDefault;
        }

        String normalizedFilename = filename.trim().toLowerCase(Locale.ROOT);

        // 检查是否匹配双扩展名
        for (String doubleExt : DOUBLE_EXTENSIONS) {
            if (normalizedFilename.endsWith(doubleExt.toLowerCase(Locale.ROOT))) {
                // 返回不包含点号的双扩展名，例如 "tar.gz"
                return sanitizeResolvedExtension(doubleExt.substring(1), safeDefault);
            }
        }

        // 常规单扩展名处理
        int lastDotIndex = normalizedFilename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == normalizedFilename.length() - 1) {
            return safeDefault; // 没有扩展名或以点结尾
        }

        return sanitizeResolvedExtension(normalizedFilename.substring(lastDotIndex + 1), safeDefault);
    }

    /**
     * 从文件名中提取扩展名（包含点号）。
     *
     * @param filename 文件名
     * @return 扩展名（包含点号），如果没有扩展名则返回默认扩展名（包含点号）
     */
    public static String resolveWithDot(String filename) {
        return resolveWithDot(filename, "." + DEFAULT_EXTENSION);
    }

    /**
     * 从文件名中提取扩展名（包含点号）。
     *
     * @param filename         文件名
     * @param defaultExtension 默认扩展名（必须包含点号）
     * @return 扩展名（包含点号）
     */
    public static String resolveWithDot(String filename, String defaultExtension) {
        String normalizedDefault = normalizeDefaultWithDot(defaultExtension);
        String ext = resolve(filename, normalizedDefault.substring(1));
        return "." + ext;
    }

    /**
     * 标准化默认扩展名（确保包含点号）。
     *
     * @param defaultExtension 默认扩展名
     * @return 标准化后的扩展名（包含点号）
     */
    private static String normalizeDefaultWithDot(String defaultExtension) {
        if (defaultExtension == null || defaultExtension.isBlank()) {
            return "." + DEFAULT_EXTENSION;
        }
        String trimmed = defaultExtension.trim();
        if (!trimmed.startsWith(".")) {
            trimmed = "." + trimmed;
        }
        if (trimmed.length() == 1) {
            return "." + DEFAULT_EXTENSION;
        }
        return trimmed;
    }

    /**
     * 清理默认扩展名（移除点号前缀）。
     *
     * @param defaultExtension 默认扩展名
     * @return 清理后的扩展名（不包含点号）
     */
    private static String sanitizeDefaultExtension(String defaultExtension) {
        if (defaultExtension == null || defaultExtension.isBlank()) {
            return DEFAULT_EXTENSION;
        }
        String trimmed = defaultExtension.trim();
        if (trimmed.startsWith(".")) {
            trimmed = trimmed.substring(1);
        }
        return sanitizeResolvedExtension(trimmed, DEFAULT_EXTENSION);
    }

    /**
     * 清理解析出的扩展名（白名单校验 + 小写转换）。
     *
     * @param ext      原始扩展名
     * @param fallback 备用扩展名
     * @return 清理后的扩展名
     */
    private static String sanitizeResolvedExtension(String ext, String fallback) {
        if (ext == null || ext.isBlank()) {
            return fallback;
        }
        String normalized = ext.trim().toLowerCase(Locale.ROOT);
        return EXTENSION_PATTERN.matcher(normalized).matches() ? normalized : fallback;
    }
}
