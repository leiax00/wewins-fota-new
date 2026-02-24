package com.wewins.fota.module.system.dto.menu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 路由元数据 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteMetaDTO {

    /**
     * 国际化 key
     */
    private String i18nKey;

    /**
     * 图标名
     */
    private String icon;

    /**
     * 是否隐藏（侧边栏）
     */
    private Boolean hidden;

    /**
     * 是否缓存
     */
    private Boolean keepAlive;

    /**
     * 是否固定标签页
     */
    private Boolean affix;

    /**
     * 单子节点时是否显示父级
     */
    private Boolean alwaysShow;

    /**
     * 是否在面包屑中隐藏
     */
    private Boolean breadcrumbHidden;

    /**
     * 是否在标签页中隐藏
     */
    private Boolean tabHidden;

    /**
     * 标签页是否可关闭
     */
    private Boolean tabClosable;

    /**
     * 高亮菜单（详情页用）
     */
    private String activeMenu;

    /**
     * 权限码
     */
    private String permission;

    /**
     * 外链配置
     */
    private ExternalLinkDTO externalLink;
}
