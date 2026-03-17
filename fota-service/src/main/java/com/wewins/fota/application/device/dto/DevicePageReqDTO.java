package com.wewins.fota.application.device.dto;

import lombok.Data;

/**
 * 设备分页查询请求 DTO
 */
@Data
public class DevicePageReqDTO {

    /**
     * 页码（从 1 开始）
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer size = 10;

    /**
     * 产品 ID
     */
    private Long productId;

    /**
     * 设备 IMEI（支持模糊查询）
     */
    private String imei;

    /**
     * 设备状态（ONLINE/OFFLINE/LOST）
     */
    private String status;

    /**
     * 导入批次 ID
     */
    private Long importBatchId;

    /**
     * 校验并设置默认值
     */
    public void validate() {
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        if (size > 100) {
            size = 100;
        }
        if (status != null && !status.isBlank()) {
            status = status.trim().toUpperCase();
        } else {
            status = null;
        }
        if (imei != null) {
            imei = imei.trim();
            if (imei.isBlank()) {
                imei = null;
            }
        }
    }

    /**
     * 获取偏移量
     */
    public int getOffset() {
        return (page - 1) * size;
    }
}
