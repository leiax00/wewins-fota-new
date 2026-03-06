package com.wewins.fota.domain.policy.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
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
     * 草稿状态
     */
    DRAFT("草稿", "DRAFT"),

    /**
     * 测试中状态
     */
    TESTING("测试中", "TESTING"),

    /**
     * 已验证状态
     */
    VERIFIED("已验证", "VERIFIED"),

    /**
     * 生产中状态
     */
    ACTIVE("生产中", "ACTIVE"),

    /**
     * 暂停状态
     */
    PAUSED("暂停", "PAUSED"),

    /**
     * 过期状态
     */
    EXPIRED("过期", "EXPIRED");

    private final String displayName;

    @EnumValue
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
     * @return 枚举值
     * @throws IllegalArgumentException 如果编码无效
     */
    @JsonCreator
    public static PolicyStatus of(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("策略状态不能为空");
        }

        String normalized = code.trim().toUpperCase();
        for (PolicyStatus status : values()) {
            if (status.code.equals(normalized)) {
                return status;
            }
        }

        throw new IllegalArgumentException("无效的策略状态: " + code);
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
     * 判断是否为草稿状态
     *
     * @return true 如果状态为 DRAFT
     */
    public boolean isDraft() {
        return this == DRAFT;
    }

    /**
     * 判断是否为测试中状态
     *
     * @return true 如果状态为 TESTING
     */
    public boolean isTesting() {
        return this == TESTING;
    }

    /**
     * 判断是否为已验证状态
     *
     * @return true 如果状态为 VERIFIED
     */
    public boolean isVerified() {
        return this == VERIFIED;
    }

    /**
     * 判断是否为暂停状态
     *
     * @return true 如果状态为 PAUSED
     */
    public boolean isPaused() {
        return this == PAUSED;
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
