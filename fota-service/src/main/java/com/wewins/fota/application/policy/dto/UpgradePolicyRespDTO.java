package com.wewins.fota.application.policy.dto;

import com.wewins.fota.domain.policy.enums.PolicyStatus;
import com.wewins.fota.domain.policy.enums.TriggerMode;
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
     * 触发模式
     */
    private TriggerMode triggerMode;

    /**
     * 时间窗口配置
     */
    private TimeWindowDTO timeWindow;

    /**
     * 允许升级的源版本 ID 列表
     */
    private List<Long> sourceVersions;

    /**
     * 目标设备模式：ALL / DEVICE_IDS / DEVICE_BATCHES / DEVICE_TAGS
     */
    private String targetMode;

    /**
     * 目标设备 IMEI 列表
     */
    private List<String> targetImeis;

    /**
     * 目标设备批次 ID 列表
     */
    private List<String> targetDeviceBatchIds;

    /**
     * 目标设备标签条件（AND 逻辑）
     */
    private Map<String, Object> targetDeviceTags;

    /**
     * 策略状态
     */
    private PolicyStatus status;

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

    // ==================== 扩展字段（用于列表展示） ====================

    /**
     * 产品名称（扩展字段，不存储）
     */
    private String productName;

    /**
     * 固件版本号（扩展字段，不存储）
     */
    private String firmwareVersion;

    /**
     * 源版本ID到版本号的映射（扩展字段，不存储）
     * <p>
     * 用于前端显示源版本列表的版本号
     * </p>
     */
    private Map<Long, String> sourceVersionNames;

    /**
     * 创建人姓名
     */
    private String createdByName;

    /**
     * 更新人姓名
     */
    private String updatedByName;
}
