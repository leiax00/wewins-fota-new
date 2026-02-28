package com.wewins.fota.module.system.dto.menu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户菜单树节点 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMenuNodeDTO {

    /**
     * 路由路径（route_path）
     */
    private String path;

    /**
     * 路由名称（route_name）
     */
    private String name;

    /**
     * 组件标识
     */
    private String componentKey;

    /**
     * 重定向路径
     */
    private String redirect;

    /**
     * 路由元数据
     */
    private RouteMetaDTO meta;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 子菜单
     */
    private List<UserMenuNodeDTO> children;
}
