package com.wewins.fota.domain.policy.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 触发模式枚举
 * <p>
 * 定义策略的触发方式限制
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
public enum TriggerMode {

    /**
     * 仅自动触发
     * <p>
     * 策略只能由系统自动触发，不允许手动干预
     * </p>
     */
    AUTO("仅自动", "AUTO"),

    /**
     * 仅手动触发
     * <p>
     * 策略只能由人工手动触发，不允许自动执行
     * </p>
     */
    MANUAL("仅手动", "MANUAL"),

    /**
     * 不限制
     * <p>
     * 策略既可以自动触发，也可以手动触发
     * </p>
     */
    BOTH("不限制", "BOTH");

    /**
     * -- GETTER --
     *  获取显示名称（中文）
     *
     */
    @Getter
    private final String displayName;

    @EnumValue
    private final String code;

    TriggerMode(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
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
     * @return 枚举值，如果编码无效返回 BOTH（默认值）
     */
    @JsonCreator
    public static TriggerMode of(String code) {
        if (code == null || code.isBlank()) {
            return BOTH; // 默认值：不限制
        }

        String normalized = code.trim().toUpperCase();
        for (TriggerMode mode : values()) {
            if (mode.code.equals(normalized)) {
                return mode;
            }
        }

        // 无效输入返回默认值
        return BOTH;
    }

    /**
     * 判断是否允许自动触发
     *
     * @return true 如果允许自动触发
     */
    public boolean allowsAuto() {
        return this == AUTO || this == BOTH;
    }

    /**
     * 判断是否允许手动触发
     *
     * @return true 如果允许手动触发
     */
    public boolean allowsManual() {
        return this == MANUAL || this == BOTH;
    }
}
