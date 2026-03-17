package com.wewins.fota.adapter.api.admin.dto.firmware;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 上传会话详情 DTO。
 *
 * @author FOTA Team
 * @since 2026-02-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadSessionDetailDTO {
    private String sessionId;
    private String status;
    private Long productId;
    private String version;
    private String fileName;
    private Long fileSize;
    private String md5;
    private String sha256;
    private String mime;
    private String objectKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String lastError;
}
