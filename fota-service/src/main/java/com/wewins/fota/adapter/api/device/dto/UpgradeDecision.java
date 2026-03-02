package com.wewins.fota.adapter.api.device.dto;

import lombok.Getter;

/**
 * 升级检查决策类型枚举
 * <p>
 * 定义设备升级检查的所有可能结果，用于设备端判断后续行为
 * 同时用于 ClickHouse Enum8 字段类型映射
 * Code 值：UPDATE=0, NO_UPDATE=1, RATE_LIMITED=2, DEVICE_NOT_FOUND=3, ERROR=4
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-01
 */
@Getter
public enum UpgradeDecision {

    /**
     * 有可用更新 - 设备应下载并安装固件
     */
    UPDATE(0, "有可用更新"),

    /**
     * 无更新 - 设备当前已是最新版本
     */
    NO_UPDATE(1, "无更新"),

    /**
     * 请求被限流 - 设备请求过于频繁，需等待后重试
     */
    RATE_LIMITED(2, "请求被限流"),

    /**
     * 设备不存在 - 设备未在系统中注册
     */
    DEVICE_NOT_FOUND(3, "设备不存在"),

    /**
     * 错误 - 处理过程中发生错误
     */
    ERROR(4, "错误");

    private final int code;
    private final String description;

    UpgradeDecision(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 从 code 解析枚举（支持 JSON 反序列化）
     */
    public static UpgradeDecision fromCode(int code) {
        for (UpgradeDecision decision : values()) {
            if (decision.code == code) {
                return decision;
            }
        }
        return null;
    }

    /**
     * 根据决策名称获取枚举
     *
     * @param name 决策名称（如 "UPDATE", "NO_UPDATE"）
     * @return 对应的枚举，如果不存在则返回 ERROR
     */
    public static UpgradeDecision fromName(String name) {
        if (name == null) {
            return ERROR;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return ERROR;
        }
    }
}
