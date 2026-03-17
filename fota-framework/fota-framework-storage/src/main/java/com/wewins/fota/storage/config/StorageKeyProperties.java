package com.wewins.fota.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 存储键配置属性。
 * <p>
     * 集中管理对象存储的路径前缀和命名规则。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Data
@Component
@ConfigurationProperties(prefix = "fota.storage.key")
public class StorageKeyProperties {

    /**
     * 固件包存储路径前缀。
     * <p>
     * 示例：fota/fw
     * </p>
     */
    private String firmwarePrefix = "fw";

    /**
     * 设备数据存储路径前缀（预留）。
     */
    private String deviceDataPrefix = "dd";

    /**
     * 日志数据存储路径前缀（预留）。
     */
    private String logDataPrefix = "logs";
}
