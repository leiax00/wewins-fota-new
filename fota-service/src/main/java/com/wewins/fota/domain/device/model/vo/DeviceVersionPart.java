package com.wewins.fota.domain.device.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 设备版本部分信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceVersionPart {
    
    /**
     * 版本 ID（关联 firmware_versions.id）
     */
    @JsonProperty("versionId")
    private Long versionId;
    
    /**
     * 版本号（冗余字段，便于查询）
     */
    @JsonProperty("version")
    private String version;

    /**
     * 内部版本号（冗余字段，便于查询）
     */
    @JsonProperty("internalVersion")
    private String internalVersion;
    
    /**
     * 更新时间
     */
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;
}
