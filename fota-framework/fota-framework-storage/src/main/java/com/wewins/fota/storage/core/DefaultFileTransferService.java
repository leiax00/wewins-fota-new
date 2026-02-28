package com.wewins.fota.storage.core;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * Default file transfer implementation based on local staging files.
 */
@RequiredArgsConstructor
public class DefaultFileTransferService implements FileTransferService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final Path tempDir;
    private final StorageClient storageClient;

    @Override
    public Path createStagingFile(String prefix, String suffix) throws IOException {
        String safePrefix = (prefix == null || prefix.isBlank()) ? "fota-" : prefix;
        String safeSuffix = (suffix == null || suffix.isBlank()) ? ".tmp" : suffix;
        return Files.createTempFile(tempDir, safePrefix, safeSuffix);
    }

    @Override
    public String transferToStorage(Path localFile, String objectKey, String contentType, boolean deleteAfterTransfer) {
        if (localFile == null || !Files.exists(localFile)) {
            throw new IllegalArgumentException("Staging file does not exist");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Object key must not be blank");
        }
        try {
            long fileSize = Files.size(localFile);
            String effectiveContentType = (contentType == null || contentType.isBlank())
                    ? DEFAULT_CONTENT_TYPE
                    : contentType;
            try (InputStream inputStream = Files.newInputStream(localFile, StandardOpenOption.READ)) {
                String storedUrl = storageClient.upload(objectKey, inputStream, fileSize, effectiveContentType);
                if (deleteAfterTransfer) {
                    deleteStagingFile(localFile);
                }
                return storedUrl;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Transfer staging file to storage failed", ex);
        }
    }

    @Override
    public void deleteStagingFile(Path localFile) {
        if (localFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(localFile);
        } catch (IOException ex) {
            throw new IllegalStateException("Delete staging file failed", ex);
        }
    }

    @Override
    public String getDownloadUrl(String objectKey, Duration ttl) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Object key must not be blank");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        return storageClient.getDownloadUrl(objectKey, ttl);
    }

    @Override
    public void deleteStorageObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        // 安全校验：防止路径遍历攻击
        if (!isValidObjectKey(objectKey)) {
            throw new IllegalArgumentException("Invalid object key format: " + objectKey);
        }

        storageClient.delete(objectKey);
    }

    /**
     * 校验对象存储键是否安全。
     * <p>
     * 只允许字母、数字、斜杠、点、短横线、下划线，防止路径遍历攻击。
     * </p>
     *
     * @param objectKey 对象存储键
     * @return 是否安全
     */
    private boolean isValidObjectKey(String objectKey) {
        // 基本格式校验：允许字母、数字、斜杠、点、短横线、下划线
        if (!objectKey.matches("^[a-zA-Z0-9/._-]+$")) {
            return false;
        }
        // 防止路径遍历
        if (objectKey.contains("..")) {
            return false;
        }
        // 防止绝对路径
        if (objectKey.startsWith("/")) {
            return false;
        }
        return true;
    }
}
