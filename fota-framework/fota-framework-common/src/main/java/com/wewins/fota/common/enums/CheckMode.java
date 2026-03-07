package com.wewins.fota.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/**
 * 设备检查模式
 * <p>
 * 用于设备上报和 ClickHouse Enum8 字段类型映射
 * Code 值：MANUAL=0, AUTO=1（与设备端约定一致）
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-02
 */
@Getter
public enum CheckMode {

    /**
     * 手动检查
     */
    MANUAL(0, "手动检查"),

    /**
     * 自动检查
     */
    AUTO(1, "自动检查");

    private final int code;
    private final String description;

    CheckMode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    /**
     * 从设备上报的值解析枚举（支持数字和字符串两种格式）
     *
     * @param value 设备上报的值（数字或字符串）
     * @return 对应的枚举类型，如果不存在则返回 null
     */
    @JsonCreator
    public static CheckMode fromValue(Object value) {
        if (value == null) {
            return null;
        }

        // 支持数字类型
        if (value instanceof Number) {
            return fromCode(((Number) value).intValue());
        }

        // 支持字符串类型（数字或枚举名称）
        String strValue = value.toString();
        try {
            int code = Integer.parseInt(strValue);
            return fromCode(code);
        } catch (NumberFormatException e) {
            // 尝试按枚举名称解析
            return Arrays.stream(values())
                    .filter(v -> v.name().equalsIgnoreCase(strValue))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static CheckMode fromCode(int code) {
        return Arrays.stream(values())
                .filter(v -> v.code == code)
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return name();
    }
}
