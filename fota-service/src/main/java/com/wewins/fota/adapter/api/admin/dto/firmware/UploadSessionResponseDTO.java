package com.wewins.fota.adapter.api.admin.dto.firmware;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上传成功响应 DTO。
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadSessionResponseDTO {
    /**
     * 会话 ID（用于后续提交固件版本）
     */
    private String sessionId;

    /**
     * 会话状态（UPLOADED）
     */
    private String status;

    /**
     * 关联的产品 ID
     */
    private Long productId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MD5 哈希值（32 位十六进制）
     */
    private String md5;

    /**
     * SHA-256 哈希值（64 位十六进制）
     */
    private String sha256;

    /**
     * MIME 类型
     */
    private String mime;
}
