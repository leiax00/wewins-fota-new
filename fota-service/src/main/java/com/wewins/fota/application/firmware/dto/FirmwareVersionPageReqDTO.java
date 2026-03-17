package com.wewins.fota.application.firmware.dto;

import lombok.Data;

/**
 * 固件版本分页查询请求 DTO
 */
@Data
public class FirmwareVersionPageReqDTO {

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
     * 版本号（模糊查询）
     */
    private String version;

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
    }

    /**
     * 获取偏移量
     */
    public int getOffset() {
        return (page - 1) * size;
    }
}
