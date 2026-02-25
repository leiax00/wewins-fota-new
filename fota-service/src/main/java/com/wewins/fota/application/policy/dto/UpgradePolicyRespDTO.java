package com.wewins.fota.application.policy.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 升级策略响应 DTO
 */
@Data
@Builder
public class UpgradePolicyRespDTO {

    /**
     * 策略 ID
     */
    private Long id;

    /**
     * 关联产品 ID
     */
    private Long productId;

    /**
     * 目标固件版本 ID
     */
    private Long firmwareVersionId;

    /**
     * 策略名称
     */
    private String name;

    /**
     * 灰度比例（0-100）
     */
    private Integer grayRate;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 计划时间
     */
    private LocalDateTime planTime;

    /**
     * 状态（ACTIVE/PAUSED/EXPIRED）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 创建人 ID
     */
    private Long createdBy;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 更新人 ID
     */
    private Long updatedBy;
}
