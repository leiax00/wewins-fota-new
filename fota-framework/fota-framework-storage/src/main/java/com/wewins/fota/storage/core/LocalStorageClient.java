package com.wewins.fota.storage.core;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Local filesystem implementation of storage client.
 */
@RequiredArgsConstructor
public class LocalStorageClient implements StorageClient {

    private final Path baseDir;

    @Override
    public String upload(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        Path target = resolveTargetPath(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toUri().toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Upload local object failed", ex);
        }
    }

    @Override
    public String getDownloadUrl(String objectKey, Duration ttl) {
        Path target = resolveTargetPath(objectKey);
        return target.toUri().toString();
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolveTargetPath(objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new IllegalStateException("Delete local object failed", ex);
        }
    }

    private Path resolveTargetPath(String objectKey) {
        Path normalized = baseDir.resolve(objectKey).normalize();
        if (!normalized.startsWith(baseDir.normalize())) {
            throw new IllegalArgumentException("Invalid object key path");
        }
        return normalized;
    }
}
