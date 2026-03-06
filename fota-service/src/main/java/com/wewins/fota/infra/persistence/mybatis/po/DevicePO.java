package com.wewins.fota.infra.persistence.mybatis.po;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wewins.fota.database.handler.JsonbStringTypeHandler;
import com.wewins.fota.database.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

import java.io.Serializable;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "devices", autoResultMap = true)
public class DevicePO extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private String imei;

    private Long productId;

    private String status;

    private LocalDateTime lastSeenAt;

    private LocalDateTime firstSeenAt;

    @TableField(typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String tags;

    @TableField(typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String versionParts;

    @TableField(typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String initialVersionParts;

    private Long importBatchId;

    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
