package com.wewins.fota.module.system.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限响应 DTO
 */
@Data
@Builder
public class PermissionRespDTO {

    private Long id;
    private String code;
    private String name;
    private String type;
    private String path;
    private String method;
    private Long parentId;
    private String status;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}
