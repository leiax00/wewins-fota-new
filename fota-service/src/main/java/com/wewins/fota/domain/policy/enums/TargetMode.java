package com.wewins.fota.domain.policy.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 目标设备模式枚举
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
public enum TargetMode {

    /**
     * 全量设备
     * <p>
     * 策略适用于产品下的所有设备
     * </p>
     */
    ALL("全量设备", "ALL"),

    /**
     * 指定设备ID列表
     * <p>
     * 策略仅适用于指定的设备ID列表
     * </p>
     */
    DEVICE_IDS("设备ID列表", "DEVICE_IDS"),

    /**
     * 指定设备批次列表
     * <p>
     * 策略仅适用于指定批次的设备
     * </p>
     */
    DEVICE_BATCHES("设备批次列表", "DEVICE_BATCHES"),

    /**
     * 按标签筛选
     * <p>
     * 策略适用于满足标签条件的设备（AND逻辑）
     * </p>
     */
    DEVICE_TAGS("设备标签筛选", "DEVICE_TAGS");

    private final String displayName;
    private final String code;

    TargetMode(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    /**
     * 获取显示名称（中文）
     *
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取枚举编码（用于数据库存储）
     *
     * @return 编码
     */
    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * 根据编码获取枚举值
     *
     * @param code 编码
     * @return 枚举值，如果编码无效返回 ALL（默认值）
     */
    @JsonCreator
    public static TargetMode of(String code) {
        if (code == null || code.isBlank()) {
            return ALL; // 默认值
        }

        String normalized = code.trim().toUpperCase();
        for (TargetMode mode : values()) {
            if (mode.code.equals(normalized)) {
                return mode;
            }
        }

        // 无效输入返回默认值
        return ALL;
    }

    /**
     * 判断是否需要设备筛选字段
     *
     * @return true 如果不是全量模式
     */
    public boolean requiresFilter() {
        return this != ALL;
    }

    /**
     * 获取对应的筛选字段名称
     *
     * @return 字段名称，如果不需要筛选返回 null
     */
    public String getFilterFieldName() {
        return switch (this) {
            case DEVICE_IDS -> "targetDeviceIds";
            case DEVICE_BATCHES -> "targetDeviceBatchIds";
            case DEVICE_TAGS -> "targetDeviceTags";
            default -> null;
        };
    }
}
