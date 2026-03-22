package com.wewins.fota.infra.persistence.mybatis.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("device_tags")
public class DeviceTagPO implements Serializable {

    private Long id;

    private Long deviceId;

    private String tagKey;

    private String tagValue;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
