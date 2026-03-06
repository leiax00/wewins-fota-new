package com.wewins.fota.application.device.dto;

import com.wewins.fota.domain.device.value.DeviceVersionParts;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备响应 DTO
 */
@Data
@Builder
public class DeviceRespDTO {

    /**
     * 设备 ID
     */
    private Long id;

    /**
     * 设备 IMEI
     */
    private String imei;

    /**
     * 产品 ID
     */
    private Long productId;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 当前固件版本 ID
     */
    private DeviceVersionParts versionParts;

    /**
     * 当前固件版本 ID
     */
    private DeviceVersionParts initialVersionParts;

    /**
     * 当前固件版本号
     */
    private String versionName;

    /**
     * 设备状态
     */
    private String status;

    /**
     * 最后在线时间
     */
    private LocalDateTime lastSeenAt;

    /**
     * 第一次在线时间
     */
    private LocalDateTime firstSeenAt;

    /**
     * 标签 JSON 字符串
     */
    private String tags;

    /**
     * 导入批次 ID
     */
    private Long importBatchId;

    /**
     * 导入批次名称
     */
    private String importBatchName;

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
