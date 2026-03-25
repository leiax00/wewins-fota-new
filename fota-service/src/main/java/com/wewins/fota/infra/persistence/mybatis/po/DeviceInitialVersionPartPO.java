package com.wewins.fota.infra.persistence.mybatis.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("device_initial_version_parts")
public class DeviceInitialVersionPartPO implements Serializable {

    private Long id;

    private Long deviceId;

    private String partName;

    private Long versionId;

    private String version;

    private String internalVersion;

    private Integer isPrimary;

    private LocalDateTime recordedAt;
}
