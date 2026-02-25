package com.wewins.fota.application.firmware.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 固件版本响应 DTO
 */
@Data
@Builder
public class FirmwareVersionRespDTO {

    /**
     * 固件版本 ID
     */
    private Long id;

    /**
     * 关联的产品 ID
     */
    private Long productId;

    /**
     * 产品名称（关联查询）
     */
    private String productName;

    /**
     * 版本号
     */
    private String version;

    /**
     * 固件文件下载地址
     */
    private String fileUrl;

    /**
     * 固件文件大小（字节）
     */
    private Long fileSize;

    /**
     * MD5 校验和
     */
    private String md5;

    /**
     * SHA-256 校验和
     */
    private String sha256;

    /**
     * 版本标签（JSON 字符串）
     */
    private String tags;

    /**
     * 扩展元数据（JSON 字符串）
     */
    private String meta;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 创建人 ID
     */
    private Long createdBy;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 更新人 ID
     */
    private Long updatedBy;
}
