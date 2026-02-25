package com.wewins.fota.application.policy.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 升级策略创建/更新请求 DTO
 */
@Data
public class UpgradePolicyReqDTO {

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
}
