package com.wewins.fota.module.system.dto;

import lombok.Data;

/**
 * 用户创建/更新请求 DTO
 */
@Data
public class UserReqDTO {

    private String username;

    /**
     * 管理端兼容字段，服务端不会回传该字段
     */
    private String passwordHash;

    private String displayName;

    private String email;

    private String phone;

    private String status;

    private Long tenantId;
}
