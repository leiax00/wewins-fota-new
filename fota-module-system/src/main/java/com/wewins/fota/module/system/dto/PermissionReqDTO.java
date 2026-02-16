package com.wewins.fota.module.system.dto;

import lombok.Data;

/**
 * 权限创建/更新请求 DTO
 */
@Data
public class PermissionReqDTO {

    private String code;
    private String name;
    private String type;
    private String path;
    private String method;
    private Long parentId;
    private String status;
}
