package com.wewins.fota.module.system.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色响应 DTO
 */
@Data
@Builder
public class RoleRespDTO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}
