package com.wewins.fota.storage.core;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Local file staging and storage transfer service.
 */
public interface FileTransferService {

    Path createStagingFile(String prefix, String suffix) throws IOException;

    String transferToStorage(Path localFile, String objectKey, String contentType, boolean deleteAfterTransfer);

    void deleteStagingFile(Path localFile);

    /**
     * 获取对象存储文件的下载地址。
     * <p>
     * 生成带签名的预签名 URL，用于安全下载。
     * </p>
     *
     * @param objectKey 对象存储键（如 fota/fw/2/uuid.zip）
     * @param ttl      URL 有效期
     * @return 完整的 HTTPS 下载地址（带签名）
     */
    String getDownloadUrl(String objectKey, Duration ttl);

    /**
     * 删除对象存储中的文件。
     * <p>
     * 用于清理被替换的固件包文件，避免孤儿文件堆积。
     * </p>
     *
     * @param objectKey 对象存储键（如 fota/fw/2/uuid.zip）
     */
    void deleteStorageObject(String objectKey);
}
