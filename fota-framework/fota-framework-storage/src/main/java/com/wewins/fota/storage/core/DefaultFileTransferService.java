package com.wewins.fota.storage.core;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
}
