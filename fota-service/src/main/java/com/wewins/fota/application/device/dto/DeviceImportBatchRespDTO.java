package com.wewins.fota.application.device.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备导入批次响应 DTO
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Data
@Builder
public class DeviceImportBatchRespDTO {

    /**
     * 批次ID
     */
    private Long id;

    /**
     * 批次名称
     */
    private String batchName;

    /**
     * 批次状态
     */
    private String status;

    /**
     * 导入总数量
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
     * 失败原因
     */
    private String errorMessage;

    /**
     * 开始导入时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束导入时间
     */
    private LocalDateTime finishedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 更新人ID
     */
    private Long updatedBy;
}
