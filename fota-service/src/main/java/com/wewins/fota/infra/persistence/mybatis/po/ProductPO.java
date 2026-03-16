package com.wewins.fota.infra.persistence.mybatis.po;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wewins.fota.database.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

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

    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
