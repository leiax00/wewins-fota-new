package com.wewins.fota.infra.validation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 固件文件校验器。
 * <p>
 * 提供固件文件上传前的类型和大小校验，防止非法文件上传和DOS攻击。
 * </p>
 * <p>
 * 校验顺序（短路失败）：
 * <ol>
 *   <li>文件大小（200MB上限）</li>
 *   <li>扩展名白名单（.bin, .zip, .tar, .tar.gz, .rar）</li>
 *   <li>Magic Number（文件头字节识别）</li>
 *   <li>MIME类型兜底校验</li>
 * </ol>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Slf4j
@Component
public class FirmwareFileValidator {

    /**
     * 最大文件大小：200MB
     */
    public static final long MAX_FILE_SIZE_BYTES = 200L * 1024L * 1024L;

    /**
     * Magic Number 读取上限（字节）
     * <p>
     * tar 格式需要在 offset 257 位置读取 "ustar"，因此至少需要 262 字节
     * </p>
     */
    private static final int MAGIC_READ_LIMIT = 560;

    /**
     * 允许的文件扩展名白名单
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".bin",
            ".zip",
            ".tar",
            ".tar.gz",
            ".rar"
    );

    /**
     * 扩展名对应的允许 MIME 类型集合
     */
    private static final Map<String, Set<String>> ALLOWED_MIME_BY_EXTENSION = Map.of(
            ".zip", Set.of("application/zip", "application/x-zip-compressed"),
            ".tar", Set.of("application/x-tar"),
            ".tar.gz", Set.of("application/gzip", "application/x-gzip", "application/x-tar"),
            ".rar", Set.of("application/vnd.rar", "application/x-rar-compressed", "application/x-rar")
    );

    /**
     * 校验固件文件。
     *
     * @param file 上传文件
     * @return 校验结果，包含是否通过、扩展名、MIME类型、文件大小等信息
     */
    public ValidationResult validate(MultipartFile file) throws IOException {
        // 1. 空值和大小校验
        if (file == null || file.isEmpty()) {
            return ValidationResult.failed("FILE_EMPTY", "上传文件不能为空");
        }

        long size = file.getSize();
        if (size <= 0) {
            return ValidationResult.failed("FILE_EMPTY", "上传文件内容为空");
        }
        if (size > MAX_FILE_SIZE_BYTES) {
            return ValidationResult.failed("FILE_TOO_LARGE",
                    String.format("文件大小超过限制（最大 %dMB）", MAX_FILE_SIZE_BYTES / 1024 / 1024));
        }

        // 2. 扩展名白名单校验
        String originalFilename = file.getOriginalFilename();
        String ext = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return ValidationResult.failed("FILE_EXT_NOT_ALLOWED",
                    String.format("文件扩展名不允许: %s（允许：%s）", ext, ALLOWED_EXTENSIONS));
        }

        // 3. Magic Number 校验
        byte[] header;
        try {
            header = readHeader(file);
        } catch (IOException e) {
            log.warn("读取文件头失败: fileName={}", originalFilename, e);
            return ValidationResult.failed("FILE_READ_ERROR", "读取文件内容失败");
        }

        if (!isMagicValid(ext, header)) {
            return ValidationResult.failed("FILE_MAGIC_INVALID",
                    "文件头校验失败，文件类型与扩展名不匹配");
        }

        // 4. MIME 类型兜底校验
        String detectedMime = detectMime(file, header);
        if (!isMimeValid(ext, detectedMime)) {
            return ValidationResult.failed("FILE_MIME_INVALID",
                    String.format("MIME 类型不合法: %s", detectedMime));
        }

