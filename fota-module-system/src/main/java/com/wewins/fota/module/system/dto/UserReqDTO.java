package com.wewins.fota.module.system.dto;

import lombok.Data;

/**
 * 用户创建/更新请求 DTO
 */
@Data
public class UserReqDTO {

    private String username;

    /**
     * 明文密码（创建用户时必填）
     */
    private String password;

    private String displayName;

    private String email;

    private String phone;

    private String status;

    private Long tenantId;
}
