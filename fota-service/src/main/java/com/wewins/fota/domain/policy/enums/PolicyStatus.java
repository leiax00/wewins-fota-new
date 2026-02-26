package com.wewins.fota.domain.policy.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 策略状态枚举
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
public enum PolicyStatus {

    /**
     * 激活状态
     */
    ACTIVE("激活", "ACTIVE"),

    /**
     * 暂停状态
     */
    PAUSED("暂停", "PAUSED"),

    /**
     * 过期状态
     */
    EXPIRED("过期", "EXPIRED");

    private final String displayName;
    private final String code;

    PolicyStatus(String displayName, String code) {
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
     * @return 枚举值，如果编码无效返回 ACTIVE（默认值）
     */
    @JsonCreator
    public static PolicyStatus of(String code) {
        if (code == null || code.isBlank()) {
            return ACTIVE; // 默认值
        }

        String normalized = code.trim().toUpperCase();
        for (PolicyStatus status : values()) {
            if (status.code.equals(normalized)) {
                return status;
            }
        }

        // 无效输入返回默认值，避免系统崩溃
        return ACTIVE;
    }

    /**
     * 判断是否为激活状态
     *
     * @return true 如果状态为 ACTIVE
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * 判断是否为终止状态（无法恢复）
     *
     * @return true 如果状态为 EXPIRED
     */
    public boolean isTerminal() {
        return this == EXPIRED;
    }
}
