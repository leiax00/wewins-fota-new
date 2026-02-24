package com.wewins.fota.module.system.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.time.LocalDateTime;

/**
 * 用户响应 DTO
 */
@Data
@Builder
public class UserRespDTO {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private String status;
    private LocalDateTime lastLoginAt;
    /**
     * 角色 code 列表
     */
    private List<String> roles;
    /**
     * 权限 code 列表
     */
    private List<String> permissions;
    private Long tenantId;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}
