package com.wewins.fota.module.system.dto;

import lombok.Data;

/**
 * 角色创建/更新请求 DTO
 */
@Data
public class RoleReqDTO {

    private String code;
    private String name;
    private String description;
    private String status;
}
