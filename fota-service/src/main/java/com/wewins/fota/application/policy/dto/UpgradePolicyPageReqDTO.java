package com.wewins.fota.application.policy.dto;

import lombok.Data;

/**
 * 升级策略分页查询请求 DTO
 */
@Data
public class UpgradePolicyPageReqDTO {

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
     * 策略名称（模糊查询）
     */
    private String name;

    /**
     * 状态（ACTIVE/PAUSED/EXPIRED）
     */
    private String status;

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
        if (name != null && name.isBlank()) {
            name = null;
        }
    }

    /**
     * 获取偏移量
     */
    public int getOffset() {
        return (page - 1) * size;
    }
}
