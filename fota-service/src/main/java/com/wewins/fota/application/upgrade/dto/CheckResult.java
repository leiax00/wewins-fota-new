package com.wewins.fota.application.upgrade.dto;

import com.wewins.fota.adapter.api.device.dto.UpgradeDecision;
import lombok.Builder;
import lombok.Data;

/**
 * 升级检查结果（Application 层内部 DTO）
 * <p>
 * 包含业务决策信息和内部状态，用于 Application 层与 Adapter 层之间传递数据。
 * Adapter 层负责将其转换为符合 PRD 定义的对外响应格式。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-01
 */
@Data
@Builder
public class CheckResult {

    /**
     * 是否有更新
     */
    private Boolean hasUpdate;

    /**
     * 检查决策类型
     */
    private UpgradeDecision decision;

    /**
     * 目标版本 ID
     */
    private Long targetVersionId;

    /**
     * 目标版本号
     */
    private String targetVersion;

    /**
     * 策略 ID
     */
    private Long policyId;

    /**
     * 请求唯一标识（UUID）
     * <p>
     * 用于关联设备检查请求和后续的升级事件
     * </p>
     */
    private String requestId;

    /**
     * 建议的下次检查间隔（秒）
     */
    private Integer checkInterval;

    /**
     * 下载延迟（秒）
     */
    private Integer downloadDelay;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误信息
     */
    private String errorMessage;

    // ==================== 固件元数据 ====================

    /**
     * 发布开始日期（ISO 8601 格式）
     */
    private String releaseStartDate;

    /**
     * 发布说明
     */
    private String releaseNote;

    /**
     * 新固件版本号
     */
    private String newFirmware;

    /**
     * 签名下载 URL
     */
    private String downloadUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件大小文本
     */
    private String fileSizeText;

    /**
     * 校验和
     */
    private String checksum;

    /**
     * 校验和类型
     */
    private String checksumType;

    // ==================== 静态工厂方法 ====================

    /** 默认检查间隔：24 小时（秒） */
    private static final int DEFAULT_CHECK_INTERVAL = 86400;

    /** 错误重试间隔：1 小时（秒） */
    private static final int ERROR_RETRY_INTERVAL = 3600;

    /**
     * 创建"有更新"结果
     */
    public static CheckResult update() {
        return CheckResult.builder()
                .hasUpdate(true)
                .decision(UpgradeDecision.UPDATE)
                .build();
    }

    /**
     * 创建"无更新"结果
     */
    public static CheckResult noUpdate() {
        return CheckResult.builder()
                .hasUpdate(false)
                .decision(UpgradeDecision.NO_UPDATE)
                .checkInterval(DEFAULT_CHECK_INTERVAL)
                .build();
    }

    /**
     * 创建"设备不存在"结果
     */
    public static CheckResult notFound(String message) {
        return CheckResult.builder()
                .hasUpdate(false)
                .decision(UpgradeDecision.DEVICE_NOT_FOUND)
                .errorMessage(message)
                .checkInterval(ERROR_RETRY_INTERVAL)
                .build();
    }

    /**
     * 创建"限流"结果
     */
    public static CheckResult rateLimited(String message, int retryAfterSeconds) {
        return CheckResult.builder()
                .hasUpdate(false)
                .decision(UpgradeDecision.RATE_LIMITED)
                .errorMessage(message)
                .checkInterval(retryAfterSeconds)
                .downloadDelay(retryAfterSeconds)
                .build();
    }

    /**
     * 创建"错误"结果
     */
    public static CheckResult error(String message) {
        return CheckResult.builder()
                .hasUpdate(false)
                .decision(UpgradeDecision.ERROR)
                .errorMessage(message)
                .checkInterval(ERROR_RETRY_INTERVAL)
                .build();
    }

    /**
     * 创建"错误"结果（带错误码）
     */
    public static CheckResult error(String errorCode, String message) {
        return CheckResult.builder()
                .hasUpdate(false)
                .decision(UpgradeDecision.ERROR)
                .errorCode(errorCode)
                .errorMessage(message)
                .checkInterval(ERROR_RETRY_INTERVAL)
                .build();
    }
}
