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
@TableName("devices")
public class DevicePO extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private String imei;

    private Long productId;

    private String status;

    private LocalDateTime lastSeenAt;

    private LocalDateTime firstSeenAt;

    private String tags;

    private String versionParts;

    private String initialVersionParts;

    private Long importBatchId;

    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
