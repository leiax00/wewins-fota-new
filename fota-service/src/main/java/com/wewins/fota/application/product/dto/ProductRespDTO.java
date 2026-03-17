package com.wewins.fota.application.product.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 产品响应 DTO
 */
@Data
@Builder
public class ProductRespDTO {

    /**
     * 产品 ID
     */
    private Long id;

    /**
     * 产品名称
     */
    private String name;

    /**
     * 制造商
     */
    private String manufacturer;

    /**
     * 产品型号
     */
    private String model;

    /**
     * 产品备注
     */
    private String remark;

    /**
     * 产品默认检测周期，单位秒。
     */
    private Integer checkPeriodSeconds;

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
