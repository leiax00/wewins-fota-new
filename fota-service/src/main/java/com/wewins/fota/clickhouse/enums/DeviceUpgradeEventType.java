package com.wewins.fota.clickhouse.enums;

import java.util.Arrays;

/**
 * 设备升级事件类型
 * <p>
 * 用于 ClickHouse Enum8 字段类型映射
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
public enum DeviceUpgradeEventType {

    /**
     * 开始下载固件
     */
    DL_START("DL_START", "Download start"),

    /**
     * 下载完成
     */
    DL_OK("DL_OK", "Download success"),

    /**
     * 下载失败
     */
    DL_FAIL("DL_FAIL", "Download failed"),

    /**
     * 升级完成
     */
    UP_OK("UP_OK", "Upgrade success"),

    /**
     * 升级失败
     */
    UP_FAIL("UP_FAIL", "Upgrade failed");

    private final String dbValue;
    private final String description;

    DeviceUpgradeEventType(String dbValue, String description) {
        this.dbValue = dbValue;
        this.description = description;
    }

    /**
     * 获取 ClickHouse 数据库存储值
     *
     * @return 数据库枚举值
     */
    public String getDbValue() {
        return dbValue;
    }

    /**
     * 获取事件描述
     *
     * @return 事件描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 从数据库值解析枚举
     *
     * @param dbValue 数据库枚举值
     * @return 对应的枚举类型，如果不存在则返回 null
     */
    public static DeviceUpgradeEventType fromDbValue(String dbValue) {
        if (dbValue == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(v -> v.dbValue.equalsIgnoreCase(dbValue))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return dbValue;
    }
}
