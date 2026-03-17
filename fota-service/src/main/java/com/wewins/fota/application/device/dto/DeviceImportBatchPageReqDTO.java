package com.wewins.fota.application.device.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 设备导入批次分页查询请求 DTO
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Data
public class DeviceImportBatchPageReqDTO {

    /**
     * 批次名称（模糊查询）
     */
    private String batchName;

    /**
     * 关联的产品ID
     */
    @Positive(message = "产品ID必须为正数")
    private Long productId;

    /**
     * 批次状态
     */
    private String status;

    /**
     * 页码（从1开始）
     */
    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;

    /**
     * 每页大小
     */
    @Min(value = 1, message = "每页大小必须大于0")
    @Max(value = 100, message = "每页大小不能超过100")
    private Integer size = 20;

    /**
     * 校验并规范化分页参数
     */
    public void validate() {
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1 || size > 100) {
            size = 20;
        }
    }
}
