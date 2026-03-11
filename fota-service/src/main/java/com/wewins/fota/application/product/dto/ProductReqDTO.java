package com.wewins.fota.application.product.dto;

import lombok.Data;

/**
 * 产品创建/更新请求 DTO
 */
@Data
public class ProductReqDTO {

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
}
