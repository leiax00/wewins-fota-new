package com.wewins.fota.application.firmware.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 固件版本创建/更新请求 DTO
 */
@Data
public class FirmwareVersionReqDTO {

    /**
     * 关联的产品 ID
     */
    private Long productId;

    /**
     * 版本号（如 1.0.0）
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
}
