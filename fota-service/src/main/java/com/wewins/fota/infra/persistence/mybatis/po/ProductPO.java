package com.wewins.fota.infra.persistence.mybatis.po;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wewins.fota.database.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("products")
public class ProductPO extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    private String manufacturer;

    private String model;

    private String remark;

    private Integer checkPeriodSeconds;

    /**
     * 逻辑删除标记 (0=未删除, 1=已删除)
     */
    @TableLogic
    private Integer deleted;
}
