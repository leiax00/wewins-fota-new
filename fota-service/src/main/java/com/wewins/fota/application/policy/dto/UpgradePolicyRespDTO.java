package com.wewins.fota.application.policy.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
     * 优先级（数值越大优先级越高）
     */
    private Integer priority;

    /**
     * 触发模式：AUTO / MANUAL
     */
    private String triggerMode;

    /**
     * 时间窗口配置
     */
    private TimeWindowDTO timeWindow;

    /**
     * 允许升级的源版本列表
     */
    private List<String> sourceVersions;

    /**
     * 目标设备模式：ALL / DEVICE_IDS / DEVICE_BATCHES / DEVICE_TAGS
     */
    private String targetMode;

    /**
     * 目标设备 ID 列表
     */
    private List<String> targetDeviceIds;

    /**
     * 目标设备批次 ID 列表
     */
    private List<String> targetDeviceBatchIds;

    /**
     * 目标设备标签条件（AND 逻辑）
     */
    private Map<String, Object> targetDeviceTags;

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
