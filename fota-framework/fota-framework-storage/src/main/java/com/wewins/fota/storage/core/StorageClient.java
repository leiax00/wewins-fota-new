package com.wewins.fota.storage.core;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

/**
 * Common object storage client abstraction.
 */
public interface StorageClient {

    String upload(String objectKey, InputStream inputStream, long contentLength, String contentType);

    String getDownloadUrl(String objectKey, Duration ttl);

    /**
     * 获取带自定义查询参数的预签名下载 URL。
     * <p>
     * 自定义参数会被纳入签名计算，确保 URL 完整性。
     * </p>
     *
     * @param objectKey     对象键
     * @param ttl           URL 有效期
     * @param customParams  自定义查询参数
     * @return 预签名 URL
     */
    default String getDownloadUrl(String objectKey, Duration ttl, Map<String, String> customParams) {
        // 默认实现忽略自定义参数，子类可覆盖
        return getDownloadUrl(objectKey, ttl);
    }

    void delete(String objectKey);
}
