package com.wewins.fota.module.system.dto;

import lombok.Data;

/**
 * 权限创建/更新请求 DTO
 */
@Data
public class PermissionReqDTO {

    // 基础字段
    private String code;
    private String name;
    private String type;
    private String path;
    private String method;
    private Long parentId;
    private String status;

    // 菜单路由字段
    private String routePath;
    private String routeName;
    private String componentKey;
    private String redirectPath;

    // 菜单显示字段
    private String i18nKey;
    private String icon;
    private Integer menuSort;
    private Boolean menuVisible;
    private Boolean breadcrumbVisible;
    private Boolean tabVisible;
    private Boolean tabClosable;
    private Boolean affixTab;
    private Boolean keepAlive;
    private Boolean alwaysShow;

    // 菜单高级字段
    private String activeMenu;
    private String externalLink;
    private String openMode;
}
