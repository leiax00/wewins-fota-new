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
     * 内部版本号（build tag）
     * <p>
     * 用于与 version 组合唯一确定固件版本
     * </p>
     */
    private String internalVersion;

    /**
     * 固件包状态（可选）
     * <p>
     * 可选值：
     * <ul>
     *   <li>NONE - 无固件包（占位版本号）</li>
     *   <li>UPLOADED - 已上传临时文件</li>
     *   <li>READY - 已转存到对象存储，可下载</li>
     *   <li>FAILED - 上传或转存失败</li>
     * </ul>
     * </p>
     * <p>
     * 若不提供，则根据是否有 uploadSessionId 或 fileUrl 自动判断
     * </p>
     */
    private String packageStatus;

    /**
     * 上传会话 ID（可选）
     * <p>
     * 若提供则优先使用上传会话中的包信息，忽略 fileUrl/fileSize/md5/sha256 字段。
     * </p>
     */
    private String uploadSessionId;

    /**
     * 固件文件下载地址（可选）
     * <p>
     * 兼容手动填写场景（未提供 uploadSessionId 时使用）。
     * </p>
     */
    private String fileUrl;

    /**
     * 固件原始文件名（可选）
     * <p>
     * 保存用户上传时的原始文件名，便于追溯。
     * </p>
     */
    private String fileName;

    /**
     * 固件文件大小（字节，可选）
     */
    private Long fileSize;

    /**
     * MD5 校验和（可选）
     */
    private String md5;

    /**
     * SHA-256 校验和（可选）
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
