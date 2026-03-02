package com.wewins.fota.domain.reporting.model.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/**
 * 设备升级事件类型
 * <p>
 * 用于设备上报和 ClickHouse Enum8 字段类型映射
 * Code 值：DL_START=0, DL_OK=1, DL_FAIL=2, UP_OK=3, UP_FAIL=4
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Getter
public enum DeviceUpgradeEventType {

    /**
     * 开始下载固件
     */
    DL_START(0, "开始下载固件"),

    /**
     * 下载完成
     */
    DL_OK(1, "下载完成"),

    /**
     * 下载失败
     */
    DL_FAIL(2, "下载失败"),

    /**
     * 升级完成
     */
    UP_OK(3, "升级完成"),

    /**
     * 升级失败
     */
    UP_FAIL(4, "升级失败");

    private final int code;
    private final String description;

    DeviceUpgradeEventType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * JSON 序列化时输出 code 值
     */
    @JsonValue
    public int getCode() {
        return code;
    }

    /**
     * 从设备上报的 code 解析枚举（支持 JSON 反序列化）
     *
     * @param code 设备上报的事件类型代码
     * @return 对应的枚举类型，如果不存在则返回 null
     */
    @JsonCreator
    public static DeviceUpgradeEventType fromCode(int code) {
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
