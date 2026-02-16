package com.wewins.fota.domain.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.wewins.fota.database.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 产品实体类
 * <p>
 * 对应数据库表：products
 * 存储产品的基本信息，每个产品代表一类设备
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("products")
public class Product extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

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
     * 软删除时间（逻辑删除）
     */
    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
