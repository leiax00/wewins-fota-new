package com.wewins.fota.storage.naming;

import com.wewins.fota.storage.config.StorageKeyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 默认存储对象 Key 生成器实现。
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultStorageObjectKeyGenerator implements StorageObjectKeyGenerator {

    private final StorageKeyProperties storageKeyProperties;

    @Override
    public String generateFirmwarePackageKey(Long productId, String originalFilename) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product ID must be positive");
        }

        // 1. 提取文件扩展名
        String extension = FileExtensionResolver.resolve(originalFilename);

        // 2. 生成 UUID（去除横线，使路径更短）
        String fileUuid = UUID.randomUUID().toString().replace("-", "");

        // 3. 组装对象 Key：{prefix}/{productId}/{uuid}.{ext}
        //    例如：fota/fw/2/a1b2c3d4e5f6.zip
        return String.format("%s/%d/%s.%s",
                storageKeyProperties.getFirmwarePrefix(),
                productId,
                fileUuid,
                extension);
    }
}
