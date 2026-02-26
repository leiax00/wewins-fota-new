package com.wewins.fota.storage.naming;

/**
 * 存储对象 Key 生成器接口。
 * <p>
 * 用于统一管理对象存储的路径命名规则。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
public interface StorageObjectKeyGenerator {

    /**
     * 生成固件包的对象存储 Key。
     * <p>
     * 格式：{prefix}/{productId}/{uuid}.{ext}
     * 示例：fota/fw/2/a1b2c3d4e5f6.zip
     * </p>
     *
     * @param productId        产品 ID
     * @param originalFilename 原始文件名
     * @return 对象存储 Key
     */
    String generateFirmwarePackageKey(Long productId, String originalFilename);
}
