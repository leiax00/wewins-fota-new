package com.wewins.fota.infra.persistence.mybatis.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("firmware_version_tags")
public class FirmwareVersionTagPO implements Serializable {

    private Long id;

    private Long firmwareVersionId;

    private String tagKey;

    private String tagValue;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
