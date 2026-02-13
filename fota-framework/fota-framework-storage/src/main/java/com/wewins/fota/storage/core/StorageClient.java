package com.wewins.fota.storage.core;

import java.io.InputStream;
import java.time.Duration;

/**
 * Common object storage client abstraction.
 */
public interface StorageClient {

    String upload(String objectKey, InputStream inputStream, long contentLength, String contentType);

    String getDownloadUrl(String objectKey, Duration ttl);

    void delete(String objectKey);
}