        return ValidationResult.passed(ext, detectedMime, size);
    }

    /**
     * 判断扩展名对应的 Magic Number 是否有效。
     *
     * @param ext    文件扩展名
     * @param header 文件头字节
     * @return 是否有效
     */
    private boolean isMagicValid(String ext, byte[] header) {
        if (header == null || header.length == 0) {
            return false;
        }

        return switch (ext) {
            case ".zip" -> isZip(header);
            case ".tar" -> isTar(header);
            case ".tar.gz" -> isGzip(header);
            case ".rar" -> isRar(header);
            case ".bin" -> true; // .bin 无统一 magic，兜底依赖 MIME 与扩展名
            default -> false;
        };
    }

    /**
     * 校验 MIME 类型是否合法。
     *
     * @param ext  文件扩展名
     * @param mime 检测到的 MIME 类型
     * @return 是否合法
     */
    private boolean isMimeValid(String ext, String mime) {
        if (mime == null || mime.isBlank()) {
            // MIME 无法识别时，仅允许 .bin 通过（弱校验）
            return Objects.equals(ext, ".bin");
        }

        String normalized = mime.toLowerCase(Locale.ROOT).trim();
        if (Objects.equals(ext, ".bin")) {
            // .bin 不做强 MIME 绑定，只要不是明显文本类型即可
            return !normalized.startsWith("text/")
                    && !"application/json".equals(normalized)
                    && !"text/html".equals(normalized);
        }

        Set<String> allowedMimes = ALLOWED_MIME_BY_EXTENSION.get(ext);
        if (allowedMimes == null || allowedMimes.isEmpty()) {
            return true;
        }
        return allowedMimes.contains(normalized);
    }

    /**
     * 检测文件 MIME 类型。
     *
     * @param file   上传文件
     * @param header 文件头字节
     * @return MIME 类型
     */
    private String detectMime(MultipartFile file, byte[] header) throws IOException {
        // 优先从文件头检测
        String mimeFromHeader = URLConnection.guessContentTypeFromStream(
                new java.io.ByteArrayInputStream(header));
        if (mimeFromHeader != null && !mimeFromHeader.isBlank()) {
            return mimeFromHeader;
        }

        // 降级使用声明的 Content-Type
        String declared = file.getContentType();
        if (declared != null && !declared.isBlank()) {
            return declared;
        }

        return "application/octet-stream";
    }

    /**
     * 读取文件头字节（用于 Magic Number 校验）。
     *
     * @param file 上传文件
     * @return 文件头字节
     * @throws IOException 读取失败
     */
    private byte[] readHeader(MultipartFile file) throws IOException {
        byte[] buffer = new byte[MAGIC_READ_LIMIT];
        int total = 0;
        try (InputStream inputStream = file.getInputStream()) {
            while (total < MAGIC_READ_LIMIT) {
                int read = inputStream.read(buffer, total, MAGIC_READ_LIMIT - total);
                if (read < 0) {
                    break;
                }
                total += read;
            }
        }
        if (total == buffer.length) {
            return buffer;
        }
        byte[] actual = new byte[total];
        System.arraycopy(buffer, 0, actual, 0, total);
        return actual;
    }

    /**
     * 提取文件扩展名（支持多级扩展名如 .tar.gz）。
     *
     * @param filename 文件名
     * @return 扩展名（含点号，如 ".tar.gz"），空字符串表示无扩展名
     */
    private String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        String lower = filename.toLowerCase(Locale.ROOT).trim();
        if (lower.endsWith(".tar.gz")) {
            return ".tar.gz";
        }
        int idx = lower.lastIndexOf('.');
        if (idx < 0 || idx == lower.length() - 1) {
            return "";
        }
        return lower.substring(idx);
    }

    // ==================== Magic Number 检测方法 ====================

    /**
     * 检测是否为 ZIP 文件。
     * <p>
     * ZIP 文件头：PK\x03\x04 / PK\x05\x06 / PK\x07\x08
     * </p>
     */
    private boolean isZip(byte[] header) {
        return startsWith(header, new byte[]{0x50, 0x4B, 0x03, 0x04})
                || startsWith(header, new byte[]{0x50, 0x4B, 0x05, 0x06})
                || startsWith(header, new byte[]{0x50, 0x4B, 0x07, 0x08});
    }

    /**
     * 检测是否为 GZIP 文件（.tar.gz 的外层压缩）。
     * <p>
     * GZIP 文件头：0x1F 0x8B
     * </p>
     */
    private boolean isGzip(byte[] header) {
        return startsWith(header, new byte[]{0x1F, (byte) 0x8B});
    }

    /**
     * 检测是否为 RAR 文件。
     * <p>
     * RAR v4: 0x52 0x61 0x72 0x21 0x1A 0x07 0x00
     * RAR v5: 0x52 0x61 0x72 0x21 0x1A 0x07 0x01 0x00
     * </p>
     */
    private boolean isRar(byte[] header) {
        return startsWith(header, new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00})
                || startsWith(header, new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00});
    }

    /**
     * 检测是否为 TAR 文件。
     * <p>
     * TAR 文件在 offset 257 位置为 "ustar" (0x75 0x73 0x74 0x61 0x72)
     * </p>
     */
    private boolean isTar(byte[] header) {
        if (header.length < 262) {
            return false;
        }
        byte[] ustar = new byte[]{0x75, 0x73, 0x74, 0x61, 0x72}; // "ustar"
        for (int i = 0; i < ustar.length; i++) {
            if (header[257 + i] != ustar[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检测字节数组是否以指定前缀开头。
     *
     * @param data  字节数组
     * @param prefix 前缀
     * @return 是否匹配
     */
    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data == null || prefix == null || data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验结果。
     */
    @Getter
    @AllArgsConstructor
    public static class ValidationResult {
        /**
         * 是否通过校验
         */
        private final boolean passed;

        /**
         * 标准化扩展名（如 ".zip"）
         */
        private final String normalizedExtension;

        /**
         * 检测到的 MIME 类型
         */
        private final String detectedMime;

        /**
         * 文件大小（字节）
         */
        private final long fileSize;

        /**
         * 错误码（未通过时）
         */
        private final String errorCode;

        /**
         * 错误信息（未通过时）
         */
        private final String errorMessage;

        /**
         * 创建成功结果。
         */
        public static ValidationResult passed(String ext, String mime, long fileSize) {
            return new ValidationResult(true, ext, mime, fileSize, null, null);
        }

        /**
         * 创建失败结果。
         */
        public static ValidationResult failed(String errorCode, String errorMessage) {
            return new ValidationResult(false, null, null, 0L, errorCode, errorMessage);
        }
    }
}
