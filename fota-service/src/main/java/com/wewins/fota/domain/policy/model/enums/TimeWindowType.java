package com.wewins.fota.domain.policy.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 时间窗口类型枚举
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
public enum TimeWindowType {

    /**
     * 不限制
     * <p>
     * 不限制执行时间，随时可以触发升级
     * </p>
     */
    UNLIMITED("不限制", "UNLIMITED"),

    /**
     * 固定范围
     * <p>
     * 一次性的时间范围，可跨越多天
     * </p>
     */
    RANGE("固定范围", "RANGE"),

    /**
     * 每日周期
     * <p>
     * 每日重复的时间段，约定不超过24小时，支持跨日（如23:00-02:00）
     * </p>
     */
    DAILY("每日周期", "DAILY");

    private final String displayName;
    private final String code;

    TimeWindowType(String displayName, String code) {
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
     * @return 枚举值，如果编码无效返回 null
     */
    @JsonCreator
    public static TimeWindowType of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        String normalized = code.trim().toUpperCase();
        for (TimeWindowType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }

        // 无效输入返回 null
        return null;
    }

    /**
     * 判断是否为每日周期模式
     *
     * @return true 如果为 DAILY 模式
     */
    public boolean isDaily() {
        return this == DAILY;
    }
}
