package com.wewins.fota.application.device.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 设备导入预估响应 DTO
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Data
@Builder
public class DeviceImportEstimateRespDTO {

    /**
     * 会话ID，用于后续执行导入
     */
    private String sessionId;

    /**
     * 解析的总数
     */
    private Integer totalCount;

    /**
     * 有效 IMEI 数量
     */
    private Integer validCount;

    /**
     * 无效 IMEI 数量
     */
    private Integer invalidCount;
}
