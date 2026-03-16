package com.wewins.fota.domain.load.model.enums;

import lombok.Getter;

/**
 * 系统负载级别枚举
 * <p>
 * 用于表示系统当前负载状态，支持动态周期调整和智能退避算法
 * </p>
 */
@Getter
public enum LoadLevel {
    LOW(0, 24, "系统空闲"),
    NORMAL(25, 49, "正常负载"),
    HIGH(50, 74, "高负载"),
    CRITICAL(75, 100, "过载");

    private final int minScore;
    private final int maxScore;
    private final String description;

    LoadLevel(int minScore, int maxScore, String description) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.description = description;
    }

    public static LoadLevel fromScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Score must be between 0 and 100, got: " + score);
        }
        for (LoadLevel level : values()) {
            if (score >= level.minScore && score <= level.maxScore) {
                return level;
            }
        }
        return CRITICAL;
    }

    public boolean isOverloaded() {
        return this == HIGH || this == CRITICAL;
    }

    public boolean isCritical() {
        return this == CRITICAL;
    }
}
