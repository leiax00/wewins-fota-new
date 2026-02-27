package com.wewins.fota.application.device.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备导入响应DTO
 *
 * @author FOTA Team
 * @since 2026-02-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceImportRespDTO {

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 批次名称
     */
    private String batchName;

    /**
     * 批次状态
     */
    private String status;

    /**
     * 总数量
     */
    private Integer totalCount;

    /**
     * 成功数量
     */
    private Integer successCount;

    /**
     * 失败数量
     */
    private Integer failedCount;

    /**
     * 错误信息
     */
    private String errorMessage;
}
