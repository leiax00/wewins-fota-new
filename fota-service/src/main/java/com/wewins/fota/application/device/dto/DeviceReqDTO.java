package com.wewins.fota.application.device.dto;

import com.wewins.fota.domain.device.model.vo.DeviceVersionParts;
import lombok.Data;

/**
 * 设备创建/更新请求 DTO
 */
@Data
public class DeviceReqDTO {

    /**
     * 设备 IMEI（15 位）
     */
    private String imei;

    /**
     * 产品 ID
     */
    private Long productId;

    /**
     * 当前固件版本 ID（可选）
     */
    private DeviceVersionParts versionParts;

    /**
     * 设备状态（ONLINE/OFFLINE/LOST）
     */
    private String status;

    /**
     * 设备标签（JSON 字符串，可选）
     */
    private String tags;
}
