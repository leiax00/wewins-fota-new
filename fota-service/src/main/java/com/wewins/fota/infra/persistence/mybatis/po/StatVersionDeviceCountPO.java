package com.wewins.fota.infra.persistence.mybatis.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wewins.fota.database.entity.AutoIdEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "stat_version_device_count", autoResultMap = true)
public class StatVersionDeviceCountPO extends AutoIdEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long productId;

    private Long versionId;

    private Long deviceCount;

    private LocalDateTime statTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
